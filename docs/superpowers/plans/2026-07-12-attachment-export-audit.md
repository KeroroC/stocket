# 阶段七：附件、导出与审计实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付鉴权附件上传下载、内容验证、发票/保修文件、与筛选结果一致的 CSV 导出、统一审计搜索、管理诊断和贯穿请求/事件/投递的追踪关联。

**Architecture:** `attachment` 模块拥有文件元数据和 Web 根目录外的本地 blob store，采用“临时文件写入并 fsync → 元数据事务 → 原子移动 → 状态确认”的可恢复协议；所有读取先做家庭和对象授权，再以随机存储名流式响应。`audit` 模块消费公开审计事实并提供只读搜索；请求过滤器生成/校验 request ID，并通过事件字段和通知投递 ID 延续关联。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Security 7、Apache Tika、Spring Modulith 2.0.5、PostgreSQL 17、Flyway、Testcontainers、Commons CSV、Vue 3.5、Vitest 4、Playwright、GraalVM Native Image

---

## 执行约束

- 前置条件：阶段二已有身份审计基线，阶段三至五公开目录、库存、提醒和通知查询契约稳定，阶段六已有移动/桌面页面。
- 本地附件根目录由 `STOCKET_ATTACHMENT_DIR` 指定，必须位于 Web 根目录外；数据库只保存随机存储 key，不保存客户端路径。任何用户输入不得参与磁盘路径拼接。
- 允许类型固定为 JPEG、PNG、WebP、PDF；默认单文件 20 MiB、单请求一个文件。扩展名和声明 Content-Type 只作提示，最终类型以内容检测和文件签名为准。
- SVG、HTML、可执行文件、压缩包、多格式伪装和超过限制文件一律拒绝。图片下载使用 `Content-Disposition:inline`，PDF/其他使用 attachment；统一 `X-Content-Type-Options:nosniff`。
- 文件元数据属于家庭，并绑定 `ITEM_DEFINITION` 或 `INVENTORY_ENTRY`；封面图片、发票和保修文档只是 purpose，不新增采购或维修业务。
- CSV 导出与同样筛选参数的搜索使用同一个查询对象和稳定数据库快照；以 UTF-8 BOM 输出，防止 Excel 乱码，对 `= + - @ tab CR` 开头单元格加单引号防公式注入。
- 审计是只追加记录，不保存密码、令牌、密钥、附件正文、完整 Push endpoint 或任意请求体。详情只使用白名单字段。
- 本阶段不实现远程对象存储、OCR、全文附件搜索或运维备份；阶段八负责把附件纳入备份恢复。

## 文件结构

```text
backend/src/main/java/com/stocket/
├── attachment/
│   ├── AttachmentSummary.java
│   └── internal/{domain,storage,validation,web,recovery}/
├── audit/
│   ├── AuditEvent.java
│   └── internal/{listener,query,web}/
└── system/internal/tracing/{RequestIdFilter,RequestContext}.java

frontend/src/
├── api/{attachment,export,audit}.ts
├── components/attachment/{AttachmentUploader,AttachmentGallery,DocumentList}.vue
├── components/export/ExportDialog.vue
└── views/{ItemDetailView,InventoryEntryView,AuditLogView,DiagnosticsView}.vue
```

## Task 1：建立附件、审计索引与请求追踪数据库基线

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V6__attachment_and_audit.sql`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/stocket/DatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/stocket/attachment/AttachmentSchemaIntegrationTest.java`

- [ ] **Step 1：加入锁定的内容检测和 CSV 依赖**

加入 Apache Tika core 和 Apache Commons CSV，版本固定并验证 GraalVM 元数据；不加入图像转码库、对象存储 SDK 或办公文档解析器。

- [ ] **Step 2：写迁移失败测试**

断言 V6、`attachment` 和现有 `audit_log` 新索引/字段存在；同一 storage key、同一对象同一 primary cover 均被唯一约束保护；非法 purpose/status/owner_type 被 check constraint 拒绝。

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,AttachmentSchemaIntegrationTest test`

Expected: FAIL，V6 尚未应用。

- [ ] **Step 3：创建 V6 迁移**

```sql
create table attachment (
  id uuid primary key,
  household_id uuid not null references household(id),
  owner_type varchar(24) not null check (owner_type in ('ITEM_DEFINITION','INVENTORY_ENTRY')),
  owner_id uuid not null,
  purpose varchar(24) not null check (purpose in ('COVER_IMAGE','ITEM_IMAGE','INVOICE','WARRANTY')),
  original_filename varchar(255) not null,
  storage_key varchar(80) not null unique,
  detected_media_type varchar(80) not null,
  size_bytes bigint not null check (size_bytes between 1 and 20971520),
  sha256 char(64) not null,
  status varchar(16) not null check (status in ('STAGED','AVAILABLE','MISSING','DELETED')),
  uploaded_by uuid not null references user_account(id),
  request_id varchar(80) not null,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
create unique index uq_attachment_primary_cover
  on attachment(household_id, owner_type, owner_id)
  where purpose = 'COVER_IMAGE' and status = 'AVAILABLE';
```

为附件 owner 列表、sha256、状态建立索引；为 `audit_log(household_id, occurred_at desc, id desc)`、actor、event_type、request_id 增加组合索引，并补齐旧表的 `household_id` 外键和 `id` 稳定分页条件。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,AttachmentSchemaIntegrationTest test`

Expected: PASS。

```bash
git add backend/pom.xml backend/src/main/resources backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(attachment): 建立附件与审计数据基线"
```

## Task 2：实现安全文件存储和内容验证

**Files:**
- Create: `backend/src/main/java/com/stocket/attachment/internal/storage/{AttachmentStore,LocalAttachmentStore,StoredObject}.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/validation/{AttachmentValidator,ValidatedUpload,MediaTypePolicy}.java`
- Create: `backend/src/test/java/com/stocket/attachment/{LocalAttachmentStoreTest,AttachmentValidatorTest}.java`
- Create: `backend/src/test/resources/attachments/{valid.jpg,valid.png,valid.webp,valid.pdf,polyglot.jpg,script.svg}`

- [ ] **Step 1：写路径和魔数失败测试**

覆盖 `../`、绝对路径、NUL、Unicode 分隔符、超长文件名不会影响 storage path；JPEG/PNG/WebP/PDF 魔数与 Tika 检测一致才接受；伪装 SVG、HTML polyglot、零字节、超限、声明类型不匹配被稳定错误码拒绝。

Run: `cd backend && ./mvnw -Dtest=LocalAttachmentStoreTest,AttachmentValidatorTest test`

Expected: FAIL，存储和验证器不存在。

- [ ] **Step 2：实现随机两级存储键**

storage key 固定为 32 字节随机 hex，并落盘为 `<root>/<first2>/<next2>/<key>`；构造后 `normalize()` 并验证仍以规范 root 开头。写入使用 root 下 `.staging/<uuid>`，限制流式读取字节数、同时计算 SHA-256、`FileChannel.force(true)`，最终用 `ATOMIC_MOVE`；不跟随符号链接，启动时拒绝 root 或父级是 symlink。

- [ ] **Step 3：实现内容策略**

验证顺序为大小上限、文件签名、Tika 检测、允许列表；JPEG/PNG 用 ImageIO 读取尺寸，WebP 显式解析 `VP8`/`VP8L`/`VP8X` 头部尺寸，三类图片均限制总像素 40MP，避免依赖 JDK 未保证提供的 WebP ImageIO reader。PDF 只接受 `%PDF-` 且 EOF 附近含 `%%EOF`。原文件名只保留最后一个 basename，清除控制字符，空名替换为 `attachment`。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=LocalAttachmentStoreTest,AttachmentValidatorTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/attachment backend/src/test/java/com/stocket/attachment backend/src/test/resources/attachments
git diff --cached --check
git commit -m "feat(attachment): 实现安全本地文件存储"
```

## Task 3：实现附件上传、下载、删除与故障恢复

**Files:**
- Create: `backend/src/main/java/com/stocket/attachment/AttachmentSummary.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/domain/{Attachment,AttachmentPurpose,AttachmentStatus}.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/domain/AttachmentRepository.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/web/{AttachmentController,AttachmentResponse}.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/recovery/AttachmentRecoveryJob.java`
- Create: `backend/src/test/java/com/stocket/attachment/{AttachmentApiIntegrationTest,AttachmentRecoveryIntegrationTest}.java`

- [ ] **Step 1：写鉴权和恢复失败测试**

覆盖成员上传/读取、只读成员只能读取、另一家庭始终 404、归档 owner 不能上传、非法 owner type、封面替换、Range 下载、删除后不可读。注入“文件 staging 成功但数据库失败”“数据库 STAGED 成功但原子移动失败”“磁盘文件丢失”三种故障，验证恢复任务清理临时文件或标记 MISSING，不返回半成品。

- [ ] **Step 2：实现上传协议**

```text
POST   /api/v1/attachments?ownerType=&ownerId=&purpose=
GET    /api/v1/attachments/{id}
GET    /api/v1/attachments/{id}/content
DELETE /api/v1/attachments/{id}
```

服务先通过公开 catalog/inventory 查询验证 owner 和家庭，再校验/暂存文件；数据库事务写 STAGED 元数据并提交；原子移动成功后短事务改 AVAILABLE。失败时保留可恢复状态。封面替换在事务中把旧封面标记 DELETED 后启用新封面，物理删除异步执行。

- [ ] **Step 3：实现安全下载**

每次读取先查询带 householdId 的元数据，再打开 storage key。响应设置精确 `Content-Type`、清理后的 RFC 5987 filename、`nosniff`、私有缓存策略和长度；MISSING 返回 `410 ATTACHMENT_CONTENT_MISSING` 并创建管理员诊断，不泄露磁盘路径。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=AttachmentApiIntegrationTest,AttachmentRecoveryIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/attachment backend/src/test/java/com/stocket/attachment
git diff --cached --check
git commit -m "feat(attachment): 实现鉴权附件生命周期"
```

## Task 4：实现统一筛选查询和流式 CSV 导出

**Files:**
- Create: `backend/src/main/java/com/stocket/system/export/{ExportController,CsvExportService,CsvCellSanitizer}.java`
- Create: `backend/src/main/java/com/stocket/catalog/CatalogFilter.java`
- Create: `backend/src/main/java/com/stocket/inventory/InventoryFilter.java`
- Modify: `backend/src/main/java/com/stocket/catalog/internal/search/CatalogSearchService.java`
- Modify: `backend/src/main/java/com/stocket/inventory/internal/query/InventoryQueryService.java`
- Create: `backend/src/test/java/com/stocket/system/export/{CsvCellSanitizerTest,CsvExportIntegrationTest}.java`

- [ ] **Step 1：写一致性和注入失败测试**

对同一 `CatalogFilter`/`InventoryFilter` 比较 JSON 搜索 ID 与 CSV ID 完全一致；覆盖中文、逗号、引号、换行和 `=SUM(1,1)` 等公式前缀；导出过程中并发写库存，断言导出基于单一 repeatable-read 快照且每行内部一致；只读成员允许导出本家庭，跨家庭过滤参数被忽略。

- [ ] **Step 2：抽取不可变筛选对象**

筛选对象集中验证 q、categoryId、locationId、inventoryType、status、expirationFrom/To、includeArchived、sort。Controller、JSON 查询和导出共用同一个 query builder，禁止复制 SQL。导出默认 10 万行上限，超过返回 `422 EXPORT_LIMIT_EXCEEDED` 并建议缩小筛选。

- [ ] **Step 3：实现流式 CSV**

```text
GET /api/v1/exports/catalog.csv
GET /api/v1/exports/inventory.csv
```

使用 `StreamingResponseBody`、UTF-8 BOM 和 Commons CSV；查询事务使用只读 `REPEATABLE_READ`，按 `(sort columns,id)` keyset 分批 1000 行，不在内存积累全部结果。单元格 sanitizer 对危险首字符加 `'`，同时让 CSV 库负责引用和换行。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=CsvCellSanitizerTest,CsvExportIntegrationTest test`

Expected: PASS，导出与筛选结果一致且公式安全。

```bash
git add backend/src/main/java/com/stocket backend/src/test/java/com/stocket/system/export
git diff --cached --check
git commit -m "feat(export): 添加一致性 CSV 导出"
```

## Task 5：建立请求 ID、公开审计事实和白名单记录器

**Files:**
- Create: `backend/src/main/java/com/stocket/system/internal/tracing/{RequestIdFilter,RequestContext}.java`
- Create: `backend/src/main/java/com/stocket/audit/AuditEvent.java`
- Create: `backend/src/main/java/com/stocket/audit/internal/listener/AuditEventListener.java`
- Create: `backend/src/main/java/com/stocket/audit/internal/domain/{AuditLog,AuditLogRepository,AuditDetailsPolicy}.java`
- Create: `backend/src/test/java/com/stocket/audit/{RequestIdFilterTest,AuditEventIntegrationTest,AuditDetailsPolicyTest}.java`

- [ ] **Step 1：写关联和脱敏失败测试**

覆盖客户端合法 `X-Request-Id` 透传、非法/超长值替换、响应始终返回 request ID、同一 ID 进入库存流水/附件元数据/事件/投递。审计详情拒绝 password/token/secret/body/authorization/cookie 字段，嵌套 map 也拒绝。

- [ ] **Step 2：实现请求上下文**

只接受 `[A-Za-z0-9._-]{8,80}`，否则生成 UUID。Filter 在 try/finally 中设置并清理 MDC `requestId`；认证后补充 `accountId`，不得依赖 ThreadLocal 跨异步边界，所有公开事件显式携带 requestId。

- [ ] **Step 3：统一审计事件**

```java
public record AuditEvent(
    UUID eventId, UUID householdId, Instant occurredAt, String eventType,
    String outcome, UUID actorAccountId, String subjectType, UUID subjectId,
    String requestId, String source, Map<String, Object> details) {}
```

把阶段二身份审计适配到该事实；目录、位置、库存、提醒、渠道、附件关键写操作发布事件。监听器用 eventId 作为审计日志主键实现重投去重。`AuditDetailsPolicy` 每个 eventType 有固定允许键和最大字符串长度。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=RequestIdFilterTest,AuditEventIntegrationTest,AuditDetailsPolicyTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket backend/src/test/java/com/stocket/audit
git diff --cached --check
git commit -m "feat(audit): 贯通请求追踪与审计事实"
```

## Task 6：实现审计搜索和管理诊断 API

**Files:**
- Create: `backend/src/main/java/com/stocket/audit/internal/query/{AuditQueryService,AuditController,AuditResponse}.java`
- Create: `backend/src/main/java/com/stocket/system/diagnostics/{DiagnosticsController,DiagnosticsService,DiagnosticsResponse}.java`
- Create: `backend/src/test/java/com/stocket/audit/AuditQueryIntegrationTest.java`
- Create: `backend/src/test/java/com/stocket/system/DiagnosticsApiTest.java`

- [ ] **Step 1：写审计和诊断失败测试**

审计按时间、actor、eventType、outcome、subject、requestId 筛选，游标分页无重复/遗漏；仅管理员可查，跨家庭永不出现。诊断展示数据库、附件目录、未完成模块事件数、DEAD 投递数、对账 OPEN 数、MISSING 附件数，但不展示路径、host、用户名或密钥。

- [ ] **Step 2：实现端点**

```text
GET /api/v1/admin/audit-logs
GET /api/v1/admin/diagnostics
```

审计默认最近 30 天、每页 50、最大 200，排序 `occurredAt desc,id desc`；details 返回经过 policy 的 JSON。诊断每个检查返回 `status,count,checkedAt,actionCode`，不把异常堆栈直接返回前端。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=AuditQueryIntegrationTest,DiagnosticsApiTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/audit backend/src/main/java/com/stocket/system/diagnostics backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(audit): 添加审计搜索与管理诊断"
```

## Task 7：实现附件、导出、审计和诊断前端

**Files:**
- Create: `frontend/src/api/{attachment,export,audit,diagnostics}.ts`
- Create: `frontend/src/components/attachment/{AttachmentUploader,AttachmentGallery,DocumentList}.vue`
- Create: `frontend/src/components/export/ExportDialog.vue`
- Create: `frontend/src/views/{AuditLogView,DiagnosticsView}.vue`
- Create: `frontend/src/views/{ItemDetailView,InventoryEntryView,AuditLogView,DiagnosticsView}.spec.ts`
- Modify: `frontend/src/views/{ItemDetailView,InventoryEntryView}.vue`

- [ ] **Step 1：写前端失败测试**

覆盖移动图片选择/预览/进度/取消、类型和大小错误、发票/保修列表、鉴权下载、封面替换确认；导出对话框复用当前筛选并下载 UTF-8 CSV；审计筛选和 request ID 复制；诊断状态不只靠颜色。只读成员无上传/删除按钮，非管理员无审计/诊断入口。

- [ ] **Step 2：实现附件界面**

Uploader 使用原生 file input 的 `accept=image/jpeg,image/png,image/webp,application/pdf`，但文案明确服务端最终验证；图片预览 URL 在替换/卸载时 revoke。失败保留选择以便重试，401 清除本地预览。下载使用鉴权 fetch 转 Blob，不直接拼接未鉴权公开 URL。

- [ ] **Step 3：实现导出和管理页**

ExportDialog 从当前查询状态构造 URL，显示行数上限提示。审计页使用游标“加载更多”，详情使用定义列表而非原始 JSON dump。诊断页为每个 actionCode 显示固定修复建议，不展示后端原始异常。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/views/ItemDetailView.spec.ts src/views/InventoryEntryView.spec.ts src/views/AuditLogView.spec.ts src/views/DiagnosticsView.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src
git diff --cached --check
git commit -m "feat(frontend): 实现附件导出与审计界面"
```

## Task 8：完成安全、原生和阶段验收

**Files:**
- Create: `backend/src/test/java/com/stocket/attachment/AttachmentExportAuditAcceptanceTest.java`
- Create: `backend/src/test/java/com/stocket/attachment/AttachmentRuntimeHintsTest.java`
- Create: `backend/src/main/java/com/stocket/attachment/internal/config/AttachmentRuntimeHints.java`
- Create: `frontend/e2e/attachment-export-audit.spec.ts`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`

- [ ] **Step 1：写阶段验收和恶意输入集**

完整流程上传封面/PDF、另一家庭和 VIEWER 尝试写入、鉴权下载、按同一筛选搜索与导出、按 requestId 找到相关库存/附件审计。恶意集覆盖路径穿越、公式注入、伪装文件、超限流、缺失磁盘文件和敏感详情字段。

- [ ] **Step 2：运行验证矩阵**

Run: `cd backend && ./mvnw -Dtest=AttachmentExportAuditAcceptanceTest,AttachmentRuntimeHintsTest test`

Run: `cd frontend && npm run test:e2e -- attachment-export-audit.spec.ts`

Run: `make test && make build && make aot`

Expected: 全部 PASS，无 Tika/AOT 资源缺失，所有安全负例返回稳定 Problem Detail。

- [ ] **Step 3：更新文档并提交**

README 记录允许类型、大小、附件根目录、CSV 安全语义、审计保留内容和诊断边界；路线图阶段七增加本计划链接和验收日期。

```bash
git add backend/src/test frontend/e2e README.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "feat: 完成阶段七附件导出与审计"
```

## 最终验收清单

- [ ] 附件路径完全由随机 storage key 决定，内容类型由文件内容验证。
- [ ] 未授权和跨家庭用户无法读取、上传或删除附件。
- [ ] 文件/数据库不一致可诊断和恢复，不会返回半成品。
- [ ] CSV 与相同筛选搜索一致，中文兼容且公式注入被中和。
- [ ] request ID 贯穿请求、流水、事件、通知和审计。
- [ ] 审计只追加、按家庭隔离、详情白名单不含敏感数据。
- [ ] 管理诊断可行动但不泄露基础设施和堆栈细节。
- [ ] `make test`、`make build`、`make aot` 和相关 E2E 通过。
- [ ] 阶段七未引入 S3、OCR、公开附件 URL 或备份调度。
