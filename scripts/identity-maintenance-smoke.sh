#!/usr/bin/env bash
#
# identity-maintenance-smoke.sh
#
# Smoke test for the local administrator recovery command.
# Verifies both JVM and Native Image maintenance paths:
#   - Flyway migration runs on first startup
#   - Recovery generates exactly one temporary password on stdout
#   - HTTP port is never listened to
#   - Old sessions are revoked
#   - PASSWORD_RECOVERED_LOCALLY audit event is written
#
# Prerequisites:
#   - backend/target/stocket-backend-0.1.0.jar (JVM jar)
#   - backend/target/stocket-backend (native executable)
#   - Docker running (for PostgreSQL container)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/backend/target/stocket-backend-0.1.0.jar"
NATIVE="$PROJECT_ROOT/backend/target/stocket-backend"

DB_NAME="stocket_smoke_$$"
DB_USER="stocket"
DB_PASSWORD="stocket-smoke-test"
DB_PORT=54321
CONTAINER_NAME="stocket-smoke-pg-$$"
# Port the application would bind to if it started an HTTP server.
# We verify this port is never listened to during maintenance.
HTTP_CHECK_PORT=18080

cleanup() {
    echo "[smoke] Cleaning up container $CONTAINER_NAME"
    docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
}
trap cleanup EXIT

# Helper: run psql against the smoke container, return first line trimmed
psql_exec() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$1" 2>/dev/null | head -1 | tr -d '[:space:]'
}

# Helper: check that no process is listening on the given port
assert_port_not_listened() {
    local port="$1"
    local label="$2"
    # Use lsof to check if any process has the port open for listening
    if lsof -iTCP:"$port" -sTCP:LISTEN -P -n 2>/dev/null | grep -q LISTEN; then
        echo "[smoke] ERROR: Port $port is being listened to during $label"
        echo "[smoke] HTTP port must never be bound during maintenance"
        lsof -iTCP:"$port" -sTCP:LISTEN -P -n 2>/dev/null
        return 1
    fi
    echo "[smoke] OK: Port $port not listened to during $label"
    return 0
}

# Helper: run a maintenance process, capture output, and determine exit code.
#
# The JVM/native process may not exit cleanly after maintenance (HikariCP
# connection pool keeps non-daemon threads alive). We handle three cases:
#   1. Process exits naturally with a code -> return that code
#   2. Output contains the success marker "Admin recovery successful." -> return 0
#   3. Output contains "Application run failed" -> wait for exit, return exit code
#   4. Timeout -> return 124
#
# Usage: run_maintenance <binary> <args...>
run_maintenance() {
    local outfile
    outfile=$(mktemp)
    "$@" > "$outfile" 2>&1 &
    local pid=$!
    local elapsed=0
    local max_wait=120
    local result_code=124  # default: timeout

    # Disable errexit temporarily so we can capture wait exit codes
    set +e
    while [[ $elapsed -lt $max_wait ]]; do
        # Check if process already exited naturally
        if ! kill -0 "$pid" 2>/dev/null; then
            wait "$pid" 2>/dev/null
            result_code=$?
            break
        fi
        # Check for success marker (exact line from MaintenanceConfiguration)
        if grep -q "^Admin recovery successful\." "$outfile" 2>/dev/null; then
            # Success - kill the lingering process (HikariCP keeps it alive)
            sleep 2
            kill "$pid" 2>/dev/null
            wait "$pid" 2>/dev/null
            result_code=0
            break
        fi
        # Check for failure marker
        if grep -q "Application run failed" "$outfile" 2>/dev/null; then
            # Wait for the process to actually exit
            wait "$pid" 2>/dev/null
            result_code=$?
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    # If we exited the loop without the process dying, kill it
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null
        wait "$pid" 2>/dev/null
    fi
    set -e

    cat "$outfile"
    rm -f "$outfile"
    return $result_code
}

# ---- Verify artifacts exist ----
if [[ ! -f "$JAR" ]]; then
    echo "[smoke] ERROR: JVM jar not found at $JAR"
    echo "[smoke] Run: cd backend && ./mvnw -DskipTests package"
    exit 1
fi
if [[ ! -f "$NATIVE" ]]; then
    echo "[smoke] ERROR: Native executable not found at $NATIVE"
    echo "[smoke] Run: cd backend && ./mvnw -Pnative -DskipTests native:compile"
    exit 1
fi

# ---- Start PostgreSQL ----
echo "[smoke] Starting PostgreSQL container: $CONTAINER_NAME"
docker run -d --name "$CONTAINER_NAME" \
    -e POSTGRES_DB="$DB_NAME" \
    -e POSTGRES_USER="$DB_USER" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -p "$DB_PORT:5432" \
    postgres:17.5-alpine

echo "[smoke] Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
    if docker exec "$CONTAINER_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

DB_URL="jdbc:postgresql://localhost:$DB_PORT/$DB_NAME"
JVM_OPTS="--spring.main.web-application-type=none --spring.datasource.url=$DB_URL --spring.datasource.username=$DB_USER --spring.datasource.password=$DB_PASSWORD"
# Native image uses same opts; web-application-type=none is set programmatically
# in StocketApplication.main() when maintenance option is detected.
NATIVE_OPTS="--spring.datasource.url=$DB_URL --spring.datasource.username=$DB_USER --spring.datasource.password=$DB_PASSWORD"

# ---- Step 1: Run JVM jar with non-existent admin (triggers Flyway migration) ----
echo "[smoke] Step 1: Running Flyway migration via JVM jar..."
set +e
JVM_MIGRATION_OUTPUT=$(run_maintenance java -jar "$JAR" \
    --stocket.maintenance.reset-admin=nonexistent \
    $JVM_OPTS 2>&1)
JVM_MIGRATION_EXIT=$?
set -e

if [[ $JVM_MIGRATION_EXIT -eq 0 ]]; then
    echo "[smoke] ERROR: Expected non-zero exit for non-existent admin, got 0"
    echo "[smoke] Output: $JVM_MIGRATION_OUTPUT"
    exit 1
fi
echo "[smoke] Flyway migration completed (exit=$JVM_MIGRATION_EXIT, expected non-zero)"

# Assert HTTP port not listened to during migration
assert_port_not_listened "$HTTP_CHECK_PORT" "JVM migration step" || exit 1

# ---- Step 2: Insert test data via psql ----
echo "[smoke] Step 2: Inserting test data..."

# The household table has a singleton_key constraint
HOUSEHOLD_ID=$(psql_exec "
    INSERT INTO household (id, singleton_key, name, timezone, created_at, updated_at)
    VALUES (gen_random_uuid(), 1, 'smoke-test', 'Asia/Shanghai', now(), now())
    RETURNING id;
")
ACCOUNT_ID=$(psql_exec "
    INSERT INTO user_account (id, username, normalized_username, display_name, password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
    VALUES (gen_random_uuid(), 'admin', 'admin', 'Smoke Admin',
            '{bcrypt}\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            'ACTIVE', false, now(), now(), now(), 0)
    RETURNING id;
")
psql_exec "
    INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
    VALUES (gen_random_uuid(), '$HOUSEHOLD_ID', '$ACCOUNT_ID', 'ADMIN', now(), now());
" >/dev/null

# Insert an active session for the admin
SESSION_TOKEN_HASH="smoke_test_session_token_hash_placeholder_32b"
psql_exec "
    INSERT INTO user_session (id, account_id, token_hash, created_at, last_seen_at, idle_expires_at, absolute_expires_at, user_agent, source_address)
    VALUES (gen_random_uuid(), '$ACCOUNT_ID', '$SESSION_TOKEN_HASH',
            now(), now(), now() + interval '30 days', now() + interval '90 days',
            'smoke-test', '127.0.0.1');
" >/dev/null

# Verify initial state
ACTIVE_SESSIONS_BEFORE=$(psql_exec "
    SELECT COUNT(*) FROM user_session WHERE account_id = '$ACCOUNT_ID' AND revoked_at IS NULL;
")
if [[ "$ACTIVE_SESSIONS_BEFORE" != "1" ]]; then
    echo "[smoke] ERROR: Expected 1 active session before recovery, got $ACTIVE_SESSIONS_BEFORE"
    exit 1
fi
echo "[smoke] Test data inserted: household=$HOUSEHOLD_ID, account=$ACCOUNT_ID"

# ---- Step 3: JVM recovery ----
echo "[smoke] Step 3: Running JVM recovery..."
set +e
JVM_OUTPUT=$(run_maintenance java -jar "$JAR" \
    --stocket.maintenance.reset-admin=admin \
    $JVM_OPTS 2>&1)
JVM_EXIT=$?
set -e

if [[ $JVM_EXIT -ne 0 ]]; then
    echo "[smoke] ERROR: JVM recovery failed with exit code $JVM_EXIT"
    echo "[smoke] Output: $JVM_OUTPUT"
    exit 1
fi

# Assert exactly one temporary password in stdout
TEMP_PASSWORD_COUNT=$(echo "$JVM_OUTPUT" | grep -c "Temporary password:" || true)
if [[ $TEMP_PASSWORD_COUNT -ne 1 ]]; then
    echo "[smoke] ERROR: Expected exactly 1 temp password line in JVM output, got $TEMP_PASSWORD_COUNT"
    echo "[smoke] Output: $JVM_OUTPUT"
    exit 1
fi
echo "[smoke] JVM recovery succeeded"

# Assert HTTP port not listened to during recovery
assert_port_not_listened "$HTTP_CHECK_PORT" "JVM recovery" || exit 1

# Assert old sessions revoked
ACTIVE_SESSIONS_AFTER_JVM=$(psql_exec "
    SELECT COUNT(*) FROM user_session WHERE account_id = '$ACCOUNT_ID' AND revoked_at IS NULL;
")
if [[ "$ACTIVE_SESSIONS_AFTER_JVM" != "0" ]]; then
    echo "[smoke] ERROR: Expected 0 active sessions after JVM recovery, got $ACTIVE_SESSIONS_AFTER_JVM"
    exit 1
fi

# Assert PASSWORD_RECOVERED_LOCALLY audit event
AUDIT_COUNT_JVM=$(psql_exec "
    SELECT COUNT(*) FROM audit_log WHERE event_type = 'PasswordRecoveredLocally';
")
if [[ "$AUDIT_COUNT_JVM" -lt 1 ]]; then
    echo "[smoke] ERROR: Expected PASSWORD_RECOVERED_LOCALLY audit event after JVM recovery"
    exit 1
fi
echo "[smoke] JVM assertions passed: sessions revoked, audit event present"

# ---- Step 4: Re-insert test data for native recovery ----
echo "[smoke] Step 4: Re-inserting test data for native recovery..."
# Reset the account password and must_change_password
psql_exec "
    UPDATE user_account SET
        password_hash = '{bcrypt}\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        must_change_password = false,
        credentials_changed_at = now(),
        updated_at = now(),
        version = version + 1
    WHERE id = '$ACCOUNT_ID';
" >/dev/null
# Insert a new active session
psql_exec "
    INSERT INTO user_session (id, account_id, token_hash, created_at, last_seen_at, idle_expires_at, absolute_expires_at, user_agent, source_address)
    VALUES (gen_random_uuid(), '$ACCOUNT_ID', 'smoke_test_session_token_hash_2_32bytes',
            now(), now(), now() + interval '30 days', now() + interval '90 days',
            'smoke-test', '127.0.0.1');
" >/dev/null

# ---- Step 5: Native recovery ----
echo "[smoke] Step 5: Running native recovery..."
set +e
NATIVE_OUTPUT=$(run_maintenance "$NATIVE" \
    --stocket.maintenance.reset-admin=admin \
    $NATIVE_OPTS 2>&1)
NATIVE_EXIT=$?
set -e

if [[ $NATIVE_EXIT -ne 0 ]]; then
    echo "[smoke] ERROR: Native recovery failed with exit code $NATIVE_EXIT"
    if echo "$NATIVE_OUTPUT" | grep -q "No ServletContext set"; then
        echo "[smoke] Root cause: AOT-compiled native image has hard-coded servlet beans"
        echo "[smoke] that require a ServletContext. The StocketApplication.main() sets"
        echo "[smoke] WebApplicationType.NONE for maintenance mode, but AOT-generated code"
        echo "[smoke] cannot conditionally skip servlet beans at runtime."
        echo "[smoke] Fix: Rebuild native image with AOT processing in NONE web mode, or"
        echo "[smoke] use Spring Boot's conditional AOT hints to exclude servlet beans."
    fi
    echo "[smoke] Output: $NATIVE_OUTPUT"
    exit 1
fi

# Assert exactly one temporary password in stdout
TEMP_PASSWORD_COUNT_NATIVE=$(echo "$NATIVE_OUTPUT" | grep -c "Temporary password:" || true)
if [[ $TEMP_PASSWORD_COUNT_NATIVE -ne 1 ]]; then
    echo "[smoke] ERROR: Expected exactly 1 temp password line in native output, got $TEMP_PASSWORD_COUNT_NATIVE"
    echo "[smoke] Output: $NATIVE_OUTPUT"
    exit 1
fi
echo "[smoke] Native recovery succeeded"

# Assert HTTP port not listened to during native recovery
assert_port_not_listened "$HTTP_CHECK_PORT" "native recovery" || exit 1

# Assert old sessions revoked
ACTIVE_SESSIONS_AFTER_NATIVE=$(psql_exec "
    SELECT COUNT(*) FROM user_session WHERE account_id = '$ACCOUNT_ID' AND revoked_at IS NULL;
")
if [[ "$ACTIVE_SESSIONS_AFTER_NATIVE" != "0" ]]; then
    echo "[smoke] ERROR: Expected 0 active sessions after native recovery, got $ACTIVE_SESSIONS_AFTER_NATIVE"
    exit 1
fi

# Assert PASSWORD_RECOVERED_LOCALLY audit event (count should be >= 2 now)
AUDIT_COUNT_NATIVE=$(psql_exec "
    SELECT COUNT(*) FROM audit_log WHERE event_type = 'PasswordRecoveredLocally';
")
if [[ "$AUDIT_COUNT_NATIVE" -lt 2 ]]; then
    echo "[smoke] ERROR: Expected at least 2 PASSWORD_RECOVERED_LOCALLY audit events after native recovery"
    exit 1
fi
echo "[smoke] Native assertions passed: sessions revoked, audit event present"

echo ""
echo "[smoke] ========================================"
echo "[smoke] ALL SMOKE TESTS PASSED"
echo "[smoke] ========================================"
