# 阶段八：运维与发布加固实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把完整 Stocket v1 加固为可安全自托管、可观测、可备份恢复并可在 Linux AMD64/ARM64 上重复构建和验证的正式发布版本。

**Architecture:** Gateway 终止 HTTPS、提供静态 PWA 并只代理 `/api`；Native Spring Boot 应用、PostgreSQL、附件和备份使用最小权限、独立持久卷与健康门禁。CI 将快速验证与发布验证分层，发布流程生成双架构镜像、SBOM、漏洞报告、版本清单和 SHA-256 校验和；恢复演练在全新临时环境中验证数据库、附件、迁移和库存一致性。

**Tech Stack:** Java 25、Spring Boot 4.0.3 Actuator/Micrometer、GraalVM Native Image、PostgreSQL 17、Docker Buildx、Docker Compose、Nginx、GitHub Actions、Trivy、Syft、Cosign、pg_dump/pg_restore、OpenTelemetry/Micrometer Observation

---

## 执行约束

- 前置条件：阶段一至七全部验收通过。本阶段不新增业务实体、用户流程、通知渠道或远程对象存储。
- 生产仅通过 Gateway 暴露 443；app 和 PostgreSQL 不发布宿主端口。HTTP 80 只做 HTTPS 重定向。TLS 证书和私钥通过只读挂载或部署平台 secret 提供，不进入镜像、仓库、备份或日志。
- 所有容器以非 root、只读根文件系统、drop all capabilities 运行；仅数据/临时目录显式可写。镜像固定 digest 或明确版本，不使用 `latest`。
- 主密钥、数据库密码、SMTP/Webhook/VAPID 凭据来自环境或 secret file；`.env.example` 只含占位符。启动时缺少关键密钥必须 readiness DOWN 或直接失败。
- 备份必须同时覆盖 PostgreSQL、自包含附件目录和不含秘密的配置摘要。默认保留 7 个每日和 4 个每周；删除前先校验新备份完整。
- 不把“备份命令成功”当作恢复完成。发布候选必须在干净环境实际恢复，并运行迁移版本、附件哈希、库存对账和核心 API 冒烟。
- 公网速率限制在 Gateway 粗粒度执行，身份/上传/渠道测试等敏感端点在应用内按账号/IP细粒度执行；不得因代理配置错误把所有用户识别成同一 IP。
- 日志为结构化 JSON，包含 timestamp、level、service、version、requestId、accountId、operation、outcome；禁止秘密、Cookie、请求/响应正文和附件路径。
- 发布构建只从干净 Git commit 产生；版本、commit SHA、架构、镜像 digest 和源码状态写入 manifest。不得把本机生成的二进制冒充跨架构产物。

## 文件结构

```text
deploy/
├── compose.yml
├── compose.production.yml
├── gateway/{default.conf,security-headers.conf,tls.conf}
├── app/Dockerfile
├── backup/{backup.sh,restore.sh,retention.sh,verify.sh}
└── smoke/{api-smoke.sh,restore-smoke.sh}

backend/src/main/java/com/stocket/system/operations/
├── ReadinessConfiguration.java
├── StorageHealthIndicator.java
├── MasterKeyHealthIndicator.java
├── RequestMetrics.java
└── RateLimitFilter.java

.github/workflows/
├── ci.yml
├── nightly.yml
└── release.yml

docs/operations/
├── deployment.md
├── backup-restore.md
├── observability.md
├── upgrade.md
└── release-checklist.md
```

## Task 1：加固生产 Compose、容器和 HTTPS Gateway

**Files:**
- Modify: `deploy/compose.yml`
- Create: `deploy/compose.production.yml`
- Modify: `deploy/app/Dockerfile`
- Modify: `deploy/frontend/Dockerfile`
- Modify: `deploy/gateway/default.conf`
- Create: `deploy/gateway/{security-headers.conf,tls.conf}`
- Modify: `.env.example`
- Create: `deploy/smoke/gateway-smoke.sh`

- [ ] **Step 1：写配置静态失败检查**

新增脚本断言生产 compose 仅 gateway 发布端口；app/db 无 `ports`；服务均有 `read_only:true`、`cap_drop: [ALL]`、`no-new-privileges:true`、非 root user、healthcheck、资源限制和日志轮转；TLS 私钥不来自 build context。

Run: `bash deploy/smoke/gateway-smoke.sh --static deploy/compose.production.yml`

Expected: FAIL，现有部署尚未满足全部断言。

- [ ] **Step 2：实现最小权限容器**

Native app 使用两阶段构建，运行镜像只包含可执行文件、CA 和非 root 用户；根文件系统只读，`/tmp` 使用 size-limited tmpfs。前端构建产物进入 Nginx 非 root 镜像。PostgreSQL 数据、附件和备份使用三个命名卷，app 只挂载附件卷，backup job 才同时挂载数据库连接、附件和备份卷。

- [ ] **Step 3：配置 HTTPS 和安全头**

Gateway 配置 TLS 1.2/1.3、HSTS（确认 HTTPS 可用后启用）、`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy` 和与 PWA/Push 兼容的 CSP。`/api` 关闭响应缓存并透传 `X-Request-Id`；静态 hash 资源长期 immutable，`index.html` no-cache；上传 body 限制 20 MiB 加开销。只信任 compose 内 gateway 的 `X-Forwarded-*`。

- [ ] **Step 4：运行容器冒烟并提交**

Run: `docker compose -f deploy/compose.yml -f deploy/compose.production.yml config --quiet`

Expected: 退出码 0。

Run: `bash deploy/smoke/gateway-smoke.sh --runtime`

Expected: HTTP 重定向 HTTPS，HTTPS 首页、`/api/v1/system/status`、安全头和未暴露端口均通过。

```bash
git add deploy .env.example
git diff --cached --check
git commit -m "chore(deploy): 加固 HTTPS 生产部署"
```

## Task 2：实现就绪门禁、结构化日志和业务指标

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/stocket/system/operations/{StorageHealthIndicator,MasterKeyHealthIndicator,RequestMetrics,OperationsConfiguration}.java`
- Create: `backend/src/test/java/com/stocket/system/operations/{ReadinessIntegrationTest,StructuredLoggingTest,RequestMetricsTest}.java`
- Modify: `deploy/gateway/default.conf`

- [ ] **Step 1：写就绪和日志失败测试**

覆盖数据库不可用、Flyway 失败、附件根目录不可写、主密钥缺失时 readiness DOWN；liveness 不因外部 SMTP/Webhook 失败而 DOWN。日志测试捕获登录/入库/通知失败，断言 JSON 字段完整且不含 Authorization/Cookie/password/secret/body/storage path。

- [ ] **Step 2：配置 Actuator 探针**

启用 Spring Boot 4.0.3 的 `management.endpoint.health.probes.add-additional-paths=true`，主端口提供 `/livez` 和 `/readyz`。只暴露 health/info/prometheus，health 详情仅管理员鉴权 API 可见；Gateway 对公网只开放聚合状态，不开放 env、beans、configprops、heapdump。

- [ ] **Step 3：实现指标和结构化日志**

增加请求延迟/状态、库存命令结果、提醒重算、投递结果/重试、未完成模块事件、对账问题和附件缺失 gauge。标签只用低基数字段（operation/outcome/channel），禁止 accountId/itemId/requestId 作为 metric tag。日志通过 MDC/Observation 关联 requestId，异步任务显式恢复事件上下文。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ReadinessIntegrationTest,StructuredLoggingTest,RequestMetricsTest test`

Expected: PASS。

```bash
git add backend/pom.xml backend/src backend/src/main/resources/application.yml deploy/gateway/default.conf
git diff --cached --check
git commit -m "feat(ops): 添加就绪检查日志与指标"
```

## Task 3：实现分层速率限制和代理地址校验

**Files:**
- Create: `backend/src/main/java/com/stocket/system/operations/ratelimit/{RateLimitPolicy,RateLimiter,RateLimitFilter,ClientAddressResolver}.java`
- Create: `backend/src/test/java/com/stocket/system/operations/ratelimit/{RateLimiterTest,ClientAddressResolverTest,RateLimitIntegrationTest}.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `deploy/gateway/default.conf`

- [ ] **Step 1：写限流失败测试**

覆盖登录、setup、邀请接受、密码重置、上传、通知渠道测试和通用 API 各自策略；账号 + 可信客户端 IP 组合；伪造 `X-Forwarded-For` 不绕过；窗口重置；响应 `429` 包含 `Retry-After` 和稳定 code；并发令牌消费不超额。

- [ ] **Step 2：实现本机单实例令牌桶**

第一版单实例部署使用有界内存 token bucket，key 只保存 HMAC 摘要并按闲置过期清理。默认策略：登录 `10/15m`、setup `5/h`、邀请接受 `20/h`、密码重置 `5/h`、上传 `30/min`、渠道测试 `5/15m`、通用 API `300/min`。配置可调但有安全下限。

- [ ] **Step 3：校验可信代理链**

只在直接 peer 属于配置的 compose gateway 网段时读取单个 `X-Forwarded-For`，否则用 socket 地址。Gateway 先覆盖而非追加传入头，再把自身识别信息传给 app。Nginx 增加粗粒度连接/请求限制，应用规则负责业务错误。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=RateLimiterTest,ClientAddressResolverTest,RateLimitIntegrationTest test`

Expected: PASS。

```bash
git add backend/src backend/src/main/resources/application.yml deploy/gateway/default.conf
git diff --cached --check
git commit -m "feat(security): 添加分层访问限流"
```

## Task 4：实现一致备份、保留和恢复脚本

**Files:**
- Create: `deploy/backup/{backup.sh,restore.sh,retention.sh,verify.sh,lib.sh}`
- Create: `deploy/backup/tests/{backup.bats,restore.bats,retention.bats}`
- Modify: `deploy/compose.production.yml`
- Create: `docs/operations/backup-restore.md`

- [ ] **Step 1：写 shell 行为失败测试**

用临时目录和 stub `pg_dump/pg_restore` 覆盖：命令失败不发布备份、manifest/checksum 缺失拒绝恢复、路径含空格、附件复制失败、保留 7 daily + 4 weekly、最新成功备份永不被错误删除、并发备份由 flock 拒绝。

Run: `bats deploy/backup/tests`

Expected: FAIL，脚本不存在。

- [ ] **Step 2：实现不可变备份目录**

每次备份先写 `<timestamp>.partial/`，包含 `database.dump`（custom format）、`attachments.tar`、`config-summary.json`、`manifest.json` 和 `SHA256SUMS`；全部成功并 fsync 后原子重命名为 `<timestamp>/`，最后更新 `latest` symlink。配置摘要只列版本、非秘密开关和卷 schema，不复制 `.env`。

- [ ] **Step 3：实现受控恢复**

restore 默认要求目标数据库为空和附件目录为空；校验 checksum 后先 `pg_restore --clean` 到临时数据库或新数据库，再恢复附件到 staging，校验 manifest 数量/哈希，最后切换。提供 `--force` 时仍先创建恢复前安全备份，禁止直接覆盖当前数据而无回滚点。

- [ ] **Step 4：实现定时与保留**

Compose 增加独立 backup service，不赋予 Docker socket；调度可由宿主 cron/systemd timer 调用 `docker compose run --rm backup`。retention 只删除校验通过且不在保留集合的完整目录，任何 `.partial` 超过 24h 才清理。

- [ ] **Step 5：验证并提交**

Run: `bats deploy/backup/tests`

Expected: PASS。

```bash
git add deploy/backup deploy/compose.production.yml docs/operations/backup-restore.md
git diff --cached --check
git commit -m "feat(ops): 实现备份保留与安全恢复"
```

## Task 5：建立干净环境恢复验证和升级流程

**Files:**
- Create: `deploy/smoke/{restore-smoke.sh,api-smoke.sh,inventory-smoke.sh}`
- Create: `docs/operations/{deployment,upgrade}.md`
- Create: `backend/src/test/java/com/stocket/system/UpgradeCompatibilityTest.java`
- Modify: `Makefile`

- [ ] **Step 1：写恢复验收脚本**

脚本创建独立 compose project 和临时卷，载入 fixture 备份，启动候选版本，等待 `/readyz`，检查 Flyway 版本、家庭/成员/物品/库存/提醒计数、附件 SHA-256、库存对账零问题，并通过 API 执行登录、搜索、入库、消耗、调拨、提醒和下载。

- [ ] **Step 2：实现升级兼容测试**

`UpgradeCompatibilityTest` 从上一正式版本 schema fixture 启动当前应用，断言 Flyway 只前进、不修改历史 checksum、核心数据可读、幂等和事件发布记录仍可恢复。破坏性迁移必须使用 expand/migrate/contract 并跨两个版本，不在 v1 首发中模拟无依据的降级。

- [ ] **Step 3：增加 Make 入口并验证**

新增 `make backup-test`、`make restore-smoke`、`make release-smoke`。所有目标可在非交互 CI 中运行，并在失败时保留临时日志路径但不输出 secret。

Run: `make restore-smoke`

Expected: 干净临时环境恢复和核心 API 冒烟 PASS，随后清理 project。

- [ ] **Step 4：提交恢复和升级边界**

```bash
git add deploy/smoke docs/operations backend/src/test/java/com/stocket/system/UpgradeCompatibilityTest.java Makefile
git diff --cached --check
git commit -m "test(ops): 验证恢复与版本升级"
```

## Task 6：分层 CI、依赖与镜像安全门禁

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/nightly.yml`
- Create: `.github/dependabot.yml`
- Create: `.trivyignore`
- Create: `docs/operations/security-scanning.md`

- [ ] **Step 1：拆分 PR 快速门禁**

PR workflow 并行执行 backend JVM tests、module verify、frontend tests/typecheck/build、migration test、AOT、Compose config 和 shell tests；使用 lockfile/Maven cache，权限默认 `contents: read`，不向 fork PR 注入 secrets。

- [ ] **Step 2：建立夜间深度验证**

nightly 执行 nativeTest、原生镜像构建、Playwright、恢复演练、Trivy filesystem/image 扫描和 SBOM 生成。失败上传脱敏测试报告、日志和扫描 JSON，保留 14 天；不上传数据库、附件 fixture 中的真实数据或环境文件。

- [ ] **Step 3：定义漏洞门禁**

发布阻止有修复版本的 CRITICAL/HIGH 漏洞。`.trivyignore` 每项必须包含 CVE、影响判断、补偿措施、负责人和不超过 30 天的到期日；过期豁免使 CI 失败。Dependabot 分 Maven/npm/GitHub Actions 每周更新，单独 PR，不自动合并主版本升级。

- [ ] **Step 4：验证 workflow 并提交**

Run: `make test && make build && make aot && docker compose -f deploy/compose.yml -f deploy/compose.production.yml config --quiet`

Expected: 本地可复现步骤 PASS；workflow YAML 可被 actionlint 校验。

```bash
git add .github .trivyignore docs/operations/security-scanning.md
git diff --cached --check
git commit -m "ci: 建立分层测试与安全门禁"
```

## Task 7：实现 AMD64/ARM64 原生发布与供应链产物

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `deploy/app/Dockerfile`
- Create: `deploy/release/{generate-manifest.sh,verify-release.sh}`
- Create: `docs/operations/release-checklist.md`

- [ ] **Step 1：定义发布输入和版本一致性检查**

只允许 `vMAJOR.MINOR.PATCH` tag 触发；tag 与 Maven/package 版本必须一致，commit 必须在 main 且工作树来源可追溯。构建矩阵在原生 AMD64 和 ARM64 runner 上分别运行 `./mvnw -Pnative native:compile`，禁止用 QEMU 产物替代最终原生测试。

- [ ] **Step 2：构建和验证每个架构**

每个 runner 运行 JVM tests、nativeTest、native compile、启动原生可执行文件并执行核心 API smoke；随后构建单架构 OCI image，记录 binary SHA-256、image digest、GraalVM/Java/OS 版本。聚合作业创建 multi-arch manifest 并验证两个 digest 均存在。

- [ ] **Step 3：生成 SBOM、签名与发布清单**

Syft 为 binary/image 生成 SPDX JSON；Trivy 扫描；Cosign 使用 GitHub OIDC keyless 签名镜像和 attest SBOM/provenance。`release-manifest.json` 包含版本、commit、构建时间、架构、文件、checksum、image digest、SBOM 和测试摘要；同时生成 `SHA256SUMS`。

- [ ] **Step 4：实现发布验证器**

`verify-release.sh` 校验 checksum、Cosign 签名、SBOM、manifest schema、两个架构和 tag/版本一致性。测试用篡改 binary、缺失 ARM64、错误 digest 和过期漏洞豁免证明会失败。

- [ ] **Step 5：提交发布流水线**

```bash
git add .github/workflows/release.yml deploy/app/Dockerfile deploy/release docs/operations/release-checklist.md
git diff --cached --check
git commit -m "ci(release): 发布双架构原生产物"
```

## Task 8：执行完整 v1 验收、恢复证据和文档收口

**Files:**
- Create: `docs/operations/acceptance-report-template.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`
- Create: `CHANGELOG.md`

- [ ] **Step 1：运行完整测试与构建**

Run: `make test && make build && make aot && make native-test`

Expected: 全部 PASS；若本地架构无法执行另一架构，只能由 release matrix 的原生 runner 提供该架构证据，不得标为本地通过。

- [ ] **Step 2：运行生产 Compose 和恢复演练**

Run: `make release-smoke`

Expected: HTTPS、权限、入库、搜索、消耗、调拨、提醒、附件、导出、审计、备份和恢复全部 PASS。

Run: `make restore-smoke`

Expected: 从正式格式备份恢复到空环境，checksum、Flyway、附件和库存对账全部通过。

- [ ] **Step 3：执行发布安全门禁**

在 release workflow 中验证 AMD64/ARM64 原生 smoke、镜像扫描、SBOM、签名和 checksum。验收报告记录 workflow run、commit、image digest、备份 ID、恢复时长和所有检查结果，不记录秘密或真实家庭数据。

- [ ] **Step 4：完成用户和运维文档**

README 提供安装最短路径和受支持平台；deployment 描述 TLS/secret/卷；backup-restore 描述 RPO/RTO 和演练；observability 描述探针、指标和告警；upgrade 描述迁移与回滚；CHANGELOG 记录 v1 能力和已知限制。

- [ ] **Step 5：更新路线图并提交**

路线图阶段八添加本计划链接、正式验收日期和报告路径；只有所有门禁有证据时才把 v1 标记完成。

```bash
git add README.md CHANGELOG.md docs/operations docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "chore(release): 完成 Stocket v1 发布验收"
```

## 最终验收清单

- [ ] 生产只暴露 HTTPS Gateway，容器最小权限且秘密不进入镜像/仓库/备份。
- [ ] liveness/readiness 语义正确，结构化日志脱敏，指标无高基数标签。
- [ ] 敏感端点和公网入口均有限流，可信代理地址解析不可伪造。
- [ ] 每次备份包含数据库、附件、配置摘要、manifest 和 checksum。
- [ ] 恢复在空环境实际执行，并通过迁移、附件哈希、库存对账和核心 API 冒烟。
- [ ] PR、nightly、release 三层 CI 分工明确且最小权限。
- [ ] AMD64/ARM64 原生二进制和镜像在各自原生 runner 上通过测试。
- [ ] 发布包含 SBOM、漏洞报告、签名、provenance、manifest 和 SHA-256 校验和。
- [ ] 完整 v1 验收有可追溯证据，文档足以由新管理员部署、备份、恢复和升级。
- [ ] 阶段八未新增业务范围，路线图中的八个阶段均有独立详细计划。
