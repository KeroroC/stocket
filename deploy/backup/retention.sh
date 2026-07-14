#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

root=${1:-${BACKUP_ROOT:-/var/lib/stocket/backups}}
mkdir -p "$root"
find "$root" -maxdepth 1 -type d -name '*.partial' -mmin +1440 -exec rm -rf -- {} +

valid=$(mktemp); trap 'rm -f "$valid"' EXIT
for directory in "$root"/[0-9]*Z; do
  [[ -d "$directory" ]] || continue
  id=$(basename "$directory")
  [[ "$id" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || continue
  if "$BACKUP_SCRIPT_DIR/verify.sh" "$directory" >/dev/null 2>&1; then printf '%s\n' "$id" >>"$valid"; fi
done
[[ -s "$valid" ]] || exit 0

python3 - "$root" "$valid" <<'PY'
import datetime as dt
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
ids = sorted(pathlib.Path(sys.argv[2]).read_text().splitlines(), reverse=True)
latest = None
link = root / "latest"
if link.is_symlink():
    latest = pathlib.Path(link.readlink()).name
keep = set(ids[:1])
if latest in ids:
    keep.add(latest)

daily = set()
weekly = set()
for value in ids:
    moment = dt.datetime.strptime(value, "%Y%m%dT%H%M%SZ")
    if len(daily) < 7 and moment.date() not in daily:
        daily.add(moment.date()); keep.add(value)
    week = moment.isocalendar()[:2]
    if len(weekly) < 4 and week not in weekly:
        weekly.add(week); keep.add(value)

for value in ids:
    if value not in keep:
        path = root / value
        for child in path.iterdir():
            if child.is_dir() and not child.is_symlink():
                import shutil; shutil.rmtree(child)
            else:
                child.unlink()
        path.rmdir()
PY
