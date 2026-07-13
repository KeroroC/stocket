# 阶段五：提醒与通知管道实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 根据库存和规则可靠生成过期、已过期与低库存提醒，并通过应用内、Web Push、SMTP 邮件和通用 Webhook 独立投递，支持去重、重试、失败管理和密钥加密。

**Architecture:** `reminder` 模块以 `@ApplicationModuleListener` 异步消费 `InventoryChanged`，在独立事务中重算提醒；Spring Modulith JDBC Event Publication Registry 保存未完成发布并支持重启后重投。`notification` 模块把提醒展开为每成员/每渠道投递记录，后台 worker 用 `FOR UPDATE SKIP LOCKED` 领取任务；每次外部 I/O 都发生在库存事务之外，结果写回独立事务。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Modulith 2.0.5 Events JDBC、Spring Mail、JDK HttpClient、PostgreSQL 17、Flyway、Testcontainers、AES-256-GCM、Vue 3.5、Vitest 4、GraalVM Native Image

---

## 执行约束

- 前置条件：阶段四已经发布稳定的 `InventoryChanged`，并提供按物品和条目读取库存汇总的公开查询契约。
- 不手写第二套通用事件总线。模块间可靠事件使用 Spring Modulith Event Publication Registry；迁移必须与锁定版本 `2.0.5` 的 JDBC schema 完全一致。
- 提醒生成、通知展开、渠道发送分别是独立事务。外部渠道超时或失败不得回滚库存、提醒或其他渠道结果。
- 同一家庭、对象、提醒类型、触发节点最多一条有效提醒；同一提醒、接收成员、渠道最多一条投递。数据库唯一约束是并发去重的最终防线。
- 重试退避固定为 `min(24h, 30s * 2^attempt)`，加入 0..20% 确定性抖动；默认最多 8 次。只有网络错误、`408`、`429` 和 `5xx` 可重试。
- SMTP 密码、Webhook 密钥、VAPID 私钥只以 AES-256-GCM 密文保存。主密钥由 `STOCKET_MASTER_KEY` 注入，缺失或不是 32 字节 Base64 时应用 readiness 为 DOWN。
- 日志、Problem Detail、审计详情不得包含正文、凭据、签名头、Push endpoint 完整值或解密后的秘密。
- 本阶段不实现附件、CSV 导出、摄像头扫描或 Service Worker 应用壳；Push subscription 注册属于通知能力，可先通过 API 和设置页完成。

## 文件结构

```text
backend/src/main/java/com/stocket/
├── reminder/
│   ├── ReminderSummary.java
│   └── internal/{rule,lifecycle,listener,query,web}/
└── notification/
    ├── NotificationRequested.java
    └── internal/{channel,crypto,delivery,worker,web}/

backend/src/test/java/com/stocket/
├── reminder/
└── notification/

frontend/src/
├── api/{reminder,notification}.ts
├── notification/usePushSubscription.ts
├── components/reminder/ReminderList.vue
└── views/{RemindersView,NotificationSettingsView,DeliveryFailuresView}.vue
```

## Task 1：建立提醒、渠道、投递和事件发布数据库基线

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V5__reminder_notification.sql`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/stocket/DatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/stocket/reminder/ReminderNotificationSchemaTest.java`

- [ ] **Step 1：加入事件 JDBC 与邮件依赖并写失败测试**

加入 `spring-modulith-events-jdbc` 和 `spring-boot-starter-mail`。测试断言 `reminder_rule`、`reminder`、`notification_channel`、`push_subscription`、`notification_delivery` 和 Modulith 2.0.5 官方事件发布表存在；验证两个并发事务不能创建相同有效提醒或相同投递。

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,ReminderNotificationSchemaTest test`

Expected: FAIL，V5 表不存在。

- [ ] **Step 2：创建 V5 迁移**

业务表关键约束如下：

```sql
create table reminder_rule (
  id uuid primary key,
  household_id uuid not null references household(id),
  scope_type varchar(16) not null check (scope_type in ('HOUSEHOLD','CATEGORY','ITEM')),
  scope_id uuid,
  expiration_offsets integer[] not null default array[30,7,1,0],
  low_stock_threshold numeric(19,4),
  enabled boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table reminder (
  id uuid primary key,
  household_id uuid not null references household(id),
  item_definition_id uuid not null references item_definition(id),
  inventory_entry_id uuid references inventory_entry(id),
  reminder_type varchar(24) not null check (reminder_type in ('EXPIRING','EXPIRED','LOW_STOCK','INTEGRITY')),
  trigger_key varchar(80) not null,
  trigger_at timestamptz not null,
  status varchar(16) not null check (status in ('SCHEDULED','OPEN','ACKNOWLEDGED','RESOLVED')),
  opened_at timestamptz,
  resolved_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index uq_active_reminder
  on reminder(household_id, item_definition_id,
              coalesce(inventory_entry_id, '00000000-0000-0000-0000-000000000000'),
              reminder_type, trigger_key)
  where status in ('SCHEDULED','OPEN','ACKNOWLEDGED');
```

`notification_channel` 保存 `type,enabled,configuration_json,encrypted_secret,key_version,version`；`push_subscription` 保存成员、endpoint 摘要、加密 endpoint/key/auth；`notification_delivery` 保存 `reminder_id,member_id,channel_type,channel_id,dedupe_key,status,attempt_count,next_attempt_at,last_error_code,last_error_at,delivered_at` 并对 `dedupe_key` 唯一。复制依赖 JAR 中 `org/springframework/modulith/events/jdbc/schema-postgresql.sql` 的 2.0.5 DDL 到本迁移，字段、索引和约束不得自行删改。

- [ ] **Step 3：配置持久事件重投**

```yaml
spring:
  modulith:
    events:
      republish-outstanding-events-on-restart: true
stocket:
  notification:
    worker-batch-size: 50
    max-attempts: 8
    initial-backoff: 30s
    max-backoff: 24h
```

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,ReminderNotificationSchemaTest test`

Expected: PASS。

```bash
git add backend/pom.xml backend/src/main/resources backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(reminder): 建立提醒通知数据基线"
```

## Task 2：实现规则优先级和提醒日期计算

**Files:**
- Create: `backend/src/main/java/com/stocket/reminder/internal/rule/{ReminderRule,ReminderRuleRepository,ReminderRuleService,EffectiveReminderRule}.java`
- Create: `backend/src/main/java/com/stocket/reminder/internal/lifecycle/{ReminderSchedule,ReminderCalculator}.java`
- Create: `backend/src/test/java/com/stocket/reminder/{ReminderRuleServiceTest,ReminderCalculatorTest}.java`

- [ ] **Step 1：写规则失败测试**

覆盖物品覆盖分类、分类覆盖家庭、缺省偏移 `[30,7,1,0]`、重复/负偏移拒绝、低库存阈值零表示关闭、家庭时区午夜转换、已过期条目只生成 EXPIRED、不为无过期日条目生成到期提醒。

Run: `cd backend && ./mvnw -Dtest=ReminderRuleServiceTest,ReminderCalculatorTest test`

Expected: FAIL，规则类型不存在。

- [ ] **Step 2：实现纯计算器**

`EffectiveReminderRule` 固定保存 `expirationOffsets` 的降序不可变列表和可空 `lowStockThreshold`。过期触发时刻为家庭时区目标日期 `09:00` 转换成 Instant；若该时刻已过去但物品尚未过期，提醒立即 OPEN；过期日当天及之后统一使用 `EXPIRED:yyyy-MM-dd` trigger key。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ReminderRuleServiceTest,ReminderCalculatorTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/reminder backend/src/test/java/com/stocket/reminder
git diff --cached --check
git commit -m "feat(reminder): 实现提醒规则计算"
```

## Task 3：可靠消费库存事件并维护提醒生命周期

**Files:**
- Create: `backend/src/main/java/com/stocket/reminder/ReminderSummary.java`
- Create: `backend/src/main/java/com/stocket/reminder/internal/lifecycle/{Reminder,ReminderRepository,ReminderRecalculator}.java`
- Create: `backend/src/main/java/com/stocket/reminder/internal/listener/InventoryChangedListener.java`
- Create: `backend/src/test/java/com/stocket/reminder/{ReminderLifecycleIntegrationTest,EventPublicationRecoveryTest}.java`

- [ ] **Step 1：写生命周期和恢复失败测试**

库存入库生成未来节点和低库存提醒；消耗跨过阈值只生成一条 LOW_STOCK；补货后关闭失效提醒；过期日修改后旧计划 RESOLVED、新计划建立。让监听器第一次故意抛异常，断言库存事务仍提交、事件发布保持未完成；恢复监听器并重投后提醒恰好创建一次且发布完成。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=ReminderLifecycleIntegrationTest,EventPublicationRecoveryTest test`

Expected: FAIL，监听器和提醒聚合不存在。

- [ ] **Step 3：实现持久模块监听器**

```java
@ApplicationModuleListener(id = "reminder.inventory-changed")
void on(InventoryChanged event) {
    recalculator.recalculate(event.householdId(), event.itemId(), event.entryId());
}
```

监听器使用默认 `REQUIRES_NEW`；每次重算先读取有效规则和当前库存，再以 `desired trigger keys` 与数据库有效提醒做集合差：缺失的 INSERT，仍有效的保留，失效的标记 RESOLVED。唯一冲突视为并发重算已完成，不得创建重复通知。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ReminderLifecycleIntegrationTest,EventPublicationRecoveryTest test`

Expected: PASS，事件失败可恢复且无重复提醒。

```bash
git add backend/src/main/java/com/stocket/reminder backend/src/test/java/com/stocket/reminder
git diff --cached --check
git commit -m "feat(reminder): 可靠维护提醒生命周期"
```

## Task 4：实现提醒 API、确认与到点打开任务

**Files:**
- Create: `backend/src/main/java/com/stocket/reminder/internal/query/{ReminderQueryService,ReminderController,ReminderResponse}.java`
- Create: `backend/src/main/java/com/stocket/reminder/internal/lifecycle/ReminderDueJob.java`
- Create: `backend/src/main/java/com/stocket/reminder/internal/web/{ReminderRuleController,ReminderRuleRequest}.java`
- Create: `backend/src/test/java/com/stocket/reminder/ReminderApiIntegrationTest.java`

- [ ] **Step 1：写 API 失败测试**

覆盖按状态/类型/时间筛选、稳定分页、成员确认 OPEN 提醒、确认后库存变化仍可自动 RESOLVED、只读成员可查看和确认但不能改规则、管理员修改家庭/分类/物品规则、版本冲突、到点任务把 SCHEDULED 变 OPEN。

- [ ] **Step 2：实现端点和领取任务**

```text
GET  /api/v1/reminders
POST /api/v1/reminders/{id}/acknowledge
GET  /api/v1/reminder-rules
PUT  /api/v1/reminder-rules/{scopeType}/{scopeId}
```

`ReminderDueJob` 每分钟用 `FOR UPDATE SKIP LOCKED` 领取最多 200 条 `trigger_at <= now()` 的 SCHEDULED 记录，更新为 OPEN 并发布 `NotificationRequested`。确认只改变用户处理状态，不影响投递历史。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ReminderApiIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/reminder backend/src/test/java/com/stocket/reminder/ReminderApiIntegrationTest.java
git diff --cached --check
git commit -m "feat(reminder): 添加提醒查询与处理"
```

## Task 5：实现渠道密钥加密和配置 API

**Files:**
- Create: `backend/src/main/java/com/stocket/notification/internal/crypto/{MasterKeyProvider,SecretCipher,EncryptedSecret}.java`
- Create: `backend/src/main/java/com/stocket/notification/internal/channel/{NotificationChannel,NotificationChannelRepository,ChannelService}.java`
- Create: `backend/src/main/java/com/stocket/notification/internal/web/{NotificationChannelController,ChannelRequest,ChannelResponse}.java`
- Create: `backend/src/test/java/com/stocket/notification/{SecretCipherTest,ChannelApiIntegrationTest}.java`

- [ ] **Step 1：写加密和配置失败测试**

固定测试向量断言 AES-GCM 可往返、相同明文产生不同 nonce、篡改密文失败、旧 key version 可解密后重加密。API 测试断言响应永不返回 secret、空 secret 更新时保留旧值、测试渠道受管理员权限和速率限制、主密钥缺失使 readiness DOWN。

- [ ] **Step 2：实现密文格式**

密文二进制格式固定为 `version(1 byte) + nonce(12 bytes) + ciphertextAndTag` 后 Base64；AAD 为 `householdId:channelId:channelType`，阻止跨记录复制。`MasterKeyProvider` 从环境读取 32 字节 Base64，保留当前和显式配置的上一版本密钥。

- [ ] **Step 3：实现渠道配置**

支持 `IN_APP`、`WEB_PUSH`、`SMTP`、`WEBHOOK`。Webhook URL 只允许 HTTPS，解析后拒绝 loopback、link-local、私网和解析结果漂移；阶段八网关内部地址不走通用 Webhook。SMTP 配置限定 host、port、TLS mode、username、from address；任何日志只记录 channelId。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=SecretCipherTest,ChannelApiIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/notification backend/src/test/java/com/stocket/notification
git diff --cached --check
git commit -m "feat(notification): 加密管理通知渠道"
```

## Task 6：实现通知展开、投递 worker 和失败重试

**Files:**
- Create: `backend/src/main/java/com/stocket/notification/NotificationRequested.java`
- Create: `backend/src/main/java/com/stocket/notification/internal/delivery/{NotificationDelivery,DeliveryRepository,DeliveryPlanner,BackoffPolicy}.java`
- Create: `backend/src/main/java/com/stocket/notification/internal/worker/{DeliveryWorker,ChannelSender,SendResult}.java`
- Create: `backend/src/main/java/com/stocket/notification/internal/channel/{InAppSender,SmtpSender,WebhookSender,WebPushSender}.java`
- Create: `backend/src/test/java/com/stocket/notification/{DeliveryPlannerIntegrationTest,DeliveryWorkerIntegrationTest,BackoffPolicyTest}.java`

- [ ] **Step 1：写展开与重试失败测试**

断言一个提醒按成员偏好展开为独立投递、重复事件不重复记录、某渠道失败不影响其他渠道、`400` 永久失败、`429 Retry-After` 优先、`500` 指数退避、八次后 DEAD、进程在发送后写结果前退出时重领仍由 dedupe key 阻止业务重复。

- [ ] **Step 2：实现投递计划和领取协议**

投递状态固定为 `PENDING,PROCESSING,DELIVERED,RETRY_WAIT,DEAD,CANCELLED`。领取 SQL 使用 `FOR UPDATE SKIP LOCKED`，写 `lease_owner,lease_until` 后提交；网络调用在事务外执行；结果用 deliveryId + leaseOwner 条件更新。过期 lease 可重领。外发请求携带 `X-Stocket-Delivery-Id`，Webhook 使用 HMAC-SHA256 签名和时间戳。

- [ ] **Step 3：实现四种 sender**

`InAppSender` 只标记可见，不发网络；SMTP 设置连接/读取总超时；Webhook 禁止自动跨主机重定向并限制响应体读取 4 KiB；Web Push subscription 全字段加密，HTTP `404/410` 时禁用订阅。所有 sender 返回分类后的 `SendResult`，不直接改数据库。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=DeliveryPlannerIntegrationTest,DeliveryWorkerIntegrationTest,BackoffPolicyTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/notification backend/src/test/java/com/stocket/notification
git diff --cached --check
git commit -m "feat(notification): 实现可靠通知投递"
```

## Task 7：实现 Push 订阅、失败管理和前端提醒界面

**Files:**
- Create: `backend/src/main/java/com/stocket/notification/internal/web/{PushSubscriptionController,DeliveryAdminController,DeliveryResponse}.java`
- Create: `backend/src/test/java/com/stocket/notification/NotificationAdminApiTest.java`
- Create: `frontend/src/api/{reminder,notification}.ts`
- Create: `frontend/src/notification/usePushSubscription.ts`
- Create: `frontend/src/components/reminder/ReminderList.vue`
- Create: `frontend/src/views/{RemindersView,NotificationSettingsView,DeliveryFailuresView}.vue`
- Create: `frontend/src/views/{RemindersView,NotificationSettingsView,DeliveryFailuresView}.spec.ts`

- [ ] **Step 1：写 API 和界面失败测试**

覆盖当前成员注册/删除 Push subscription、管理员分页查看 DEAD 投递、手工重试把状态恢复 PENDING、非管理员禁止查看失败详情。前端覆盖提醒筛选/确认、浏览器拒绝通知权限、订阅更新、渠道表单不回显密码、失败重试反馈。

- [ ] **Step 2：实现管理 API**

```text
PUT    /api/v1/notification/push-subscription
DELETE /api/v1/notification/push-subscription
GET    /api/v1/admin/notification/deliveries?status=DEAD
POST   /api/v1/admin/notification/deliveries/{id}/retry
```

手工重试清除 `last_error_code`、设置 `attempt_count=0,next_attempt_at=now()`，但保留历史时间字段供审计。列表响应只给 endpoint 摘要和错误分类。

- [ ] **Step 3：实现前端**

提醒页按 OPEN/ACKNOWLEDGED 分段并展示具体日期、位置和剩余量。设置页用 toggle 控制启用，密钥字段为空表示不修改。Push composable 只在用户点击启用后请求浏览器权限，不在页面加载时弹权限框。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=NotificationAdminApiTest test`

Run: `cd frontend && npm test -- src/views/RemindersView.spec.ts src/views/NotificationSettingsView.spec.ts src/views/DeliveryFailuresView.spec.ts && npm run typecheck`

Expected: 后端与前端测试全部 PASS。

```bash
git add backend/src frontend/src
git diff --cached --check
git commit -m "feat(frontend): 实现提醒与通知管理"
```

## Task 8：完成可靠性、原生兼容和阶段验收

**Files:**
- Create: `backend/src/test/java/com/stocket/reminder/ReminderNotificationAcceptanceTest.java`
- Create: `backend/src/test/java/com/stocket/notification/NotificationRuntimeHintsTest.java`
- Modify: `backend/src/main/java/com/stocket/notification/internal/config/NotificationRuntimeHints.java`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`

- [ ] **Step 1：写端到端验收**

流程固定为：入库临期批次、确认提醒、消耗至低库存、模拟 Webhook 首次 500 后成功、模拟 SMTP 永久失败、重启上下文并恢复未完成模块事件；断言库存始终已提交、提醒无重复、两渠道状态互不影响。

- [ ] **Step 2：运行验收和故障注入**

Run: `cd backend && ./mvnw -Dtest=ReminderNotificationAcceptanceTest,NotificationRuntimeHintsTest test`

Expected: PASS，未完成事件和过期 lease 均可恢复。

- [ ] **Step 3：运行完整矩阵并更新文档**

Run: `make test && make build && make aot`

Expected: 全部 PASS；AOT 无邮件、HTTP client、加密或事件序列化缺失提示。

README 记录主密钥格式、渠道配置、失败重试和敏感日志边界；路线图阶段五添加本计划链接和实际验收日期。

- [ ] **Step 4：提交阶段收口**

```bash
git add backend/src/test backend/src/main/java/com/stocket/notification/internal/config README.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "feat: 完成阶段五提醒通知管道"
```

## 最终验收清单

- [ ] 库存变化能可靠重算提醒，监听失败不回滚库存且重投不重复。
- [ ] 提醒规则优先级、家庭时区、过期和低库存边界均有确定性测试。
- [ ] 四种渠道独立投递，失败分类、退避、lease 和手工重试可恢复。
- [ ] 所有渠道秘密静态加密，API 和日志不泄露敏感数据。
- [ ] 外部网络 I/O 不发生在库存事务内。
- [ ] 模块事件使用 Spring Modulith 2.0.5 持久发布注册表，重启恢复已验证。
- [ ] `make test`、`make build`、`make aot` 通过。
- [ ] 阶段五未实现附件、CSV 导出、摄像头扫描或离线库存写入。
