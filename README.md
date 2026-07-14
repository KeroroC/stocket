# Stocket

Stocket 是面向单个家庭、多位成员的自托管家庭资产与日常物品管理系统。项目采用 Spring Boot 模块化单体后端、Vue 3 前端、PostgreSQL，并支持 GraalVM Native Image 与 Docker Compose 部署。

## 环境要求

- JDK 25
- Node.js 24 与 npm
- Docker Engine 或兼容的容器 daemon，以及 Docker Compose
- GraalVM 25（仅 `native-test` 与本地原生构建需要）

## 验证命令

```bash
make test
make build
make aot
make compose-config
make native-test
make backup-test
make release-test
STOCKET_SMOKE_APP_DOCKERFILE=deploy/app/Dockerfile.jvm make restore-smoke

# PWA 移动、离线与响应式验收
cd frontend && npm run test:e2e
```

也可以分别运行 `make backend-test` 和 `make frontend-test`。Make 的前端目标会检查 `frontend/node_modules/.package-lock.json`；依赖尚未安装或 `package.json`、`package-lock.json` 更新时，会自动运行 `npm ci`，不会在每次执行时重复安装。

`make test` 是完整测试门禁；`make build` 只执行后端打包和前端生产构建，避免在连续验证时重复启动整套 Testcontainers。

原生 AOT 测试集合不包含标注 `@DisabledInAotMode` 的数据库迁移、模块架构、Testcontainers 集成和纯反射形状测试；这些门禁由 JVM 测试覆盖。可在原生环境运行的领域、契约与 MVC 测试仍会进入 Native Image。CI 还会实际构建并启动 Native Compose 栈，通过网关对系统 API 和 readiness 端点执行 HTTP smoke。

## 本地启动后端

先准备宿主机可访问的 PostgreSQL 17。默认配置连接 `localhost:5432`，需要创建 database `stocket`、user `stocket`，并将密码设置为 `stocket-local-dev`。也可以通过 `STOCKET_DB_URL`、`STOCKET_DB_USER` 和 `STOCKET_DB_PASSWORD` 覆盖连接配置。

然后使用 `local` profile 启动后端：

```bash
cd backend
STOCKET_DB_PASSWORD=stocket-local-dev ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

后端默认监听 `http://localhost:8080`。

## 本地管理员恢复

当管理员忘记密码或无法登录时，可以使用本地维护命令重置密码。该命令直接操作数据库，不需要通过 Web 接口。

**JVM 模式：**

```bash
java -jar backend/target/stocket-backend-0.1.0.jar \
  --stocket.maintenance.reset-admin=owner
```

**原生模式：**

```bash
./stocket --stocket.maintenance.reset-admin=owner
```

命令执行后会：
1. 生成一个 20 位临时密码
2. 强制用户在首次登录时修改密码
3. 撤销该用户的所有活跃会话
4. 写入 `PasswordRecoveredLocally` 审计事件

临时密码会打印到标准输出，请妥善保管。

## 本地启动前端

```bash
cd frontend
npm ci
# 首次开发、需要更新依赖锁时也可以使用 npm install
npm run dev
```

Vite 开发服务器会把 `/api` 请求代理到 `http://localhost:8080`。

## PWA 安装与离线边界

生产环境必须通过 HTTPS 提供 Stocket；`localhost` 仅用于本地开发。浏览器需要支持 Service Worker、IndexedDB、MediaDevices 和 Web Crypto。登录后可使用浏览器的“安装应用”或“添加到主屏幕”入口安装 Stocket。

PWA 离线能力刻意限制为：

- 打开带版本的静态应用壳；
- 恢复当前标签页已认证账号的非敏感摘要；
- 编辑并恢复按账号隔离、七天过期的 IndexedDB 入库草稿。

所有 `/api/**` 请求使用 `NetworkOnly`，Cache Storage 不保存会话、CSRF、Problem Detail、附件或通知密钥。消耗、调拨、调整和入库等库存写入在离线时会被阻止；系统不实现 Background Sync、离线写队列或自动冲突合并。扫码只在 HTTPS/localhost 且用户主动点击后申请摄像头，权限拒绝或无摄像头时可使用手工输入。

实体手机的安装、真实摄像头和安全区验收清单见 `docs/operations/pwa-device-verification.md`。

## 附件、CSV 导出与审计

附件根目录由 `STOCKET_ATTACHMENT_DIR` 指定，默认位于系统临时目录下的 `stocket-attachments`，生产环境必须挂载到 Web 根目录外的持久卷。允许上传 JPEG、PNG、WebP 和 PDF，单文件上限 20 MiB；扩展名和客户端 Content-Type 仅作为提示，服务端会校验文件签名、Tika 检测结果、图片像素与 PDF 结尾，并拒绝 SVG、HTML、压缩包、可执行文件、多格式伪装和路径穿越输入。数据库只保存随机 storage key，不保存客户端路径。

物品和库存条目可绑定封面、普通图片、发票和保修文件。每次读取都会重新执行家庭与对象授权；图片以内联方式响应，PDF 作为附件下载，全部带 `nosniff` 和私有禁缓存策略。元数据处于 `STAGED`、`MISSING` 或 `DELETED` 时不会返回半成品；恢复任务会完成可恢复的原子移动、标记缺失内容并清理已删除 blob。

目录和库存 CSV 端点分别为 `/api/v1/exports/catalog.csv` 与 `/api/v1/exports/inventory.csv`。导出复用查询筛选，在只读 `REPEATABLE_READ` 快照中按 1,000 行批次流式生成，最多 100,000 行。文件使用 UTF-8 BOM；以 `= + - @ tab CR` 开头的单元格会添加单引号，避免电子表格公式注入。

所有 HTTP 响应返回 `X-Request-Id`。合法客户端 request ID 会贯穿附件元数据、库存流水、公开审计事实、提醒事件和通知投递；非法或超长值由服务端替换。审计详情只保留事件类型对应的白名单字段，不记录密码、令牌、密钥、Authorization/Cookie、附件正文、完整请求体或通知秘密。管理员可通过 `/api/v1/admin/audit-logs` 进行稳定游标搜索，并通过 `/api/v1/admin/diagnostics` 查看数据库、附件目录、未完成事件、DEAD 投递、OPEN 对账和 MISSING 附件的安全摘要；诊断响应不包含路径、主机名、用户名、密钥或异常堆栈。

## 生产快速启动

正式发布前先阅读 [生产部署](docs/operations/deployment.md) 并准备不入库的 `.env`、PostgreSQL 密码、32 字节 Base64 主密钥和 TLS certificate/private key。生产 Compose 只向宿主公开 HTTPS Gateway：

```bash
cp .env.example .env
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  config --quiet
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  up -d --build postgres app gateway
```

启动后检查公开域名的 `/livez` 与 `/readyz`。数据库、附件和备份必须使用持久卷；一致备份、空环境恢复、升级与回滚分别见 [备份恢复](docs/operations/backup-restore.md) 和 [升级回滚](docs/operations/upgrade.md)。当前仓库保留双架构原生发布工作流，但 Native 打包能力正在等待后续去留决策，不纳入本次收口门禁。正式发布仍需归档版本 tag、镜像 digest、扫描、签名、SBOM/provenance 和 Release URL 等实际证据。

## 阶段一完成

JVM 测试套件、前端测试/构建、Spring AOT 处理、PostgreSQL 迁移测试、GraalVM 原生测试和原生 Docker 冒烟测试均已为工程基础通过。

## 阶段二完成：身份与家庭

系统初始化、服务端会话、CSRF 防护、登录/登出、账户管理、邀请接受、角色授权、密码修改、管理员重置、本地维护恢复和身份审计事件均已实现并通过验收。

### 初始化

首次启动时通过 `/api/v1/setup/initialize` 创建家庭和管理员账户。该操作只能成功一次；首位管理员自动获得会话。

### Cookie 与 HTTPS 要求

会话令牌通过 `STOCKET_SESSION` Cookie 传输，设置 `Secure`、`HttpOnly` 和 `SameSite=Lax`。生产环境必须启用 HTTPS，否则 Cookie 无法发送。

### 会话期限

- 空闲超时：30 天
- 绝对超时：90 天
- 密码变更后全量撤销所有会话

### 管理员恢复

当管理员忘记密码时，可使用本地维护命令：

```bash
# JVM 模式
java -jar backend/target/stocket-backend-0.1.0.jar \
  --stocket.maintenance.reset-admin=<用户名>

# 原生模式
./backend/target/stocket-backend \
  --stocket.maintenance.reset-admin=<用户名>
```

命令会生成临时密码、撤销所有会话、写入审计事件。临时密码仅输出到标准输出。

### 角色权限矩阵

| 操作 | ADMIN | MEMBER | VIEWER |
|------|-------|--------|--------|
| 读取数据 | Yes | Yes | Yes |
| 写入数据 | Yes | Yes | No |
| 管理成员/邀请 | Yes | No | No |

### 验证命令

```bash
# 阶段二验收测试
cd backend && ./mvnw -Dtest=IdentityAcceptanceTest test

# 全量后端测试
cd backend && ./mvnw test

# 前端测试与构建
cd frontend && npm test && npm run typecheck && npm run build

# 维护命令冒烟测试
cd backend && ./mvnw -DskipTests package
./scripts/identity-maintenance-smoke.sh
```

## 阶段三完成：目录与位置

系统已实现家庭内的分类树、位置树、分类属性模式、可复用物品定义、标签、条码、位置二维码、归档规则与目录搜索投影。

### API 与权限

- `ADMIN`：维护分类树和位置树，也可维护物品。
- `MEMBER`：读取分类与位置，创建、更新、归档和恢复物品。
- `VIEWER`：只读访问分类、位置、物品与搜索。
- 所有资源均按当前家庭隔离；未知或其他家庭资源统一按不存在处理。

分类属性支持 `TEXT`、`NUMBER`、`BOOLEAN`、`DATE` 和 `ENUM`。分类模式更新会校验已有活跃物品，避免把现有数据变成无效状态。分类、位置和物品更新均使用乐观版本；父节点归档、恢复和循环规则由服务端强制执行。

### 搜索与二维码

- `GET /api/v1/catalog/search?q=...` 会规范化首尾空白和大小写。
- 完整条码精确命中优先于名称、品牌、规格、标签和分类路径的文本结果。
- 归档物品默认不进入搜索结果，目录写事务会同步更新搜索投影。
- 位置二维码载荷使用 `stocket:location:<public-code>`；公开码不可替代认证，解析结果仍受家庭隔离约束。

### 阶段三验收记录

2026-07-14 在 GraalVM 25.0.1、Docker 29.4.0 和 PostgreSQL 17.5 Testcontainers 环境完成：

```bash
cd backend && ./mvnw -Dtest=CatalogLocationAcceptanceTest test
# 1 个阶段验收测试通过

make test
# 后端 196 个测试、前端 98 个测试、类型检查和配置契约通过

make build
# JVM 可执行 JAR 与前端生产构建通过

make aot
# Spring AOT 处理通过

make native-test
# Native Image 生成成功；32 个原生适用测试通过，0 失败
```

## 阶段四完成：库存台账

系统已实现批次与独立资产库存、入库、消耗、退库、调整、损耗、报废、完整调拨、部分批次拆分、不可变流水、库存查询、幂等重放和完整性对账。

### API 与权限

- `GET /api/v1/inventory/entries`：按物品、位置、类型、资产状态和到期区间查询库存；默认隐藏归档条目。
- `GET /api/v1/inventory/entries/{id}` 与 `/movements`：查看批次/资产详情及倒序流水。
- `GET /api/v1/inventory/availability?itemId=...`：返回总可用量、活跃条目数和最早到期日期。
- `POST /api/v1/inventory/receipts` 及条目下的 `consume`、`return`、`adjust`、`transfer`、`lost`、`retire`：仅 `ADMIN`、`MEMBER` 可执行，且必须携带 `Idempotency-Key`。
- `POST /api/v1/admin/inventory/reconcile`：仅管理员可触发完整性对账。

所有数量在 HTTP JSON 中保持十进制字符串，服务端使用 `numeric(19,4)` 与 `BigDecimal`，不会经过浮点转换。写请求的幂等键绑定账户、操作和规范化请求摘要；相同请求可安全重放，不同请求复用同一键返回冲突。

### 一致性模型

- 库存变更在单个 PostgreSQL 事务内完成行锁、规则校验、快照更新和流水追加。
- 批次库存不会小于零，资产可用量只能为 `0` 或 `1`；部分批次调拨生成可追溯的新条目。
- `inventory_movement` 只追加，不提供更新或删除路径；当前快照必须等于流水数量变化之和。
- 对账发现差异时只创建问题记录，不自动改写库存或历史流水；恢复一致后问题自动关闭。
- `inventory` 模块只通过 `identity :: api`、`catalog :: api`、`location :: api` 访问其他模块。

### 阶段四验收记录

2026-07-14 在 Java 25.0.1、Docker 29.4.0 和 PostgreSQL 17.5 Testcontainers 环境完成：

```bash
cd backend && ./mvnw -Dtest=InventoryLedgerAcceptanceTest test
# 1 个全链路验收测试通过

make test
# 后端 230 个测试、前端 103 个测试、类型检查和配置契约通过

make build
# JVM 可执行 JAR 与前端生产构建通过

make aot
# Spring AOT 处理通过
```

## 阶段五完成：提醒与通知管道

系统已实现家庭时区下的临期、过期和低库存规则，提醒确认与到点打开，Spring Modulith JDBC 持久事件恢复，以及应用内、Web Push、SMTP 和 Webhook 四类独立投递。投递使用去重键、租约领取、`FOR UPDATE SKIP LOCKED`、确定性抖动指数退避和八次失败转 DEAD；外部 I/O 不参与库存事务。

### 主密钥与后台任务

`STOCKET_MASTER_KEY` 必须是 Base64 编码的 32 字节随机值，用于 AES-256-GCM 加密 SMTP 密码、Webhook 密钥、VAPID 私钥和 Push subscription。缺失或格式错误时 readiness 为 DOWN。可用以下命令生成：

```bash
openssl rand -base64 32
```

提醒到点任务和投递 worker 默认关闭，生产部署必须显式启用：

```bash
STOCKET_REMINDER_DUE_JOB_ENABLED=true
STOCKET_NOTIFICATION_WORKER_ENABLED=true
```

可通过 `STOCKET_REMINDER_DUE_JOB_CRON` 和 `STOCKET_NOTIFICATION_WORKER_DELAY` 调整执行频率。默认分别为每分钟和 5 秒。

### 渠道配置与安全边界

- `IN_APP`：无需外部配置。
- `SMTP`：只保存 `host`、`port`、`tlsMode`、`username`、`fromAddress` 白名单字段；密码通过请求的 `secret` 字段传入并加密。
- `WEBHOOK`：只允许 HTTPS 公网地址；保存首次解析的公网 IP 集合，投递前重新解析并拒绝结果漂移；不跟随重定向，响应体最多读取 4 KiB，签名使用 `X-Stocket-Signature: sha256=...`。
- `WEB_PUSH`：配置包含 Base64URL 无填充的 P-256 VAPID 公钥和 `mailto:`/HTTPS subject，VAPID 私钥通过 `secret` 加密保存；前端 `VITE_STOCKET_VAPID_PUBLIC_KEY` 必须使用同一公钥。消息使用 RFC 8291 `aes128gcm` 内容编码和 ES256 VAPID JWT。

渠道配置中的未知字段会被丢弃，API 不回显任何秘密。日志、Problem Detail、失败管理和审计详情只记录标识与错误分类，不记录通知正文、完整 Push endpoint、凭据、签名头或解密值。

### 重试与失败处理

网络错误、HTTP `408`、`429` 和 `5xx` 可重试；其他 `4xx` 永久失败。退避为 `min(24h, 30s * 2^attempt)`，附加 0..20% 确定性抖动，最多尝试 8 次。管理员可在失败投递页查看 DEAD 记录并手工重试；历史错误时间保留用于审计。

### 阶段五验收记录

2026-07-14 在 Java 25.0.1、Docker 29.4.0 和 PostgreSQL 17.5 Testcontainers 环境完成：

```bash
cd backend && ./mvnw -Dtest=ReminderNotificationAcceptanceTest,NotificationRuntimeHintsTest,WebPushMessageEncoderTest test
# 提醒通知全链路、原生 hints、Web Push 加密与 VAPID 验证通过

make test
# 后端 257 个测试、前端 110 个测试、类型检查和配置契约通过

make build
# JVM 可执行 JAR 与前端生产构建通过

make aot
# Spring AOT 处理通过
```

## 阶段六完成：移动优先 PWA 工作流

系统已实现可安装应用壳、移动五栏导航和桌面侧栏、任务首页、全局搜索、分类/位置浏览、四步入库向导、商品/位置扫码、库存 bottom sheet 操作、提醒与个人页，以及账号隔离的七天 IndexedDB 草稿。断网刷新可恢复草稿，所有库存写入在重新联网前被阻止。

2026-07-14 自动验收记录：

```bash
make test
# 后端、前端、类型检查和配置契约通过

make build
# JVM 可执行 JAR、前端生产构建、manifest 与 Service Worker 生成通过

make aot
# Spring AOT 处理通过

cd frontend && npm run test:e2e
# Playwright 移动/桌面 5 个场景通过

cd backend && ./mvnw -Dtest=PwaWorkflowAcceptanceTest test
# 条码、位置码、入库、dashboard 与权限边界验收通过
```

实体手机的安装、真实摄像头释放和安全区布局仍需按设备验证文档人工确认。

## 阶段七完成：附件、导出与审计

系统已实现认证附件上传/下载、基于内容的文件验证、封面/发票/保修关联、流式 CSV 导出、审计搜索、管理诊断和 request ID 全链路关联。附件授权、路径与内容检测、CSV 公式注入防护、审计字段白名单和管理端流程均已纳入自动验收。

## 阶段八实现完成：运维与发布加固

系统已实现生产 HTTPS Gateway、secret file 挂载、非 root/只读容器、readiness/liveness、结构化日志、Prometheus 指标、分层限流、一致备份与恢复、升级验证、分层 CI、安全扫描和发布流水线。本次收口不执行 Native Image/nativeTest；现有双架构原生发布能力等待后续去留决策。正式发布仍需由版本 tag workflow 补齐镜像 digest、扫描、签名、SBOM/provenance 和 GitHub Release 证据。

常用运维命令：

```bash
make backup-test
make release-test
STOCKET_SMOKE_APP_DOCKERFILE=deploy/app/Dockerfile.jvm make restore-smoke
```

`make release-smoke` 会额外构建 Native 应用，仅在后续确认继续保留 Native 打包能力时使用，不属于当前收口门禁。

## 文档

- [产品与技术设计规格](docs/superpowers/specs/2026-07-10-stocket-design.md)
- [交付路线图](docs/superpowers/plans/2026-07-11-delivery-roadmap.md)
- [基础与原生构建实施说明](docs/superpowers/plans/2026-07-11-foundation-native-baseline.md)
- [阶段三目录与位置实施计划](docs/superpowers/plans/2026-07-12-catalog-location.md)
- [阶段四库存台账实施计划](docs/superpowers/plans/2026-07-12-inventory-ledger.md)
- [阶段五提醒与通知实施计划](docs/superpowers/plans/2026-07-12-reminder-notification.md)
- [阶段六移动优先 PWA 实施计划](docs/superpowers/plans/2026-07-12-mobile-pwa-workflows.md)
- [阶段七附件、导出与审计实施计划](docs/superpowers/plans/2026-07-12-attachment-export-audit.md)
- [阶段八运维与发布加固实施计划](docs/superpowers/plans/2026-07-12-operations-release-hardening.md)
- [生产部署](docs/operations/deployment.md)
- [备份、保留与恢复](docs/operations/backup-restore.md)
- [可观测性与告警](docs/operations/observability.md)
- [依赖与镜像安全扫描](docs/operations/security-scanning.md)
- [正式发布检查清单](docs/operations/release-checklist.md)
- [正式验收报告模板](docs/operations/acceptance-report-template.md)
- [升级与回滚](docs/operations/upgrade.md)
- [PWA 实体设备验证记录](docs/operations/pwa-device-verification.md)
