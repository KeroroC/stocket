# 阶段二：身份与家庭设计规格

- 日期：2026-07-12
- 状态：已确认，等待文档复核
- 对应路线图：阶段二“身份与家庭”
- 技术基线：Java 25、Spring Boot 4.0.3、Spring Security、PostgreSQL、Vue 3

## 1. 目标与范围

阶段二交付可实际使用的单家庭身份边界，使管理员能够完成一次性初始化、创建或邀请成员，并确保 `ADMIN`、`MEMBER`、`VIEWER` 三种角色在后端获得正确授权。

本阶段包含：

- 一次性家庭初始化和首位管理员创建；
- 用户名密码登录、持久化服务端会话和登出；
- CSRF 防护、认证限流和安全 Cookie；
- 当前账户、密码和会话管理；
- 管理员直接创建成员、修改成员角色和重置密码；
- 一次性邀请创建、查看、撤销和接受；
- 本地维护命令恢复管理员访问；
- 身份领域事件和安全审计记录；
- 初始化、登录、强制改密、账户会话、成员邀请管理前端页面。

本阶段不包含邮箱验证、邮件发送、公开注册、匿名找回密码、多家庭、外部身份提供商和多实例分布式限流。

## 2. 已确认的产品决策

### 2.1 账户标识

- 登录标识为全局唯一用户名；用户名按去除首尾空白并转换为小写后的规范值判重。
- 账户另有显示名称；邮箱是可选资料，不参与登录，也不要求 SMTP。
- 用户名创建后第一版不可修改，显示名称和邮箱可由本人修改。

### 2.2 邀请

- 管理员创建邀请时预先指定角色和有效期，默认有效期为 24 小时。
- 系统只向管理员展示一次包含随机令牌的邀请链接；数据库只保存令牌摘要。
- 受邀者填写用户名、显示名称和密码后完成注册。
- 接受成功后邀请立即失效；过期、撤销或已接受的邀请不能再次使用。

### 2.3 会话

- 会话闲置 30 天过期，创建 90 天后绝对过期，以先到者为准。
- 修改密码、管理员重置密码或本地恢复后，撤销该账户的全部既有会话。
- 用户可以查看自己的活跃会话，并撤销单个会话或除当前会话外的全部会话。

### 2.4 直接创建成员

- 管理员指定用户名、显示名称和角色创建成员。
- 系统生成高强度一次性临时密码，只在成功响应中展示一次。
- 新成员首次登录后必须修改密码；完成修改前不能访问其他受保护业务 API。

### 2.5 本地恢复与限流

- 不提供匿名 Web 密码恢复入口。
- 本地维护命令直接连接数据库，为指定管理员生成一次性临时密码，并撤销其全部会话。
- 登录、邀请接受和管理员密码重置采用单实例进程内有界限流；服务重启后计数清空。
- 多实例部署时再将限流存储替换为数据库或 Redis，不在阶段二提前引入。

## 3. 架构方案

采用 Spring Security 和自定义数据库持久化会话。浏览器 Cookie 保存随机不透明令牌，数据库仅保存令牌的 SHA-256 摘要。该方案比 JWT 更适合主动撤销、会话列表、密码变更后失效和审计，也比直接使用通用 HTTP Session 表更容易表达身份领域字段。

### 3.1 模块职责

- `identity`：家庭、账户、成员、邀请、凭证、会话、认证与授权应用服务。
- `audit`：订阅身份模块发布的审计事件并持久化审计日志。
- `system`：保留现有系统状态接口，不拥有身份数据。

`identity` 不直接访问 `audit` Repository。审计通过 Spring Modulith 应用事件传递；审计写入失败必须可观测，但不能泄漏密码、临时密码、邀请令牌或会话令牌。

### 3.2 认证请求链

1. 自定义安全过滤器从 Cookie 读取不透明会话令牌；
2. 对令牌执行 SHA-256，按摘要查询未撤销会话；
3. 校验闲置过期、绝对过期和账户状态；
4. 加载账户、家庭成员角色与 `mustChangePassword` 状态；
5. 建立 Spring Security 身份，并节流更新 `lastSeenAt`；
6. 未认证返回 `401 application/problem+json`，无权限返回 `403 application/problem+json`。

后端不依赖 Servlet 容器内存会话，重启后数据库中的有效会话继续可用。

## 4. 数据模型

Flyway V2 创建以下表，主键统一使用 UUID，时间统一使用带时区时间戳。

### 4.1 `household`

- `id`、`name`、`timezone`、`created_at`、`updated_at`；
- 固定哨兵唯一列保证系统中最多存在一个家庭；
- 初始化服务在同一事务中创建家庭、首位管理员账户和成员关系。

### 4.2 `user_account`

- `id`、`username`、`normalized_username`、`display_name`、`email`；
- `password_hash`、`status`、`must_change_password`；
- `credentials_changed_at`、`created_at`、`updated_at`、`version`；
- `normalized_username` 全局唯一；
- 状态第一版为 `ACTIVE`、`DISABLED`。

### 4.3 `household_member`

- `id`、`household_id`、`account_id`、`role`、`created_at`、`updated_at`；
- 每个家庭和账户组合唯一；
- 角色为 `ADMIN`、`MEMBER`、`VIEWER`；
- 禁止禁用、降级或删除最后一位有效管理员。

### 4.4 `member_invite`

- `id`、`household_id`、`token_hash`、`role`；
- `expires_at`、`accepted_at`、`revoked_at`；
- `created_by`、`accepted_by`、`created_at`；
- `token_hash` 唯一；接受时锁定记录并在同一事务中创建账户、成员关系及标记接受。

### 4.5 `user_session`

- `id`、`account_id`、`token_hash`；
- `created_at`、`last_seen_at`、`idle_expires_at`、`absolute_expires_at`；
- `revoked_at`、`revoke_reason`；
- 可选的 `user_agent` 和经最小化处理的来源地址，用于用户识别会话；
- `token_hash` 唯一，并为账户活跃会话查询和过期清理建立索引。

### 4.6 `audit_log`

- `id`、`occurred_at`、`event_type`、`outcome`；
- `actor_account_id`、`subject_type`、`subject_id`；
- `request_id`、`source`、`details` JSONB；
- 审计详情只保存必要元数据和变更前后非敏感值。

## 5. 安全设计

### 5.1 密码与随机令牌

- 密码使用 Spring Security 推荐的自适应单向密码编码器；编码参数通过配置集中管理。
- 初始化、邀请接受和修改密码执行统一密码策略校验。
- 会话、邀请和临时密码均使用密码学安全随机源生成。
- 明文密码、临时密码、邀请令牌和会话令牌不得写入数据库、日志、审计或错误响应。
- 临时密码只在创建或重置成功响应中返回一次，并设置 `mustChangePassword=true`。

### 5.2 Cookie 与 CSRF

- 会话 Cookie 使用 `HttpOnly`、`Secure`、`SameSite=Lax`、`Path=/`，不设置持久化明文身份信息。
- CSRF 采用可由前端读取的 CSRF Cookie 与请求 Header 配对；所有非安全方法必须校验。
- 登录、初始化和邀请接受同样要求 CSRF，避免登录 CSRF 和跨站初始化。
- 前端启动时先获取 CSRF 令牌，再提交写请求；退出登录后服务端撤销当前会话并清除会话 Cookie。
- 生产环境只允许同源访问；开发代理保持同源语义，不使用宽泛 CORS。

### 5.3 限流

- 登录按规范化用户名与来源地址组合限流；错误响应不暴露账户是否存在。
- 邀请接受按邀请摘要与来源地址组合限流。
- 管理员密码重置按管理员账户和目标账户组合限流。
- 限流器必须有容量上限和过期清理，超过阈值返回 `429` 和可重试提示。

### 5.4 强制改密边界

带 `mustChangePassword=true` 的已认证账户只允许访问：当前账户信息、修改密码、退出登录、CSRF 和必要的系统状态接口。其他受保护 API 一律返回 `403` 和稳定错误码 `PASSWORD_CHANGE_REQUIRED`。

## 6. API 设计

所有接口位于 `/api/v1`，错误使用 `application/problem+json`。写请求均要求 CSRF；需要认证的接口同时要求有效会话。

### 6.1 公共与认证接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/setup/status` | 返回是否允许初始化，不返回账户信息 |
| `POST` | `/setup/initialize` | 创建家庭和首位管理员，成功后建立会话 |
| `GET` | `/auth/csrf` | 获取或刷新 CSRF 令牌 |
| `POST` | `/auth/login` | 用户名密码登录并建立会话 |
| `POST` | `/auth/logout` | 撤销当前会话并清除 Cookie |
| `GET` | `/account` | 获取当前账户、角色和强制改密状态 |
| `PATCH` | `/account/profile` | 修改本人显示名称和可选邮箱 |
| `POST` | `/account/password` | 修改本人密码并撤销全部既有会话，再建立当前新会话 |
| `GET` | `/account/sessions` | 查看本人活跃会话 |
| `DELETE` | `/account/sessions/{sessionId}` | 撤销本人指定会话 |
| `DELETE` | `/account/sessions/others` | 撤销除当前会话外的全部会话 |

初始化接口只在不存在家庭时可成功。数据库唯一约束和事务是最终并发保护；重复初始化统一返回 `409 SETUP_ALREADY_COMPLETED`。

### 6.2 邀请接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/invites/{token}/status` | 公共 | 返回邀请是否可用、角色和到期时间，不返回创建人资料 |
| `POST` | `/invites/{token}/accept` | 公共 | 创建账户并接受邀请 |
| `GET` | `/admin/invites` | `ADMIN` | 分页查看邀请 |
| `POST` | `/admin/invites` | `ADMIN` | 创建邀请并仅一次返回链接 |
| `DELETE` | `/admin/invites/{inviteId}` | `ADMIN` | 撤销未使用邀请 |

### 6.3 成员管理接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/admin/members` | `ADMIN` | 分页查看成员 |
| `POST` | `/admin/members` | `ADMIN` | 创建成员并仅一次返回临时密码 |
| `PATCH` | `/admin/members/{memberId}` | `ADMIN` | 修改角色、显示名称或启用状态 |
| `POST` | `/admin/members/{memberId}/password-reset` | `ADMIN` | 生成临时密码并撤销目标账户全部会话 |

所有管理员接口必须在应用服务层再次校验角色和最后管理员规则，不能只依赖前端或 URL 规则。

## 7. 权限矩阵

| 能力 | `ADMIN` | `MEMBER` | `VIEWER` |
| --- | --- | --- | --- |
| 查看本人资料和会话 | 允许 | 允许 | 允许 |
| 修改本人资料和密码 | 允许 | 允许 | 允许 |
| 查看后续业务只读 API | 允许 | 允许 | 允许 |
| 执行库存和目录业务写入 | 允许 | 允许 | 拒绝 |
| 管理成员、角色和邀请 | 允许 | 拒绝 | 拒绝 |
| 执行管理员密码重置 | 允许 | 拒绝 | 拒绝 |

阶段二提供代表性受保护测试端点或在安全测试中构造请求，证明三种角色的读取、普通写入和管理写入边界。后续模块仍需在各自接口和应用服务上声明具体权限。

## 8. 审计事件

`identity` 发布不可包含敏感值的事件，`audit` 订阅并持久化：

- `HouseholdInitialized`；
- `LoginSucceeded`、`LoginFailed`、`LoggedOut`；
- `MemberCreated`、`MemberRoleChanged`、`MemberStatusChanged`；
- `InviteCreated`、`InviteAccepted`、`InviteRevoked`；
- `PasswordChanged`、`PasswordResetByAdmin`、`PasswordRecoveredLocally`；
- `SessionRevoked`。

登录失败事件不保存提交的密码；未知用户名使用空主体并保存规范化后的不可逆标识或最小必要信息，避免审计库成为账号探测数据源。

## 9. 本地维护命令

应用提供独立维护模式，例如 `--stocket.maintenance.reset-admin=<username>`：

1. 只在显式维护模式启动，不启动 HTTP 服务；
2. 连接同一 PostgreSQL，查找有效管理员；
3. 生成一次性临时密码并更新密码摘要；
4. 设置强制改密，撤销该账户全部会话；
5. 写入来源为 `LOCAL_MAINTENANCE` 的审计记录；
6. 只向本地终端打印一次临时密码后退出。

用户名不存在、目标不是管理员或数据库不可用时以非零状态退出，且不修改数据。该命令必须通过 JVM 冒烟验证。

## 10. 前端体验

前端使用明确的身份状态机：`checking-setup`、`setup-required`、`anonymous`、`password-change-required`、`authenticated`。

- 未初始化时展示家庭与首位管理员初始化向导；
- 已初始化且未登录时展示登录页；
- 强制改密时只展示修改密码和退出入口；
- “我的”页面提供资料修改、密码修改和会话撤销；
- 管理员页面提供成员、邀请、角色、禁用和密码重置操作；
- 普通成员和只读成员不展示管理入口；
- `401` 使客户端回到登录态，`403` 展示权限原因，CSRF 失效时刷新令牌后只安全重试一次。

临时密码和邀请链接使用一次性结果页，离开后不可从列表重新查看。浏览器不得把这些值写入持久化存储。

## 11. 测试与验收

实现采用测试驱动开发，至少覆盖：

- 并发初始化只有一个请求成功；
- 用户名规范化唯一性和密码策略；
- 正确/错误登录、统一失败响应和限流；
- 会话跨应用重启语义、闲置过期、绝对过期、主动撤销；
- CSRF 对初始化、登录、邀请接受及认证写请求生效；
- 邀请接受的成功、过期、撤销、重复和并发场景；
- 直接创建成员、首次强制改密和密码重置后的会话失效；
- 最后一位管理员保护；
- 三种角色的 API 权限矩阵；
- 审计事件不包含敏感字段；
- 本地维护命令成功与失败退出路径；
- Vue 身份状态跳转、表单校验、权限导航和敏感结果一次性展示；
- Spring Modulith 模块边界、JVM 测试、前端测试和容器冒烟。

阶段二验收切片：在全新数据库中，管理员完成初始化并自动登录；管理员直接创建一名成员并创建一条邀请；受邀者接受邀请；三种角色的读取、普通业务写入和管理写入权限均符合矩阵；密码修改、管理员重置和本地恢复均撤销旧会话并留下不含敏感值的审计记录。

## 12. 实施边界

- 详细实施计划必须按可独立验证的小任务拆分，并明确测试先行、文件范围、命令和提交边界。
- 使用独立 Git worktree 和 `codex/` 前缀功能分支。
- 计划执行采用 subagent-driven development：每项任务由新的实现子代理处理，随后依次进行规格符合性审查和代码质量审查。
- 引入 Spring Security 或使用 Spring Boot 4 API 前，必须通过 Context7 获取当前版本文档。
- 不提前实现阶段三目录与位置业务，也不为未来多实例部署引入 Redis。
