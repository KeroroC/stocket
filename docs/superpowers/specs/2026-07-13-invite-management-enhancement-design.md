# 邀请管理功能完善设计文档

**日期:** 2026-07-13
**范围:** 最小可用版本（MVP）
**目标:** 延长邀请有效期、显示接受者信息、优化链接复制体验

---

## 1. 背景与目标

当前邀请管理功能已支持基本的创建、列表、撤销、接受邀请流程。本次改进聚焦三个方向：

1. **邀请链接管理** — 支持延长已有邀请的有效期
2. **邀请状态追踪** — 显示邀请被谁接受了、使用次数统计
3. **用户体验优化** — 优化邀请链接的复制交互

---

## 2. 后端改动

### 2.1 实体改造

#### MemberInvite 实体

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `max_uses` | Integer | 1 | 最大使用次数 |
| `use_count` | Integer | 0 | 已使用次数 |

```java
@Column(name = "max_uses", nullable = false)
private Integer maxUses = 1;

@Column(name = "use_count", nullable = false)
private Integer useCount = 0;
```

`isAvailable()` 方法更新为：
```java
public boolean isAvailable() {
    return acceptedAt == null 
        && revokedAt == null 
        && useCount < maxUses;
}
```

#### HouseholdMember 实体

新增字段：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `joined_via_invite_id` | UUID | null | 通过哪个邀请加入 |

```java
@Column(name = "joined_via_invite_id")
private UUID joinedViaInviteId;
```

### 2.2 数据库迁移

```sql
ALTER TABLE member_invite ADD COLUMN max_uses INTEGER NOT NULL DEFAULT 1;
ALTER TABLE member_invite ADD COLUMN use_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE household_member ADD COLUMN joined_via_invite_id UUID;
```

### 2.3 InviteService 改动

#### createInvite 方法

- `CreateInviteRequest` 新增可选参数 `maxUses`（默认 1）
- 保存到 `MemberInvite.maxUses`

#### acceptInvite 方法

- 验证 `useCount < maxUses`
- 成功后 `useCount++`
- 创建 `HouseholdMember` 时设置 `joinedViaInviteId`

#### 新增 extendInvite 方法

```java
@Transactional
public boolean extendInvite(UUID householdId, UUID inviteId, 
                            Instant newExpiry, Instant now) {
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
        throw new InvalidExpiryException("Expiry cannot exceed " + MAX_EXPIRY_DAYS + " days");
    }
    
    invite.setExpiresAt(newExpiry);
    inviteRepository.save(invite);
    
    publishAuditEvent("InviteExtended", "SUCCESS", null, Map.of(
            "inviteId", invite.getId().toString(),
            "newExpiry", newExpiry.toString()));
    
    return true;
}
```

#### listInvites 方法扩展

返回数据新增：
- `acceptedByUsernames` — 通过此邀请加入的用户名列表
- `useCount` / `maxUses`

### 2.4 Controller 改动

#### InviteAdminController

新增端点：
```java
@PatchMapping("/{inviteId}/extend")
ResponseEntity<?> extendInvite(
        @AuthenticationPrincipal IdentityPrincipal principal,
        @PathVariable UUID inviteId,
        @Valid @RequestBody ExtendInviteRequest request) {
    // ...
}
```

#### DTO 改动

`CreateInviteRequest` 新增：
```java
public record CreateInviteRequest(
        @NotNull IdentityRole role,
        Instant expiresAt,
        Integer maxUses  // 可选，默认 1
) {}
```

新增 `ExtendInviteRequest`：
```java
public record ExtendInviteRequest(
        @NotNull Instant expiresAt
) {}
```

`InviteResponse` 扩展：
```java
public record InviteResponse(
        UUID id,
        IdentityRole role,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        Integer useCount,           // 新增
        Integer maxUses,            // 新增
        List<String> acceptedBy     // 新增：接受者用户名列表
) {}
```

---

## 3. 前端改动

### 3.1 API 层 (identity.ts)

新增函数：
```typescript
export function extendInvite(inviteId: string, expiresAt: string): Promise<void> {
  return apiRequest<void>(`/api/v1/admin/invites/${inviteId}/extend`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expiresAt }),
  })
}
```

更新 `InviteListItem` 类型：
```typescript
export interface InviteListItem {
  id: string
  role: string
  expiresAt: string
  status: string
  createdAt: string
  useCount: number      // 新增
  maxUses: number       // 新增
  acceptedBy: string[]  // 新增
}
```

更新 `CreateInviteRequest`：
```typescript
export interface CreateInviteRequest {
  role: string
  expiresInHours?: number
  maxUses?: number  // 新增
}
```

### 3.2 AdminInvitesView.vue 改动

#### 邀请列表增强

每个邀请项显示：
- 使用次数：`已使用 X/Y 次`
- 接受者：`接受者: user1, user2`（如有）
- "延长有效期"按钮（仅对未过期、未撤销邀请显示）

#### 创建邀请对话框增强

新增字段：
- "最大使用次数"输入框（默认 1，最小 1）

#### 延长有效期对话框

新增对话框：
- 日期时间选择器（限制范围：当前时间 ~ 30天后）
- 确认/取消按钮

#### 链接复制优化

创建邀请成功后：
- 显示复制按钮（已存在，优化样式）
- 复制成功后显示 Toast 提示

---

## 4. 错误处理

| 场景 | HTTP 状态码 | 错误码 | 消息 |
|------|-------------|--------|------|
| 延长已过期邀请 | 409 | INVITE_ALREADY_EXPIRED | 邀请已过期，无法延长 |
| 延长已撤销邀请 | 409 | INVITE_ALREADY_REVOKED | 邀请已撤销，无法延长 |
| 新过期时间无效 | 400 | INVALID_EXPIRY | 过期时间必须在未来 |
| 使用次数达上限 | 410 | INVITE_MAX_USES_REACHED | 邀请已达使用上限 |

---

## 5. 兼容性考虑

- `maxUses` 默认值为 1，保持向后兼容
- `useCount` 默认值为 0，现有邀请数据自动兼容
- `acceptedBy` 列表为空时返回空数组，不影响现有前端
- `ExtendInviteRequest` 是纯新增，不影响现有接口

---

## 6. 测试要点

### 后端测试

1. 创建邀请时设置 maxUses > 1
2. 接受邀请后 useCount 递增
3. useCount 达到 maxUses 后拒绝新的接受请求
4. 延长未过期邀请的有效期
5. 延长已过期/已撤销邀请应返回错误
6. listInvites 返回正确的 useCount 和 acceptedBy

### 前端测试

1. 创建邀请对话框显示最大使用次数字段
2. 邀请列表正确显示使用次数和接受者
3. 延长有效期按钮仅对有效邀请显示
4. 复制链接功能正常工作
