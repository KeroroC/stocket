# 身份与家庭实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一次性家庭初始化、数据库持久化会话、账户与邀请管理、三角色授权、本地管理员恢复、身份审计和对应 Vue 管理界面。

**Architecture:** `identity` 模块拥有家庭、账户、成员、邀请和会话，使用 Spring Security 7 自定义不透明 Cookie 会话过滤器；`audit` 模块只通过 Spring Modulith 事件接收身份审计事实。后端先形成完整、安全、可测试的 API，再由 Vue 身份状态机和页面接入；所有写请求使用 SPA Cookie CSRF。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Security 7、Spring Data JPA、Spring Modulith 2.0.5、Flyway、PostgreSQL 17、Testcontainers、Vue 3.5、TypeScript 5.9、Element Plus、Vitest、GraalVM Native Image

---

## 执行约束

- 开始执行前必须使用 `using-git-worktrees` 创建 `codex/identity-household` 独立 worktree；不要直接在 `main` 实施。
- 每个任务由新的实现子代理完成，再依次交给规格审查子代理和代码质量审查子代理；两轮审查通过后才进入下一任务。
- 每个功能严格遵循红—绿—重构：先运行并观察测试按预期失败，再写最小实现；不提交红态测试。
- 每次提交前按当前任务 Files 清单精确暂存，并运行 `git diff --cached --check`；禁止用目录级 `git add` 带入其他子代理改动。
- Spring Security 7 当前文档确认：SPA 使用 `http.csrf(csrf -> csrf.spa())`；登录和退出会清除旧 CSRF Cookie，因此前端必须重新请求 `/api/v1/auth/csrf`。
- 不引入 JWT、Spring Session、Redis、邮件、公开注册、多家庭或阶段三业务。

## 文件结构

```text
backend/src/main/java/com/stocket/
├── identity/
│   ├── IdentityAuditEvent.java                 # audit 模块可订阅的公开事件
│   ├── IdentityRole.java                       # 跨模块可引用的角色
│   └── internal/
│       ├── config/IdentityProperties.java      # Cookie、会话、邀请、密码、限流配置
│       ├── domain/                             # JPA 聚合与枚举
│       ├── persistence/                        # 仅 identity 内部可见的 Repository
│       ├── security/                           # SecurityFilterChain、会话过滤器、身份主体
│       ├── setup/                              # 一次性初始化
│       ├── authentication/                     # 登录、退出、令牌与限流
│       ├── account/                            # 本人资料、密码、会话
│       ├── member/                             # 管理员成员管理
│       ├── invite/                             # 邀请生命周期
│       ├── maintenance/                        # 无 Web 的本地管理员恢复入口
│       └── web/                                # Controller、请求和响应 DTO
└── audit/internal/                             # 审计实体、Repository、事件监听器

backend/src/test/java/com/stocket/
├── identity/                                   # 单元、MVC、PostgreSQL 集成与安全测试
├── audit/IdentityAuditIntegrationTest.java
├── identity/maintenance/AdminRecoveryCommandTest.java
└── DatabaseMigrationTest.java

frontend/src/
├── api/http.ts                                 # CSRF、ProblemDetail、同源请求封装
├── api/identity.ts                             # 身份 API 类型与调用
├── auth/AuthState.ts                           # 身份状态联合类型
├── auth/useAuth.ts                             # 启动、登录、退出和状态切换
├── components/AppShell.vue
├── views/SetupView.vue
├── views/LoginView.vue
├── views/PasswordChangeView.vue
├── views/InviteAcceptView.vue
├── views/AccountView.vue
├── views/AdminMembersView.vue
└── views/AdminInvitesView.vue
```

## Task 1：配置与身份数据库迁移

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/db/migration/V2__identity_and_audit.sql`
- Modify: `backend/src/test/java/com/stocket/DatabaseMigrationTest.java`

- [ ] **Step 1：扩展迁移测试并确认失败**

在 `DatabaseMigrationTest` 新增测试，逐表断言并验证单家庭约束：

```java
@Test
void createsIdentityAndAuditSchema() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForList("""
            select table_name from information_schema.tables
            where table_schema = 'public'
              and table_name in ('household', 'user_account', 'household_member',
                                 'member_invite', 'user_session', 'audit_log')
            order by table_name
            """, String.class))
            .containsExactly("audit_log", "household", "household_member",
                    "member_invite", "user_account", "user_session");

    jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, ?, ?)",
            UUID.randomUUID(), "家", "Asia/Shanghai");
    assertThatThrownBy(() -> jdbc.update(
            "insert into household(id, singleton_key, name, timezone) values (?, 1, ?, ?)",
            UUID.randomUUID(), "第二个家", "Asia/Shanghai"))
            .isInstanceOf(DataAccessException.class);
}
```

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest test`

Expected: FAIL，提示 `household` 等表不存在。

- [ ] **Step 2：创建 V2 迁移**

迁移必须创建六张表、外键、检查约束和索引。关键约束使用以下 SQL：

```sql
create table household (
    id uuid primary key,
    singleton_key smallint not null default 1 check (singleton_key = 1),
    name varchar(120) not null,
    timezone varchar(80) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint household_singleton unique (singleton_key)
);

create table user_account (
    id uuid primary key,
    username varchar(64) not null,
    normalized_username varchar(64) not null unique,
    display_name varchar(120) not null,
    email varchar(254),
    password_hash varchar(255) not null,
    status varchar(16) not null check (status in ('ACTIVE', 'DISABLED')),
    must_change_password boolean not null default false,
    credentials_changed_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table household_member (
    id uuid primary key,
    household_id uuid not null references household(id),
    account_id uuid not null references user_account(id),
    role varchar(16) not null check (role in ('ADMIN', 'MEMBER', 'VIEWER')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint household_member_account unique (household_id, account_id)
);

create table member_invite (
    id uuid primary key,
    household_id uuid not null references household(id),
    token_hash char(64) not null unique,
    role varchar(16) not null check (role in ('ADMIN', 'MEMBER', 'VIEWER')),
    expires_at timestamptz not null,
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_by uuid not null references user_account(id),
    accepted_by uuid references user_account(id),
    created_at timestamptz not null default now(),
    check (accepted_at is null or revoked_at is null)
);

create table user_session (
    id uuid primary key,
    account_id uuid not null references user_account(id),
    token_hash char(64) not null unique,
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    idle_expires_at timestamptz not null,
    absolute_expires_at timestamptz not null,
    revoked_at timestamptz,
    revoke_reason varchar(40),
    user_agent varchar(255),
    source_address varchar(64)
);

create index user_session_active_account_idx
    on user_session(account_id, absolute_expires_at) where revoked_at is null;
create index member_invite_active_idx
    on member_invite(household_id, expires_at) where accepted_at is null and revoked_at is null;

create table audit_log (
    id uuid primary key,
    occurred_at timestamptz not null,
    event_type varchar(80) not null,
    outcome varchar(16) not null,
    actor_account_id uuid,
    subject_type varchar(40) not null,
    subject_id uuid,
    request_id varchar(80),
    source varchar(40) not null,
    details jsonb not null default '{}'::jsonb
);

create index audit_log_occurred_at_idx on audit_log(occurred_at desc);
create index audit_log_subject_idx on audit_log(subject_type, subject_id, occurred_at desc);
```

- [ ] **Step 3：增加集中配置并验证迁移**

在 `application.yml` 增加 `stocket.identity`：Cookie 名 `STOCKET_SESSION`、`secure=true`，闲置期 `30d`，绝对期 `90d`，邀请默认期 `24h`，会话触碰间隔 `5m`，登录限流窗口 `15m`、最大失败 `10`。`application-local.yml` 只为本机 HTTP 开发覆盖 `cookie.secure=false`，生产与 Compose 不覆盖安全默认值。运行：

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest test`

Expected: PASS。

- [ ] **Step 4：提交迁移边界**

```bash
git add backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml backend/src/main/resources/db/migration/V2__identity_and_audit.sql backend/src/test/java/com/stocket/DatabaseMigrationTest.java
git diff --cached --check
git commit -m "feat(identity): add identity database schema"
```

## Task 2：领域模型、安全原语与一次性初始化

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/stocket/identity/IdentityRole.java`
- Create: `backend/src/main/java/com/stocket/identity/IdentityAuditEvent.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/config/IdentityProperties.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/domain/{Household,UserAccount,HouseholdMember,MemberInvite,UserSession,AccountStatus}.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/persistence/HouseholdRepository.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/persistence/UserAccountRepository.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/persistence/HouseholdMemberRepository.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/persistence/MemberInviteRepository.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/persistence/UserSessionRepository.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/authentication/{PasswordPolicy,SecureValueGenerator,TokenHasher}.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/authentication/{SessionService,SessionCookieService}.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/security/{SecurityConfiguration,PasswordEncoderConfiguration}.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/setup/SetupService.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/{SetupController,SetupStatusResponse,InitializeRequest,CurrentAccountResponse}.java`
- Create: `backend/src/test/java/com/stocket/identity/SetupIntegrationTest.java`

- [ ] **Step 1：加入安全依赖**

在 `backend/pom.xml` 添加 `spring-boot-starter-security` 和测试范围的 `spring-security-test`。同一步创建 `PasswordEncoderConfiguration`，Bean 明确使用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`；单元测试断言编码结果不等于明文、两次编码不同且均可匹配。

- [ ] **Step 2：写初始化集成测试**

用 PostgreSQL Testcontainer 和 `@SpringBootTest` 覆盖：空库状态为可初始化；初始化创建三条关联记录；用户名以 `trim + Locale.ROOT lowercase` 判重；第二次和并发初始化返回 `409 SETUP_ALREADY_COMPLETED`。核心请求：

```java
mockMvc.perform(post("/api/v1/setup/initialize")
        .with(csrf())
        .contentType(APPLICATION_JSON)
        .content("""
                {"householdName":"王家","timezone":"Asia/Shanghai",
                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                """))
        .andExpect(status().isCreated())
        .andExpect(cookie().exists("STOCKET_SESSION"))
        .andExpect(jsonPath("$.username").value("Owner"))
        .andExpect(jsonPath("$.role").value("ADMIN"));
```

Run: `cd backend && ./mvnw -Dtest=SetupIntegrationTest test`

Expected: FAIL，`SetupController` 不存在或返回 404。

- [ ] **Step 3：实现领域类型和 Repository 契约**

公开角色和审计事件固定为：

```java
public enum IdentityRole { ADMIN, MEMBER, VIEWER }

public record IdentityAuditEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String outcome,
        UUID actorAccountId,
        String subjectType,
        UUID subjectId,
        String requestId,
        String source,
        Map<String, Object> details) {
    public IdentityAuditEvent {
        details = Map.copyOf(details);
    }
}
```

Repository 必须提供 `HouseholdRepository.existsAny()`、`UserAccountRepository.findByNormalizedUsername()`、`HouseholdMemberRepository.findByAccountId()`、`UserSessionRepository.findActiveByTokenHash()` 和带悲观锁的 `MemberInviteRepository.findByTokenHashForUpdate()`。

- [ ] **Step 4：实现密码与令牌原语测试和代码**

`PasswordPolicy` 接受 12–128 个 Unicode 字符并拒绝等于规范化用户名的密码；`SecureValueGenerator` 生成 32 字节 URL-safe 无填充令牌和 20 字符临时密码；`TokenHasher` 返回 64 位小写 SHA-256 十六进制。测试固定输入：

```java
assertThat(tokenHasher.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
assertThat(passwordPolicy.validate("owner", "short"))
        .containsExactly("PASSWORD_TOO_SHORT");
```

- [ ] **Step 5：实现最小安全链与事务性初始化**

`SecurityConfiguration` 在本任务先启用 `csrf.spa()`，允许系统状态、setup 和 CSRF 端点，其他请求要求认证，从而覆盖 Boot 默认安全链。`SessionService.create(accountId, userAgent, sourceAddress, now)` 返回只含一次明文令牌和会话 ID 的 `CreatedSession`，数据库只保存摘要；`SessionCookieService` 集中创建与清除 `STOCKET_SESSION` Cookie。

`SetupTransaction.create(command)` 承担单事务写入并在发布成功事件前 `flush`；外层非事务 `SetupService.initialize(command)` 捕获从事务边界抛出的唯一约束异常并映射 `409 SETUP_ALREADY_COMPLETED`，禁止在已标记 rollback-only 的事务内吞异常。Controller 返回 `201` 并通过 `SessionCookieService` 写安全 Cookie。

Run: `cd backend && ./mvnw -Dtest=SetupIntegrationTest test`

Expected: PASS，包括并发测试恰好一个 `201`、其余 `409`。

- [ ] **Step 6：提交初始化切片**

```bash
git add backend/pom.xml backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/identity
git diff --cached --check
git commit -m "feat(identity): initialize household and administrator"
```

## Task 3：数据库会话认证、CSRF 与统一安全响应

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/security/{IdentityPrincipal,SessionAuthenticationFilter,ProblemAuthenticationEntryPoint,ProblemAccessDeniedHandler,PasswordChangeRequiredFilter}.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/security/SecurityConfiguration.java`
- Modify: `backend/src/main/java/com/stocket/identity/internal/authentication/SessionService.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/CsrfController.java`
- Modify: `backend/src/main/java/com/stocket/system/ApiExceptionHandler.java`
- Create: `backend/src/test/java/com/stocket/identity/SecurityIntegrationTest.java`

- [ ] **Step 1：写安全链失败测试**

测试公共 GET 可访问、受保护 API 无 Cookie 返回 ProblemDetail `401`、伪造 Cookie 返回 `401`、认证但角色不足返回 `403`、无 CSRF 的写请求返回 `403`、有效 Cookie 和 CSRF 可通过。断言响应至少包含 `code` 与 `retryable=false`。

Run: `cd backend && ./mvnw -Dtest=SecurityIntegrationTest test`

Expected: FAIL，当前应用没有目标安全规则。

- [ ] **Step 2：实现会话查找与触碰**

`SessionService.authenticate(rawToken, now)` 只返回未撤销、账户启用、`idleExpiresAt > now`、`absoluteExpiresAt > now` 的 `IdentityPrincipal`。仅当 `lastSeenAt + 5m <= now` 时更新 `lastSeenAt` 与 `idleExpiresAt=min(now+30d, absoluteExpiresAt)`，避免每次请求写库。

- [ ] **Step 3：实现会话过滤器**

`SessionAuthenticationFilter` 继承 `OncePerRequestFilter`，读取 `STOCKET_SESSION`，认证成功时创建：

```java
var authentication = UsernamePasswordAuthenticationToken.authenticated(
        principal,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
SecurityContextHolder.getContext().setAuthentication(authentication);
```

无 Cookie 时继续过滤链；Cookie 无效时清除 Cookie、保持匿名，不把原始令牌写入异常或日志。

- [ ] **Step 4：配置 Spring Security 7**

`SecurityConfiguration` 必须：

```java
http
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .securityContext(context -> context.securityContextRepository(
            new RequestAttributeSecurityContextRepository()))
    .requestCache(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable)
    .formLogin(AbstractHttpConfigurer::disable)
    .csrf(csrf -> csrf.spa())
    .exceptionHandling(errors -> errors
            .authenticationEntryPoint(problemAuthenticationEntryPoint)
            .accessDeniedHandler(problemAccessDeniedHandler))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/v1/system", "/api/v1/setup/status",
                    "/api/v1/auth/csrf", "/api/v1/invites/*/status").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/setup/initialize",
                    "/api/v1/auth/login", "/api/v1/auth/logout",
                    "/api/v1/invites/*/accept").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
    .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class)
    .addFilterAfter(passwordChangeRequiredFilter, SessionAuthenticationFilter.class);
```

`CsrfController` 的 GET 形参接收 `CsrfToken` 并返回 header、parameter、token，使延迟令牌被解析并写 Cookie。

`PasswordChangeRequiredFilter` 对所有已认证请求先执行全局强制改密检查，只放行 `/api/v1/account`、`/api/v1/account/password`、`/api/v1/auth/logout`、`/api/v1/auth/csrf` 和 `/api/v1/system`；因此即使 principal 是 ADMIN，也不能被后续 `/admin/**` 首匹配规则绕过。

- [ ] **Step 5：运行并提交安全底座**

Run: `cd backend && ./mvnw -Dtest=SecurityIntegrationTest,SetupIntegrationTest,SystemApiTest test`

Expected: PASS；现有 `SystemApiTest` 对 POST 校验探针需添加 `.with(csrf())`。

另建重启语义测试：第一次 `SpringApplication` context 创建会话后关闭 context，保留同一个 Testcontainer 数据库；第二次 context 使用原 Cookie 请求 `/api/v1/account` 并断言认证成功。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/main/java/com/stocket/system/ApiExceptionHandler.java backend/src/test/java/com/stocket/identity backend/src/test/java/com/stocket/system/SystemApiTest.java
git diff --cached --check
git commit -m "feat(identity): secure APIs with persistent sessions"
```

## Task 4：登录、退出与有界限流

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/authentication/{AuthenticationService,BoundedRateLimiter,LoginThrottleKey}.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/{AuthenticationController,LoginRequest}.java`
- Create: `backend/src/test/java/com/stocket/identity/AuthenticationIntegrationTest.java`
- Create: `backend/src/test/java/com/stocket/identity/BoundedRateLimiterTest.java`

- [ ] **Step 1：写登录与退出测试**

覆盖正确登录、错误密码、未知用户名同形响应、禁用账户、超过 10 次失败返回 `429`、成功后清除失败桶、退出撤销当前会话并清 Cookie。所有错误都不得回显用户名是否存在。

Run: `cd backend && ./mvnw -Dtest=AuthenticationIntegrationTest,BoundedRateLimiterTest test`

Expected: FAIL，认证端点不存在。

- [ ] **Step 2：实现有界限流器**

实现固定窗口 `BoundedRateLimiter<K>`：构造参数为 `Clock`、窗口、最大尝试数、最大键数；使用 `ConcurrentHashMap`，每次操作清理过期项，容量满时删除最早窗口项。公开契约：

```java
boolean tryAcquire(K key);
void reset(K key);
int trackedKeyCount();
```

测试必须证明第 11 次被拒绝、窗口推进后恢复、键数从不超过配置容量。

- [ ] **Step 3：实现登录服务**

按 `normalizedUsername + sourceAddress` 限流。未知用户仍对固定的伪密码摘要执行一次 `PasswordEncoder.matches`，再统一返回 `INVALID_CREDENTIALS`。成功时创建随机会话，数据库保存摘要，Cookie 保存原值；发布成功或失败审计事件，失败事件不包含原始用户名和密码。Controller 在认证成功后调用 Spring Security 的 `CsrfAuthenticationStrategy` 清除旧 CSRF token，前端随后请求 `/auth/csrf` 获得新 Cookie。

- [ ] **Step 4：实现退出服务**

`POST /api/v1/auth/logout` 允许匿名但始终要求 CSRF；有 principal 时按 `sessionId` 撤销会话并发布 `LoggedOut`，无 principal 时只清 Cookie。Controller 调用 `CsrfLogoutHandler` 清除 CSRF token，因此重复退出保持幂等并返回 `204`。集成测试必须断言登录和退出都会清除旧 `XSRF-TOKEN`，随后 `/auth/csrf` 签发新值。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=AuthenticationIntegrationTest,BoundedRateLimiterTest,SecurityIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/identity
git diff --cached --check
git commit -m "feat(identity): add throttled login and logout"
```

## Task 5：本人资料、密码与会话管理

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/account/AccountService.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/{AccountController,UpdateProfileRequest,ChangePasswordRequest,SessionResponse}.java`
- Create: `backend/src/test/java/com/stocket/identity/AccountIntegrationTest.java`

- [ ] **Step 1：写账户 API 测试**

覆盖读取当前账户、修改显示名称与可选邮箱、旧密码错误、成功改密、改密撤销全部旧会话并建立新当前会话、列出会话、撤销自己的指定会话、拒绝撤销他人会话、撤销除当前外全部会话。

Run: `cd backend && ./mvnw -Dtest=AccountIntegrationTest test`

Expected: FAIL，账户 API 不存在。

- [ ] **Step 2：实现资料和会话查询**

`GET /account` 返回 `id, username, displayName, email, role, mustChangePassword`。会话列表只返回 `id, current, createdAt, lastSeenAt, absoluteExpiresAt, userAgent, sourceAddress`，绝不返回 `tokenHash`。

- [ ] **Step 3：实现密码变更原子操作**

事务顺序固定为：校验旧密码与新密码策略；更新摘要、`credentialsChangedAt`、`mustChangePassword=false`；撤销账户全部会话；创建新会话；发布 `PasswordChanged`。响应设置新 Cookie，因此当前请求之后旧 Cookie 立即无效。

- [ ] **Step 4：强制改密授权测试**

为 `mustChangePassword=true` 账户断言仅允许 `/account`、`/account/password`、`/auth/logout`、`/auth/csrf` 和 `/system`；访问其他受保护路径返回 `403 PASSWORD_CHANGE_REQUIRED`。扩展 Task 3 的 `PasswordChangeRequiredFilter`，并重点断言强制改密的 ADMIN 访问 `/admin/**` 仍被拒绝。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=AccountIntegrationTest,SecurityIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/identity
git diff --cached --check
git commit -m "feat(identity): manage account password and sessions"
```

## Task 6：管理员成员管理与三角色强制

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/member/MemberAdminService.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/{MemberAdminController,CreateMemberRequest,UpdateMemberRequest,MemberResponse,TemporaryPasswordResponse}.java`
- Create: `backend/src/test/java/com/stocket/identity/MemberAdminIntegrationTest.java`
- Create: `backend/src/test/java/com/stocket/identity/RoleAuthorizationIntegrationTest.java`

- [ ] **Step 1：写成员管理失败测试**

覆盖管理员创建成员并只返回一次临时密码、初次登录必须改密、修改角色、禁用成员、管理员重置密码、重置后旧会话失效、普通成员与只读成员全部被拒绝。

- [ ] **Step 2：写最后管理员保护测试**

分别尝试禁用、降级最后管理员并断言 `409 LAST_ADMIN_REQUIRED`；存在第二位有效管理员时允许操作。并发降级两位管理员时至少保留一位有效管理员，服务应在事务中锁定所有有效管理员成员行后判断。

Run: `cd backend && ./mvnw -Dtest=MemberAdminIntegrationTest test`

Expected: FAIL，管理员成员 API 不存在。

- [ ] **Step 3：实现创建、更新和重置**

创建和重置均使用 `SecureValueGenerator.temporaryPassword()`，只在成功响应返回明文一次，数据库保存 `PasswordEncoder` 摘要并设置 `mustChangePassword=true`。重置事务撤销目标账户全部会话并发布 `PasswordResetByAdmin`；按 `actorId + targetId` 使用独立限流器。集成测试明确验证阈值内成功、超限 `429`、窗口后恢复、两个目标键隔离且键数不超过容量。

- [ ] **Step 4：实现并测试角色矩阵**

`RoleAuthorizationIntegrationTest` 使用三个真实数据库账户验证：三者都能 GET 本人；`ADMIN` 与 `MEMBER` 通过代表性普通写权限检查；`VIEWER` 被拒绝普通写；只有 `ADMIN` 能访问 `/admin/**`。测试辅助 Controller 只放在测试配置中，不进入生产代码。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=MemberAdminIntegrationTest,RoleAuthorizationIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/identity
git diff --cached --check
git commit -m "feat(identity): add administrator member management"
```

## Task 7：邀请创建、撤销与一次性接受

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/invite/InviteService.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/web/{InviteAdminController,InviteAcceptanceController,CreateInviteRequest,AcceptInviteRequest,InviteResponse,InviteStatusResponse,InviteLinkResponse}.java`
- Create: `backend/src/test/java/com/stocket/identity/InviteIntegrationTest.java`

- [ ] **Step 1：写邀请生命周期测试**

覆盖默认 24 小时、自定义未来有效期、拒绝过去或超过 30 天有效期、列表不泄漏令牌、撤销、过期、接受、重复接受、并发接受只有一次成功、用户名冲突不消费邀请。

Run: `cd backend && ./mvnw -Dtest=InviteIntegrationTest test`

Expected: FAIL，邀请 API 不存在。

- [ ] **Step 2：实现管理员邀请 API**

创建时生成 32 字节 URL-safe 令牌，只保存 SHA-256；响应仅一次返回 `/invite/{rawToken}`。列表返回 `id, role, expiresAt, acceptedAt, revokedAt, createdAt`。撤销只更新未接受记录，重复撤销幂等返回 `204`。

- [ ] **Step 3：实现公共状态与接受事务**

状态接口只返回 `available, role, expiresAt`。接受服务按 `inviteHash + sourceAddress` 限流，再用 `PESSIMISTIC_WRITE` 锁邀请，验证未撤销/未接受/未过期，创建账户和成员，最后写 `acceptedAt/acceptedBy`；任何账户校验失败都回滚且邀请仍可用。集成测试明确验证阈值内失败仍可重试、超限 `429`、窗口后恢复、不同邀请键隔离且键数不超过容量。

- [ ] **Step 4：验证 CSRF 和敏感信息边界**

测试 GET 状态无需认证；POST 接受缺 CSRF 返回 `403`；日志捕获器和审计事件均不包含原始邀请令牌或提交密码。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=InviteIntegrationTest,SecurityIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/identity
git diff --cached --check
git commit -m "feat(identity): add one-time member invitations"
```

## Task 8：身份审计模块

**Files:**
- Create: `backend/src/main/java/com/stocket/audit/internal/{AuditLog,AuditLogRepository,IdentityAuditListener}.java`
- Create: `backend/src/test/java/com/stocket/audit/IdentityAuditIntegrationTest.java`
- Modify: `backend/src/test/java/com/stocket/ArchitectureTest.java`

- [ ] **Step 1：写审计集成测试**

初始化、成功/失败登录、退出、成员创建/角色/状态、邀请创建/接受/撤销、本人改密、管理员重置、会话撤销各执行一次，然后断言对应 `event_type`、actor、subject、source 和 outcome。序列化 `details` 后断言不含 `password`、`token`、`secret`、临时密码和 Cookie 值。

Run: `cd backend && ./mvnw -Dtest=IdentityAuditIntegrationTest test`

Expected: FAIL，监听器和审计持久化不存在。

- [ ] **Step 2：实现监听与 JSONB 持久化**

`IdentityAuditListener` 使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)` 和 `REQUIRES_NEW` 事务接收 `IdentityAuditEvent`：成功业务事件在外层事务提交后写入；登录失败等无外层事务事件通过 fallback 同步写入。通过 `@JdbcTypeCode(SqlTypes.JSON)` 将不可变 `Map<String,Object> details` 映射为 PostgreSQL JSONB，并增加 JSON 对象读写往返测试。事件 ID 作为 `audit_log.id`，重复投递因主键冲突安全忽略，保证幂等。

- [ ] **Step 3：补齐事件发布点**

逐一对照设计规格第 8 节，确保 12 类事件都有成功路径；登录失败使用不可逆用户名指纹，`actorAccountId=null`，不得保存规范化用户名明文。

- [ ] **Step 4：验证模块边界**

`ArchitectureTest` 断言 `audit` 只依赖 `identity` 的公开包，`identity` 不依赖 `audit`。成功事件只允许从事务内发布；失败登录事件在事务外发布。本地恢复事务提交会触发 AFTER_COMMIT 监听器，并在恢复方法返回前完成独立审计事务。运行：

Run: `cd backend && ./mvnw -Dtest=IdentityAuditIntegrationTest,ArchitectureTest test`

Expected: PASS。

- [ ] **Step 5：提交审计边界**

```bash
git add backend/src/main/java/com/stocket/audit backend/src/main/java/com/stocket/identity backend/src/test/java/com/stocket/audit backend/src/test/java/com/stocket/ArchitectureTest.java
git diff --cached --check
git commit -m "feat(audit): persist identity security events"
```

## Task 9：本地管理员恢复命令

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/internal/maintenance/{AdminRecoveryCommand,MaintenanceConfiguration}.java`
- Create: `backend/src/test/java/com/stocket/identity/maintenance/AdminRecoveryCommandTest.java`
- Modify: `backend/src/main/java/com/stocket/StocketApplication.java`
- Modify: `README.md`

- [ ] **Step 1：写命令测试**

使用 `SpringApplication` 启动真实非 Web context 并捕获 stdout/退出码，覆盖：未提供维护参数时正常启动 Web 应用；只提供 reset 参数时自动使用 `WebApplicationType.NONE`；指定有效管理员时生成临时密码、强制改密、撤销全部会话、写 `LOCAL_MAINTENANCE` 审计并以 0 退出；未知用户、非管理员和数据库失败以非零退出且不修改数据。

Run: `cd backend && ./mvnw -Dtest=AdminRecoveryCommandTest test`

Expected: FAIL，维护命令不存在。

- [ ] **Step 2：实现显式维护模式**

`StocketApplication.main` 在创建 context 前检查 `--stocket.maintenance.reset-admin=<username>`；存在时对 `SpringApplication` 调用 `setWebApplicationType(WebApplicationType.NONE)`，从而不依赖操作者额外参数且绝不绑定 HTTP 端口。维护事务锁账户和成员、确认 `ADMIN`、更新密码摘要、设置强制改密、撤销会话并发布 `PasswordRecoveredLocally`；AFTER_COMMIT 审计监听器返回后才只向 stdout 输出一次临时密码，异常信息不包含摘要或数据库凭证。

- [ ] **Step 3：记录运维命令**

在 README 添加 JVM 和原生命令：

```bash
java -jar backend/target/stocket-backend-0.1.0-SNAPSHOT.jar \
  --stocket.maintenance.reset-admin=owner

./stocket --stocket.maintenance.reset-admin=owner
```

- [ ] **Step 4：验证 AOT 可达性**

Run: `cd backend && ./mvnw -Dtest=AdminRecoveryCommandTest test && ./mvnw -Pnative spring-boot:process-aot`

Expected: 测试 PASS，AOT 处理 BUILD SUCCESS。

- [ ] **Step 5：提交维护命令**

```bash
git add backend/src/main/java/com/stocket/StocketApplication.java backend/src/main/java/com/stocket/identity/internal/maintenance backend/src/test/java/com/stocket/identity/maintenance README.md
git diff --cached --check
git commit -m "feat(identity): add local administrator recovery"
```

## Task 10：前端 HTTP 客户端与身份状态机

**Files:**
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/identity.ts`
- Create: `frontend/src/auth/AuthState.ts`
- Create: `frontend/src/auth/useAuth.ts`
- Create: `frontend/src/auth/useAuth.spec.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/App.spec.ts`

- [ ] **Step 1：写身份启动状态测试**

测试以下确定性状态：setup status 为开放时 `setup-required`；已初始化且 `/account` 返回 401 时 `anonymous`；账户要求改密时 `password-change-required`；其他账户为 `authenticated`。登录后重新获取 CSRF；401 回到匿名；CSRF 403 时刷新并最多重试一次。

Run: `cd frontend && npm test -- src/auth/useAuth.spec.ts src/App.spec.ts`

Expected: FAIL，身份状态模块不存在。

- [ ] **Step 2：实现同源 HTTP 封装**

`http.ts` 公开：

```ts
export interface ApiProblem {
  status: number
  code: string
  detail?: string
  retryable: boolean
  fieldErrors?: Array<{ field: string; message: string }>
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  retryCsrf = true,
): Promise<T>
```

所有请求使用 `credentials: 'same-origin'`。非 GET/HEAD 从 `XSRF-TOKEN` Cookie 写 `X-XSRF-TOKEN`；CSRF 错误调用 `refreshCsrf()` 后只重试一次；其他非 2xx 抛出解析后的 `ApiProblem`。

- [ ] **Step 3：实现身份 API 类型**

`identity.ts` 提供 `getSetupStatus`、`initialize`、`refreshCsrf`、`login`、`logout`、`getCurrentAccount`、`changePassword`，请求/响应字段必须与阶段二设计规格第 6 节一致。

- [ ] **Step 4：实现状态机并替换基础外壳**

联合类型固定为：

```ts
export type AuthState =
  | { kind: 'checking-setup' }
  | { kind: 'setup-required' }
  | { kind: 'anonymous' }
  | { kind: 'password-change-required'; account: CurrentAccount }
  | { kind: 'authenticated'; account: CurrentAccount }
```

`useAuth` 暴露 `state, bootstrap, initialize, login, logout, passwordChanged`；`App.vue` 只按状态渲染对应视图，不自行调用 fetch。

- [ ] **Step 5：验证并提交**

Run: `cd frontend && npm test && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/api frontend/src/auth frontend/src/App.vue frontend/src/App.spec.ts
git diff --cached --check
git commit -m "feat(frontend): add identity state and secure API client"
```

## Task 11：初始化、登录、邀请接受与强制改密界面

**Files:**
- Create: `frontend/src/views/{SetupView,LoginView,InviteAcceptView,PasswordChangeView}.vue`
- Create: `frontend/src/views/{SetupView,LoginView,InviteAcceptView,PasswordChangeView}.spec.ts`
- Modify: `frontend/src/api/identity.ts`
- Modify: `frontend/src/styles/main.css`

- [ ] **Step 1：写四个页面的交互测试**

初始化页验证家庭名、IANA 时区、用户名、显示名称、密码与确认密码；登录页提交用户名密码且统一显示“用户名或密码错误”；邀请接受页从 `/invite/{token}` 只在内存读取 token，展示角色和到期时间，并提交用户名、显示名称、密码；强制改密页验证旧密码、新密码、确认密码并只提供退出。测试不得断言或持久化明文密码及邀请 token。

Run: `cd frontend && npm test -- src/views/SetupView.spec.ts src/views/LoginView.spec.ts src/views/InviteAcceptView.spec.ts src/views/PasswordChangeView.spec.ts`

Expected: FAIL，页面不存在。

- [ ] **Step 2：实现可访问表单**

先在 `identity.ts` 增加 `getInviteStatus(token)` 与 `acceptInvite(token, request)`。每个输入有可见 label，错误使用 `role="alert"`，提交期间禁用按钮；密码字段设置正确 `autocomplete`：初始化/邀请/新密码为 `new-password`，登录用户名 `username`、密码 `current-password`。成功事件只向父级发送已验证 DTO。

- [ ] **Step 3：接入 App 状态**

`SetupView` 成功后进入 authenticated；`LoginView` 根据响应进入强制改密或 authenticated；应用启动时若 `window.location.pathname` 匹配 `/invite/{token}`，优先渲染 `InviteAcceptView`，接受成功后清除内存 token 并进入登录页；`PasswordChangeView` 成功后刷新账户并进入 authenticated。任何敏感字段在成功后立即置空。

- [ ] **Step 4：实现移动优先样式**

复用 Element Plus，保持 320px 最小宽度；表单最大宽度 480px；焦点、错误和禁用状态可辨识；不以颜色作为唯一反馈。

- [ ] **Step 5：验证并提交**

Run: `cd frontend && npm test && npm run typecheck && npm run build`

Expected: PASS。

```bash
git add frontend/src/api/identity.ts frontend/src/views frontend/src/App.vue frontend/src/styles/main.css
git diff --cached --check
git commit -m "feat(frontend): add setup login and password flows"
```

## Task 12：账户、会话、成员与邀请管理界面

**Files:**
- Create: `frontend/src/components/AppShell.vue`
- Create: `frontend/src/views/{AccountView,AdminMembersView,AdminInvitesView}.vue`
- Create: `frontend/src/views/{AccountView,AdminMembersView,AdminInvitesView}.spec.ts`
- Modify: `frontend/src/api/identity.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles/main.css`

- [ ] **Step 1：写权限导航和账户页面测试**

断言三种角色都看到“我的账户”，只有 ADMIN 看到“成员”和“邀请”；账户页可更新资料、改密、显示当前会话、撤销单个或其他全部会话；当前会话有明确标记且不能误撤销。

- [ ] **Step 2：写成员管理测试**

断言管理员可创建成员、复制一次性临时密码、修改角色/状态和重置密码；关闭结果对话框后临时密码从组件状态清除且列表无法再次显示；`LAST_ADMIN_REQUIRED` 显示明确中文提示。

- [ ] **Step 3：写邀请管理测试**

断言管理员可选择角色与有效期、创建并复制一次性链接、查看状态、撤销；关闭结果对话框后原始链接从组件状态清除，列表只显示非敏感元数据。

Run: `cd frontend && npm test -- src/views/AccountView.spec.ts src/views/AdminMembersView.spec.ts src/views/AdminInvitesView.spec.ts`

Expected: FAIL，页面不存在。

- [ ] **Step 4：实现页面与 API**

在 `identity.ts` 增加设计规格中账户会话、成员和邀请全部调用。`AppShell` 使用组件内轻量视图选择，不额外引入路由依赖；ADMIN 才渲染管理导航。对 `401` 调用全局 logout，对 `PASSWORD_CHANGE_REQUIRED` 切换强制改密状态，对其他 `403` 显示服务端稳定原因。

- [ ] **Step 5：验证并提交**

Run: `cd frontend && npm test && npm run typecheck && npm run build`

Expected: PASS，浏览器存储中不存在密码、临时密码或邀请令牌。

```bash
git add frontend/src
git diff --cached --check
git commit -m "feat(frontend): add account member and invite management"
```

## Task 13：全链路验收、原生验证与文档收口

**Files:**
- Create: `backend/src/test/java/com/stocket/identity/IdentityAcceptanceTest.java`
- Create: `scripts/identity-maintenance-smoke.sh`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`

- [ ] **Step 1：写阶段二验收测试**

在一个 PostgreSQL Testcontainer 中串联：初始化管理员并自动登录；管理员创建 MEMBER；创建 VIEWER 邀请并接受；三角色权限矩阵；MEMBER 改密撤销旧会话；管理员重置 VIEWER 后旧会话失效；审计表包含所有关键事件且敏感值扫描为空。

Run: `cd backend && ./mvnw -Dtest=IdentityAcceptanceTest test`

Expected: 若存在遗漏则 FAIL；补齐的代码只能修复阶段二验收缺口，不扩展范围。

- [ ] **Step 2：运行后端与架构全测**

Run: `cd backend && ./mvnw test`

Expected: BUILD SUCCESS，所有 JVM 测试通过，Spring Modulith 无循环或非法内部依赖。

- [ ] **Step 3：运行前端全测与构建**

Run: `cd frontend && npm test && npm run typecheck && npm run build`

Expected: 全部 PASS，Vite 构建成功。

- [ ] **Step 4：运行 AOT、原生测试与 Compose 验证**

Run: `make aot && make native-test && make compose-config`

Expected: 三个目标成功。

创建 `scripts/identity-maintenance-smoke.sh`：启动临时 PostgreSQL；先用 JVM jar 对不存在的管理员执行一次恢复以完成 Flyway 迁移并断言非零退出；通过 `psql` 插入单家庭、一个 ACTIVE ADMIN 和一条活跃会话；分别运行 JVM jar 与 `target/stocket-backend` 原生可执行文件进行恢复。脚本必须断言两次成功退出、每次 stdout 只有一个临时密码、HTTP 端口从未监听、旧会话被撤销且 `PASSWORD_RECOVERED_LOCALLY` 审计存在。最后用 trap 删除临时容器。

Run: `cd backend && ./mvnw -DskipTests package && ./mvnw -Pnative -DskipTests native:compile && cd .. && ./scripts/identity-maintenance-smoke.sh`

Expected: JVM 与 Native Image 两条维护路径均 PASS。

随后运行 `docker compose --env-file .env.example -f deploy/compose.yml up --build -d`，用真实浏览器完成初始化、登录、成员和邀请冒烟，再执行 `docker compose --env-file .env.example -f deploy/compose.yml down`。

- [ ] **Step 5：更新文档并提交收口**

README 记录初始化、Cookie/HTTPS 要求、会话期限、管理员恢复和阶段二验证命令；路线图阶段二增加详细计划链接与完成状态。然后执行：

```bash
git add backend/src/test/java/com/stocket/identity/IdentityAcceptanceTest.java scripts/identity-maintenance-smoke.sh README.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "docs: complete phase two identity delivery"
```

## 最终验收清单

- [ ] 全新数据库只能成功初始化一次，首位管理员自动登录。
- [ ] 会话令牌和邀请令牌只以摘要持久化，密码只以自适应哈希持久化。
- [ ] 会话满足 30 天闲置、90 天绝对期限以及密码变更后的全量撤销。
- [ ] 初始化、登录、邀请接受和全部认证写请求均受 CSRF 保护。
- [ ] 登录、邀请接受和管理员密码重置限流有容量上限且返回 `429`。
- [ ] `ADMIN`、`MEMBER`、`VIEWER` 后端权限矩阵通过，最后管理员不可被移除。
- [ ] 临时密码和邀请链接只展示一次，不进入日志、审计或浏览器持久化存储。
- [ ] 本地恢复不启动 HTTP，撤销会话并留下审计。
- [ ] `identity` 不依赖 `audit` 内部实现，模块验证通过。
- [ ] JVM、前端、AOT、原生测试和 Compose 配置全部通过。
