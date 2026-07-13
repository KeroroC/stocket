# 邀请管理功能完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善邀请管理功能，支持延长邀请有效期、显示接受者信息、优化链接复制体验

**Architecture:** 后端扩展 MemberInvite 实体增加 maxUses/useCount 字段，新增 extendInvite 服务方法和 PATCH 端点；前端增强 AdminInvitesView 显示使用统计和接受者信息，新增延长有效期对话框

**Tech Stack:** Spring Boot 3, JPA/Hibernate, PostgreSQL, Flyway, Vue 3, TypeScript, Element Plus, Vitest

---

## File Structure

### Backend Files
- Modify: `backend/src/main/resources/db/migration/V2__identity_and_audit.sql` → 新建 V3 迁移
- Create: `backend/src/main/resources/db/migration/V3__invite_enhancements.sql`
- Modify: `backend/src/main/java/com/stocket/identity/internal/domain/MemberInvite.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/domain/HouseholdMember.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteAdminController.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/CreateInviteRequest.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/ExtendInviteRequest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteResponse.java`
- Modify: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`

### Frontend Files
- Modify: `frontend/src/api/identity.ts`
- Modify: `frontend/src/views/AdminInvitesView.vue`
- Modify: `frontend/src/views/AdminInvitesView.spec.ts`

---

## Task 1: Database Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__invite_enhancements.sql`

- [ ] **Step 1: Create migration file**

```sql
-- Invite enhancements: max uses, use count, and invite tracking on member

ALTER TABLE member_invite ADD COLUMN max_uses INTEGER NOT NULL DEFAULT 1;
ALTER TABLE member_invite ADD COLUMN use_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE household_member ADD COLUMN joined_via_invite_id UUID REFERENCES member_invite(id);

-- Update the availability check to include use_count
DROP INDEX IF EXISTS member_invite_active_idx;
CREATE INDEX member_invite_active_idx
    ON member_invite(household_id, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL AND use_count < max_uses;
```

- [ ] **Step 2: Verify migration applies**

Run: `cd backend && ./mvnw flyway:info`
Expected: V3 migration appears as pending

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__invite_enhancements.sql
git commit -m "feat(db): add invite enhancement migration (max_uses, use_count, joined_via_invite_id)"
```

---

## Task 2: Backend Entity Changes

**Files:**
- Modify: `backend/src/main/java/com/stocket/identity/internal/domain/MemberInvite.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/domain/HouseholdMember.java`

- [ ] **Step 1: Add maxUses and useCount to MemberInvite**

Add fields after `revokedAt`:

```java
@Column(name = "max_uses", nullable = false)
private Integer maxUses = 1;

@Column(name = "use_count", nullable = false)
private Integer useCount = 0;
```

Add getters and setters:

```java
public Integer getMaxUses() {
    return maxUses;
}

public void setMaxUses(Integer maxUses) {
    this.maxUses = maxUses;
}

public Integer getUseCount() {
    return useCount;
}

public void setUseCount(Integer useCount) {
    this.useCount = useCount;
}
```

Update `isAvailable()` method:

```java
public boolean isAvailable() {
    return acceptedAt == null && revokedAt == null && useCount < maxUses;
}
```

- [ ] **Step 2: Add joinedViaInviteId to HouseholdMember**

Add field after `updatedAt`:

```java
@Column(name = "joined_via_invite_id")
private UUID joinedViaInviteId;
```

Add getter and setter:

```java
public UUID getJoinedViaInviteId() {
    return joinedViaInviteId;
}

public void setJoinedViaInviteId(UUID joinedViaInviteId) {
    this.joinedViaInviteId = joinedViaInviteId;
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/stocket/identity/internal/domain/MemberInvite.java \
        backend/src/main/java/com/stocket/identity/internal/domain/HouseholdMember.java
git commit -m "feat(domain): add maxUses, useCount to MemberInvite and joinedViaInviteId to HouseholdMember"
```

---

## Task 3: InviteService - Extend Invite Method

**Files:**
- Modify: `backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java`

- [ ] **Step 1: Add new exception classes**

Add after `DuplicateUsernameException`:

```java
@ResponseStatus(HttpStatus.CONFLICT)
public static class InviteAlreadyRevokedException extends RuntimeException {
    public InviteAlreadyRevokedException() {
        super("Invite has already been revoked");
    }
}

@ResponseStatus(HttpStatus.CONFLICT)
public static class InviteAlreadyExpiredException extends RuntimeException {
    public InviteAlreadyExpiredException() {
        super("Invite has already expired");
    }
}
```

- [ ] **Step 2: Add extendInvite method**

Add after `revokeInvite` method:

```java
/**
 * Extends the expiry of an invite. Only valid for non-expired, non-revoked invites.
 */
@Transactional
public boolean extendInvite(UUID householdId, UUID inviteId, Instant newExpiry, Instant now) {
    MemberInvite invite = inviteRepository.findByHouseholdIdAndId(householdId, inviteId)
            .orElseThrow(() -> new InviteNotFoundException());

    if (invite.getRevokedAt() != null) {
        throw new InviteAlreadyRevokedException();
    }
    if (invite.getExpiresAt().isBefore(now)) {
        throw new InviteAlreadyExpiredException();
    }
    if (newExpiry.isBefore(now)) {
        throw new InvalidExpiryException("New expiry must be in the future");
    }
    if (newExpiry.isAfter(now.plus(MAX_EXPIRY_DAYS, ChronoUnit.DAYS))) {
        throw new InvalidExpiryException("Expiry cannot be more than " + MAX_EXPIRY_DAYS + " days");
    }

    invite.setExpiresAt(newExpiry);
    inviteRepository.save(invite);

    publishAuditEvent("InviteExtended", "SUCCESS", null, Map.of(
            "inviteId", invite.getId().toString(),
            "newExpiry", newExpiry.toString()));

    return true;
}
```

- [ ] **Step 3: Update InviteInfo record to include new fields**

Replace the existing `InviteInfo` record:

```java
public record InviteInfo(
        UUID id,
        IdentityRole role,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        Integer useCount,
        Integer maxUses,
        List<String> acceptedBy) {
}
```

- [ ] **Step 4: Update listInvites to populate new fields**

Replace the `listInvites` method:

```java
@Transactional(readOnly = true)
public List<InviteInfo> listInvites(UUID householdId) {
    return inviteRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId).stream()
            .map(invite -> {
                // Find members who joined via this invite
                List<String> acceptedByNames = householdMemberRepository
                        .findByJoinedViaInviteId(invite.getId())
                        .stream()
                        .map(m -> m.getAccount().getDisplayName())
                        .toList();

                return new InviteInfo(
                        invite.getId(),
                        invite.getRole(),
                        invite.getExpiresAt(),
                        invite.getAcceptedAt(),
                        invite.getRevokedAt(),
                        invite.getCreatedAt(),
                        invite.getUseCount(),
                        invite.getMaxUses(),
                        acceptedByNames);
            })
            .toList();
}
```

- [ ] **Step 5: Update acceptInvite to increment useCount and set joinedViaInviteId**

In `acceptInvite` method, after creating the HouseholdMember (around line 254-256), add `joinedViaInviteId`:

```java
HouseholdMember member = new HouseholdMember(
        UUID.randomUUID(), household, account, invite.getRole(), now);
member.setJoinedViaInviteId(invite.getId());
householdMemberRepository.save(member);
```

After saving the member (around line 259-261), update useCount instead of setting acceptedAt:

```java
// Mark invite as used (increment use count)
invite.setUseCount(invite.getUseCount() + 1);

// Only set acceptedAt for single-use invites (backward compatibility)
if (invite.getMaxUses() == 1) {
    invite.setAcceptedAt(now);
    invite.setAcceptedBy(account);
}
inviteRepository.save(invite);
```

- [ ] **Step 6: Update createInvite to accept maxUses parameter**

Change method signature to include maxUses:

```java
@Transactional
public InviteCreationResult createInvite(UUID householdId, IdentityRole role,
                                          Instant customExpiry, Integer maxUses,
                                          UUID createdByAccountId, Instant now) {
```

After creating the `MemberInvite` object (around line 120-123), set maxUses:

```java
MemberInvite invite = new MemberInvite(
        UUID.randomUUID(), household, tokenHash,
        role, expiresAt, createdBy, now);
if (maxUses != null && maxUses > 0) {
    invite.setMaxUses(maxUses);
}
inviteRepository.save(invite);
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java
git commit -m "feat(invite): add extendInvite, update listInvites with acceptedBy, support maxUses"
```

---

## Task 4: HouseholdMemberRepository - Add findByJoinedViaInviteId

**Files:**
- Modify: `backend/src/main/java/com/stocket/identity/internal/persistence/HouseholdMemberRepository.java`

- [ ] **Step 1: Add query method**

```java
List<HouseholdMember> findByJoinedViaInviteId(UUID inviteId);
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/stocket/identity/internal/persistence/HouseholdMemberRepository.java
git commit -m "feat(persistence): add findByJoinedViaInviteId to HouseholdMemberRepository"
```

---

## Task 5: Backend DTOs and Controller

**Files:**
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/CreateInviteRequest.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/ExtendInviteRequest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteResponse.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteAdminController.java`

- [ ] **Step 1: Update CreateInviteRequest to include maxUses**

```java
package com.stocket.identity.internal.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import com.stocket.identity.IdentityRole;

public record CreateInviteRequest(
        @NotNull IdentityRole role,
        Instant expiresAt,
        Integer maxUses
) {
}
```

- [ ] **Step 2: Create ExtendInviteRequest**

```java
package com.stocket.identity.internal.web;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record ExtendInviteRequest(
        @NotNull Instant expiresAt
) {
}
```

- [ ] **Step 3: Update InviteResponse to include new fields**

```java
package com.stocket.identity.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocket.identity.IdentityRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InviteResponse(
        UUID id,
        IdentityRole role,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        Integer useCount,
        Integer maxUses,
        List<String> acceptedBy
) {
}
```

- [ ] **Step 4: Update InviteAdminController - createInvite**

Update the `createInvite` method to pass `maxUses`:

```java
@PostMapping
ResponseEntity<?> createInvite(
        @AuthenticationPrincipal IdentityPrincipal principal,
        @Valid @RequestBody CreateInviteRequest request) {
    UUID householdId = resolveHouseholdId(principal.accountId());
    Instant now = clock.instant();

    try {
        InviteService.InviteCreationResult result = inviteService.createInvite(
                householdId, request.role(), request.expiresAt(),
                request.maxUses(), principal.accountId(), now);

        // ... rest of the method remains the same
```

- [ ] **Step 5: Update InviteAdminController - listInvites**

Update the `listInvites` method to include new fields:

```java
@GetMapping
ResponseEntity<List<InviteResponse>> listInvites(
        @AuthenticationPrincipal IdentityPrincipal principal) {
    UUID householdId = resolveHouseholdId(principal.accountId());
    List<InviteService.InviteInfo> invites = inviteService.listInvites(householdId);

    List<InviteResponse> response = invites.stream()
            .map(info -> new InviteResponse(
                    info.id(),
                    info.role(),
                    info.expiresAt(),
                    info.acceptedAt(),
                    info.revokedAt(),
                    info.createdAt(),
                    info.useCount(),
                    info.maxUses(),
                    info.acceptedBy()))
            .toList();

    return ResponseEntity.ok(response);
}
```

- [ ] **Step 6: Add extendInvite endpoint**

Add after `revokeInvite` method:

```java
@PatchMapping("/{inviteId}/extend")
ResponseEntity<?> extendInvite(
        @AuthenticationPrincipal IdentityPrincipal principal,
        @PathVariable UUID inviteId,
        @Valid @RequestBody ExtendInviteRequest request) {
    UUID householdId = resolveHouseholdId(principal.accountId());
    Instant now = clock.instant();

    try {
        inviteService.extendInvite(householdId, inviteId, request.expiresAt(), now);
        return ResponseEntity.noContent().build();
    } catch (InviteService.InviteAlreadyRevokedException e) {
        return conflictProblem("INVITE_ALREADY_REVOKED", e.getMessage());
    } catch (InviteService.InviteAlreadyExpiredException e) {
        return conflictProblem("INVITE_ALREADY_EXPIRED", e.getMessage());
    } catch (InviteService.InvalidExpiryException e) {
        return badRequestProblem("INVALID_EXPIRY", e.getMessage());
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/stocket/identity/internal/web/CreateInviteRequest.java \
        backend/src/main/java/com/stocket/identity/internal/web/ExtendInviteRequest.java \
        backend/src/main/java/com/stocket/identity/internal/web/InviteResponse.java \
        backend/src/main/java/com/stocket/identity/internal/web/InviteAdminController.java
git commit -m "feat(api): add extend invite endpoint, update DTOs with maxUses/useCount/acceptedBy"
```

---

## Task 6: Backend Integration Tests

**Files:**
- Modify: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`

- [ ] **Step 1: Add test for creating invite with maxUses**

```java
@Test
void createInviteWithMaxUses() throws Exception {
    String adminCookie = loginAsAdmin();

    String responseJson = mockMvc.perform(post("/api/v1/admin/invites")
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"role":"MEMBER","maxUses":3}
                            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

    UUID inviteId = UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
    Integer maxUses = jdbc.queryForObject(
            "SELECT max_uses FROM member_invite WHERE id = ?",
            Integer.class, inviteId);
    assertThat(maxUses).isEqualTo(3);
}
```

- [ ] **Step 2: Add test for extending invite**

```java
@Test
void extendInviteReturns204() throws Exception {
    String adminCookie = loginAsAdmin();

    // Create invite
    String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"role":"MEMBER"}
                            """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    UUID inviteId = UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());

    // Extend invite
    Instant newExpiry = Instant.now().plus(Duration.ofDays(7));
    mockMvc.perform(patch("/api/v1/admin/invites/{inviteId}/extend", inviteId)
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"expiresAt":"%s"}
                            """.formatted(newExpiry.toString())))
            .andExpect(status().isNoContent());

    // Verify expiry updated in database
    Instant expiresAt = jdbc.queryForObject(
            "SELECT expires_at FROM member_invite WHERE id = ?",
            Instant.class, inviteId);
    assertThat(expiresAt).isEqualTo(newExpiry);
}
```

- [ ] **Step 3: Add test for extending expired invite fails**

```java
@Test
void extendExpiredInviteReturns409() throws Exception {
    String adminCookie = loginAsAdmin();

    // Create invite with short expiry
    Instant shortExpiry = clock.instant().plus(Duration.ofMinutes(5));
    String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"role":"MEMBER","expiresAt":"%s"}
                            """.formatted(shortExpiry.toString())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    UUID inviteId = UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());

    // Advance clock past expiry
    ((MutableClock) clock).advance(Duration.ofMinutes(10));

    // Try to extend expired invite
    Instant newExpiry = Instant.now().plus(Duration.ofDays(7));
    mockMvc.perform(patch("/api/v1/admin/invites/{inviteId}/extend", inviteId)
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"expiresAt":"%s"}
                            """.formatted(newExpiry.toString())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVITE_ALREADY_EXPIRED"));
}
```

- [ ] **Step 4: Add test for listInvites showing acceptedBy**

```java
@Test
void listInvitesShowsAcceptedBy() throws Exception {
    String adminCookie = loginAsAdmin();

    // Create invite
    String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                    .with(csrf())
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"role":"MEMBER"}
                            """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    String inviteLink = com.jayway.jsonpath.JsonPath.read(createResponse, "$.inviteLink").toString();
    String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

    // Accept invite
    mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content("""
                            {"username":"AcceptedUser","displayName":"接受用户","password":"strongpassword123"}
                            """))
            .andExpect(status().isCreated());

    // List invites should show acceptedBy
    mockMvc.perform(get("/api/v1/admin/invites")
                    .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].acceptedBy").isArray())
            .andExpect(jsonPath("$[0].acceptedBy[0]").value("接受用户"))
            .andExpect(jsonPath("$[0].useCount").value(1));
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java
git commit -m "test(invite): add tests for maxUses, extendInvite, and acceptedBy tracking"
```

---

## Task 7: Frontend API Layer

**Files:**
- Modify: `frontend/src/api/identity.ts`

- [ ] **Step 1: Update InviteListItem interface**

```typescript
export interface InviteListItem {
  id: string
  role: string
  expiresAt: string
  status: string
  createdAt: string
  useCount: number
  maxUses: number
  acceptedBy: string[]
}
```

- [ ] **Step 2: Update CreateInviteRequest interface**

```typescript
export interface CreateInviteRequest {
  role: string
  expiresInHours?: number
  maxUses?: number
}
```

- [ ] **Step 3: Add extendInvite function**

Add after `revokeInvite`:

```typescript
export function extendInvite(inviteId: string, expiresAt: string): Promise<void> {
  return apiRequest<void>(`/api/v1/admin/invites/${inviteId}/extend`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expiresAt }),
  })
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/identity.ts
git commit -m "feat(api): add extendInvite function and update invite types"
```

---

## Task 8: Frontend AdminInvitesView

**Files:**
- Modify: `frontend/src/views/AdminInvitesView.vue`

- [ ] **Step 1: Import extendInvite API**

Update imports at top of script:

```typescript
import {
  getInvites as apiGetInvites,
  createInvite as apiCreateInvite,
  revokeInvite as apiRevokeInvite,
  extendInvite as apiExtendInvite,
} from '../api/identity'
```

- [ ] **Step 2: Add extend dialog state**

Add after `copied` ref:

```typescript
// Extend invite dialog
const showExtendDialog = ref(false)
const extendInviteId = ref('')
const newExpiryDate = ref('')
const extendSubmitting = ref(false)
const extendError = ref('')
```

- [ ] **Step 3: Add maxUses to create dialog state**

Update `newExpiresInHours` ref and add `newMaxUses`:

```typescript
const newMaxUses = ref(1)
```

Update `openCreateDialog`:

```typescript
function openCreateDialog() {
  newRole.value = 'MEMBER'
  newExpiresInHours.value = 24
  newMaxUses.value = 1
  createError.value = ''
  showCreateDialog.value = true
}
```

- [ ] **Step 4: Update handleCreateInvite to include maxUses**

```typescript
async function handleCreateInvite() {
  createError.value = ''
  createSubmitting.value = true
  try {
    const result = await apiCreateInvite({
      role: newRole.value,
      expiresInHours: newExpiresInHours.value,
      maxUses: newMaxUses.value,
    })
    showCreateDialog.value = false
    resultInviteLink.value = result.inviteLink
    showResultDialog.value = true
    await loadInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) createError.value = msg
  } finally {
    createSubmitting.value = false
  }
}
```

- [ ] **Step 5: Add extend invite functions**

```typescript
function openExtendDialog(inviteId: string, currentExpiry: string) {
  extendInviteId.value = inviteId
  extendError.value = ''
  // Default to 7 days from now
  const defaultDate = new Date()
  defaultDate.setDate(defaultDate.getDate() + 7)
  newExpiryDate.value = defaultDate.toISOString().slice(0, 16)
  showExtendDialog.value = true
}

async function handleExtendInvite() {
  extendError.value = ''
  extendSubmitting.value = true
  try {
    const expiresAt = new Date(newExpiryDate.value).toISOString()
    await apiExtendInvite(extendInviteId.value, expiresAt)
    showExtendDialog.value = false
    await loadInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) extendError.value = msg
  } finally {
    extendSubmitting.value = false
  }
}
```

- [ ] **Step 6: Add helper function to check if invite can be extended**

```typescript
function canExtend(invite: InviteListItem): boolean {
  return invite.status === 'PENDING'
}
```

- [ ] **Step 7: Update template - invite list to show use count and acceptedBy**

Replace the invite item content inside the `<li>`:

```html
<li v-for="invite in invites" :key="invite.id" class="invite-item">
  <div class="invite-item-info">
    <el-tag :type="formatStatusType(invite.status) as any" size="small">
      {{ formatStatus(invite.status) }}
    </el-tag>
    <span class="invite-role">{{ formatRole(invite.role) }}</span>
    <span class="invite-expires">
      有效期至：{{ new Date(invite.expiresAt).toLocaleString() }}
    </span>
    <span v-if="invite.maxUses > 1" class="invite-uses">
      使用次数：{{ invite.useCount }}/{{ invite.maxUses }}
    </span>
    <span v-if="invite.acceptedBy && invite.acceptedBy.length > 0" class="invite-accepted-by">
      接受者：{{ invite.acceptedBy.join(', ') }}
    </span>
  </div>
  <div class="invite-item-actions">
    <button
      v-if="canExtend(invite)"
      class="member-action-btn extend-btn"
      @click="openExtendDialog(invite.id, invite.expiresAt)"
    >
      延长
    </button>
    <button
      v-if="invite.status === 'PENDING'"
      class="member-action-btn"
      @click="handleRevokeInvite(invite.id)"
    >
      撤销
    </button>
  </div>
</li>
```

- [ ] **Step 8: Update create invite dialog template**

Add maxUses input after the expiry input:

```html
<div class="form-field">
  <label for="inviteMaxUses">最大使用次数</label>
  <input
    id="inviteMaxUses"
    v-model.number="newMaxUses"
    type="number"
    min="1"
    max="100"
  />
</div>
```

- [ ] **Step 9: Add extend dialog template**

Add after the result dialog:

```html
<!-- Extend invite dialog -->
<el-dialog
  v-model="showExtendDialog"
  title="延长邀请有效期"
  :close-on-click-modal="false"
  width="400px"
>
  <form class="auth-form" @submit.prevent="handleExtendInvite">
    <div v-if="extendError" role="alert" class="auth-error">
      {{ extendError }}
    </div>

    <div class="form-field">
      <label for="newExpiry">新的过期时间</label>
      <input
        id="newExpiry"
        v-model="newExpiryDate"
        type="datetime-local"
        :min="new Date().toISOString().slice(0, 16)"
      />
    </div>
  </form>
  <template #footer>
    <button class="auth-logout-btn" style="width: auto; height: 36px;" @click="showExtendDialog = false">取消</button>
    <button class="auth-submit" style="width: auto; height: 36px;" :disabled="extendSubmitting" @click="handleExtendInvite">
      {{ extendSubmitting ? '提交中...' : '确认延长' }}
    </button>
  </template>
</el-dialog>
```

- [ ] **Step 10: Add CSS for new elements**

Add to the style section:

```css
.invite-uses {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  margin-left: 0.5rem;
}

.invite-accepted-by {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  margin-left: 0.5rem;
}

.invite-item-actions {
  display: flex;
  gap: 0.5rem;
}

.extend-btn {
  background-color: var(--color-primary);
  color: white;
}
```

- [ ] **Step 11: Commit**

```bash
git add frontend/src/views/AdminInvitesView.vue
git commit -m "feat(ui): enhance AdminInvitesView with extend, maxUses, and acceptedBy display"
```

---

## Task 9: Frontend Tests

**Files:**
- Modify: `frontend/src/views/AdminInvitesView.spec.ts`

- [ ] **Step 1: Update mock to include extendInvite**

Update the vi.mock block:

```typescript
vi.mock('../api/identity', () => ({
  initialize: vi.fn(),
  getSetupStatus: vi.fn(),
  refreshCsrf: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentAccount: vi.fn(),
  changePassword: vi.fn(),
  getInviteStatus: vi.fn(),
  acceptInvite: vi.fn(),
  updateProfile: vi.fn(),
  getSessions: vi.fn(),
  revokeSession: vi.fn(),
  revokeOtherSessions: vi.fn(),
  getMembers: vi.fn(),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  resetMemberPassword: vi.fn(),
  getInvites: vi.fn().mockResolvedValue([]),
  createInvite: vi.fn(),
  revokeInvite: vi.fn(),
  extendInvite: vi.fn(),
}))
```

- [ ] **Step 2: Update invitesFixture to include new fields**

```typescript
const invitesFixture = [
  {
    id: 'inv-1',
    role: 'MEMBER',
    expiresAt: '2026-07-13T12:00:00Z',
    status: 'PENDING',
    createdAt: '2026-07-12T12:00:00Z',
    useCount: 0,
    maxUses: 1,
    acceptedBy: [],
  },
  {
    id: 'inv-2',
    role: 'VIEWER',
    expiresAt: '2026-07-11T12:00:00Z',
    status: 'EXPIRED',
    createdAt: '2026-07-10T12:00:00Z',
    useCount: 0,
    maxUses: 1,
    acceptedBy: [],
  },
  {
    id: 'inv-3',
    role: 'MEMBER',
    expiresAt: '2026-07-14T12:00:00Z',
    status: 'ACCEPTED',
    createdAt: '2026-07-09T12:00:00Z',
    useCount: 1,
    maxUses: 1,
    acceptedBy: ['接受用户'],
  },
]
```

- [ ] **Step 3: Add test for showing acceptedBy**

```typescript
it('shows acceptedBy for accepted invites', async () => {
  render(AdminInvitesView)

  await waitFor(() => {
    expect(screen.getByText(/接受者：接受用户/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Add test for showing use count**

```typescript
it('shows use count for multi-use invites', async () => {
  const multiUseFixture = [
    {
      id: 'inv-multi',
      role: 'MEMBER',
      expiresAt: '2026-07-14T12:00:00Z',
      status: 'PENDING',
      createdAt: '2026-07-12T12:00:00Z',
      useCount: 2,
      maxUses: 5,
      acceptedBy: ['用户1', '用户2'],
    },
  ]
  vi.mocked(identityApi.getInvites).mockResolvedValue(multiUseFixture)

  render(AdminInvitesView)

  await waitFor(() => {
    expect(screen.getByText(/使用次数：2\/5/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 5: Add test for extend button visibility**

```typescript
it('shows extend button only for pending invites', async () => {
  render(AdminInvitesView)

  await waitFor(() => {
    const extendButtons = screen.getAllByText('延长')
    expect(extendButtons).toHaveLength(1) // Only inv-1 is PENDING
  })
})
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/AdminInvitesView.spec.ts
git commit -m "test(ui): update AdminInvitesView tests for new invite features"
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run backend tests**

Run: `cd backend && ./mvnw test -pl . -Dtest=InviteIntegrationTest`
Expected: All tests pass

- [ ] **Step 2: Run frontend tests**

Run: `cd frontend && npm test`
Expected: All tests pass

- [ ] **Step 3: Verify build**

Run: `cd backend && ./mvnw package -DskipTests && cd ../frontend && npm run build`
Expected: Both build successfully

- [ ] **Step 4: Final commit with all changes**

```bash
git add -A
git status
```

Verify all changes are staged, then:
```bash
git commit -m "feat: complete invite management enhancement (extend, maxUses, acceptedBy, copy link)"
```
