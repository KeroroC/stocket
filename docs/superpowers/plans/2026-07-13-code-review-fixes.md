# Code Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复代码审查发现的邀请安全、前后端契约和部署配置问题，并增加覆盖真实协议的回归测试。

**Architecture:** 以后端 REST DTO 与控制器映射作为邀请协议的唯一来源，前端按相同字段和路由调用。密码策略在服务层强制执行；登录恒时校验使用合法 BCrypt 摘要；部署默认根据请求来源生成同源邀请链接，并通过 Nginx 脱敏日志避免令牌泄漏。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Security、PostgreSQL、Vue 3、TypeScript、Vitest、Nginx、Docker Compose

---

### Task 1: Enforce invite password policy

**Files:**
- Modify: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/AcceptInviteRequest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java`

- [x] Add integration tests proving passwords shorter than 12 characters and passwords equal to the normalized username are rejected without consuming the invite.
- [x] Run `cd backend && ./mvnw -Dtest=InviteIntegrationTest test` and confirm the new assertions fail.
- [x] Inject `PasswordPolicy` into `InviteService`, validate before creating the account, and return a stable password-policy problem response.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Restore constant-cost unknown-user checks

**Files:**
- Create: `backend/src/test/java/com/stocket/identity/internal/authentication/AuthenticationServiceTest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/authentication/AuthenticationService.java`

- [x] Add a test that verifies the dummy digest contains a structurally valid BCrypt payload.
- [x] Run the focused test and confirm it fails with the current digest.
- [x] Replace the malformed digest with a valid encoded BCrypt value.
- [x] Re-run the focused test and confirm it passes.

### Task 3: Align the invite API contract

**Files:**
- Modify: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteResponse.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/web/InviteAdminController.java`
- Modify: `frontend/src/api/identity.ts`
- Modify: `frontend/src/views/AdminInvitesView.vue`
- Modify: `frontend/src/views/AdminInvitesView.spec.ts`
- Create: `frontend/src/api/identity.spec.ts`

- [x] Add backend assertions for `status` and frontend assertions for custom expiry and the canonical revoke route.
- [x] Add a frontend API test that inspects the actual revoke method and path.
- [x] Run focused backend and frontend tests and confirm contract assertions fail.
- [x] Return an explicit invite status, send `expiresAt`, and call `POST /{inviteId}/revoke`.
- [x] Re-run focused tests and confirm they pass.

### Task 4: Make deployed invite links same-origin and redact logs

**Files:**
- Modify: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `deploy/gateway/default.conf`
- Modify: `deploy/compose.yml`
- Modify: `.env.example`

- [x] Add a backend test proving an empty configured frontend URL falls back to the current request origin.
- [x] Add configuration checks proving Compose does not inject a development URL and Nginx invite/API locations do not write access logs.
- [x] Run the focused checks and confirm the current configuration fails them.
- [x] Remove the localhost frontend default, expose an optional deployment override, and disable access logging for token-bearing routes.
- [x] Run `make compose-config` and the focused tests.

### Task 5: Full verification

**Files:**
- Modify only if verification exposes a regression in the files above.

- [x] Run `make test`.
- [x] Run `make build`.
- [x] Run `make compose-config`.
- [x] Inspect `git diff --check` and `git status --short`.
