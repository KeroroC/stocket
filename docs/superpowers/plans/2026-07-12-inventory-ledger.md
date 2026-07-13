# 阶段四：库存台账实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在目录与位置能力之上交付批次和独立资产库存、不可变流水、并发安全的入库/消耗/调拨/调整/损耗/退库、请求幂等与完整性对账。

**Architecture:** `inventory` 模块独占库存数量、位置和流水写权限，通过 `catalog`、`location`、`identity` 的公开契约校验引用；命令服务在单个 PostgreSQL 事务中完成幂等占位、行锁、规则校验、快照更新、流水追加和领域事件发布。`inventory_movement` 是不可变事实，`inventory_entry.available_quantity` 是事务快照；查询侧只读取快照和流水，不绕过命令服务写数据。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Data JPA 4、Spring Security 7、Spring Modulith 2.0.5、PostgreSQL 17、Flyway、Testcontainers、Vue 3.5、TypeScript 5.9、Element Plus 2.14、Vitest 4

---

## 执行约束

- 前置条件：阶段二身份与家庭、阶段三目录与位置已经完成；若阶段三公开 DTO 的最终名称有变化，只允许在适配器中对齐，不得引用其他模块的 `internal` 包。
- 本阶段不实现提醒计算、外部通知、摄像头扫码、附件或离线草稿；只发布公开 `InventoryChanged` 事实供阶段五订阅。
- 数量统一用 `BigDecimal`/`numeric(19,4)`；API 中数量始终是十进制字符串。数量 scale 不超过 4，绝对值不超过 `999999999999999.9999`。
- `BATCH` 的可用数量必须大于等于零；`ASSET` 的可用数量只能为 `0` 或 `1`，且所有资产命令数量固定为 `1`。
- 所有库存写 API 必须带 `Idempotency-Key`；键绑定 `accountId + operation + requestHash`，保留首次 HTTP 状态与响应 JSON。相同键不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 数量和位置变更先用 `PESSIMISTIC_WRITE` 锁定源条目；非数量资料编辑使用 `@Version`。锁顺序固定按 UUID 字典序，避免双条目操作死锁。
- 流水只允许 INSERT；数据库角色和应用均不提供 UPDATE/DELETE 路径。错误流水通过 `REVERSAL` 或带原因的 `ADJUSTMENT` 纠正。
- 每个任务遵循 Red-Green-Refactor；提交前精确暂存并运行 `git diff --cached --check`。提交信息使用中文 Semantic Commit。

## 文件结构

```text
backend/src/main/java/com/stocket/inventory/
├── InventoryChanged.java                    # 阶段五订阅的公开事实
├── InventoryItemAvailability.java           # catalog/search 可读取的汇总契约
├── InventoryQuery.java                      # 跨模块只读查询接口
└── internal/
    ├── config/InventoryProperties.java       # 幂等保留期、对账批量配置
    ├── domain/                               # Entry、BatchDetail、AssetDetail、Movement
    ├── persistence/                          # JPA Repository 和显式锁 SQL
    ├── command/                              # 入库、消耗、调拨、调整命令服务
    ├── idempotency/                          # 请求摘要、占位与响应重放
    ├── query/                                # 库存详情、列表和流水查询
    ├── reconciliation/                       # 快照对账与问题记录
    └── web/                                  # REST Controller 与 DTO

backend/src/test/java/com/stocket/inventory/  # 领域、API、并发、对账和验收测试
frontend/src/
├── api/inventory.ts
├── inventory/{inventoryModels,useInventoryCommands}.ts
├── components/inventory/{InventoryEntryList,MovementTimeline,QuantityInput}.vue
└── views/{InventoryEntryView,InventoryReceiveView,InventoryOperateView}.vue
```

## Task 1：建立库存数据库基线与公开契约

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__inventory_ledger.sql`
- Modify: `backend/src/test/java/com/stocket/DatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/stocket/inventory/InventorySchemaIntegrationTest.java`
- Create: `backend/src/main/java/com/stocket/inventory/{InventoryChanged,InventoryItemAvailability,InventoryQuery}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/domain/{InventoryType,AssetStatus,MovementType}.java`

- [ ] **Step 1：写迁移失败测试**

断言 Flyway 版本包含 `4`，并检查 `inventory_entry`、`batch_detail`、`asset_detail`、`inventory_movement`、`idempotency_record`、`inventory_reconciliation_issue` 六张表。额外直接执行 SQL，证明负批次数量、数量为 `2` 的资产、重复家庭资产号和同一流水 ID 均被数据库拒绝。

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,InventorySchemaIntegrationTest test`

Expected: FAIL，最高迁移版本仍为 `3`，库存表不存在。

- [ ] **Step 2：创建 V4 迁移**

迁移核心约束固定为：

```sql
create table inventory_entry (
  id uuid primary key,
  household_id uuid not null references household(id),
  item_definition_id uuid not null references item_definition(id),
  location_id uuid not null references location(id),
  inventory_type varchar(16) not null check (inventory_type in ('BATCH','ASSET')),
  available_quantity numeric(19,4) not null check (available_quantity >= 0),
  received_at timestamptz not null,
  production_date date,
  expiration_date date,
  custom_attributes jsonb not null default '{}'::jsonb,
  version bigint not null default 0,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (inventory_type = 'BATCH' or available_quantity in (0, 1))
);

create table batch_detail (
  inventory_entry_id uuid primary key references inventory_entry(id),
  batch_number varchar(120),
  source_entry_id uuid references inventory_entry(id),
  shelf_life_value integer,
  shelf_life_unit varchar(8) check (shelf_life_unit in ('DAY','MONTH','YEAR'))
);

create table asset_detail (
  inventory_entry_id uuid primary key references inventory_entry(id),
  household_id uuid not null references household(id),
  asset_number varchar(80) not null,
  serial_number varchar(160),
  purchase_date date,
  warranty_expires_on date,
  status varchar(16) not null check (status in ('AVAILABLE','IN_USE','LOANED','LOST','RETIRED')),
  constraint uq_asset_number unique (household_id, asset_number)
);

create table inventory_movement (
  id uuid primary key,
  household_id uuid not null references household(id),
  entry_id uuid not null references inventory_entry(id),
  related_entry_id uuid references inventory_entry(id),
  movement_type varchar(24) not null,
  quantity_delta numeric(19,4) not null,
  from_location_id uuid references location(id),
  to_location_id uuid references location(id),
  reason varchar(240),
  actor_account_id uuid not null references user_account(id),
  idempotency_record_id uuid not null,
  request_id varchar(80) not null,
  occurred_at timestamptz not null
);
```

`idempotency_record` 对 `(account_id, operation, idempotency_key)` 建唯一约束，保存 `request_hash,status,response_body,created_at,expires_at`；`inventory_reconciliation_issue` 对未解决的 `(entry_id, expected_quantity, actual_quantity)` 去重。为 `household_id,item_definition_id,archived_at`、`location_id`、`expiration_date`、`movement(entry_id,occurred_at desc,id desc)` 建索引。`asset_detail.household_id` 必须由服务从库存条目复制并与条目家庭一致，确保资产号仅在家庭内唯一。

- [ ] **Step 3：定义公开契约**

```java
public record InventoryChanged(
    UUID eventId, UUID householdId, UUID itemId, UUID entryId,
    String operation, BigDecimal quantityDelta, Instant occurredAt) {}

public record InventoryItemAvailability(
    UUID itemId, BigDecimal totalAvailable, LocalDate earliestExpiration,
    int activeEntryCount) {}

public interface InventoryQuery {
    Optional<InventoryItemAvailability> availability(UUID householdId, UUID itemId);
}
```

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,InventorySchemaIntegrationTest test`

Expected: PASS，数据库约束和跨家庭资产号场景均符合预期。

```bash
git add backend/src/main/resources/db/migration/V4__inventory_ledger.sql backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(inventory): 建立库存台账数据基线"
```

## Task 2：实现日期、数量和库存聚合规则

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/domain/{InventoryEntry,BatchDetail,AssetDetail,InventoryMovement}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/domain/{Quantity,ExpirationCalculator,InventoryRules}.java`
- Create: `backend/src/test/java/com/stocket/inventory/internal/domain/{QuantityTest,ExpirationCalculatorTest,InventoryEntryTest}.java`

- [ ] **Step 1：写纯领域失败测试**

覆盖四位小数规范化、零和负数拒绝、闰年 `2024-02-29 + 1 YEAR = 2025-02-28`、月末 `2026-01-31 + 1 MONTH = 2026-02-28`、显式过期日期优先、资产数量约束、已归档条目禁止操作、损耗后状态变化。

Run: `cd backend && ./mvnw -Dtest=QuantityTest,ExpirationCalculatorTest,InventoryEntryTest test`

Expected: FAIL，领域类型不存在。

- [ ] **Step 2：实现值对象和聚合行为**

`Quantity.of(String)` 使用 `new BigDecimal(value).stripTrailingZeros()`，拒绝 scale 大于 4 和越界值；禁止从 `double` 构造。`ExpirationCalculator` 的优先级固定为：请求显式日期，其次生产日期加请求保质期，最后生产日期加目录默认保质期，否则为空。

`InventoryEntry` 只暴露 `receive` 工厂和 `consume/transfer/adjust/markLost/retire/returnToStock` 行为；setter 不得改变数量或位置。每个行为返回包含前后快照的 `MovementDraft`，由命令服务持久化。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=QuantityTest,ExpirationCalculatorTest,InventoryEntryTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/inventory/internal/domain backend/src/test/java/com/stocket/inventory/internal/domain
git diff --cached --check
git commit -m "feat(inventory): 实现库存领域规则"
```

## Task 3：实现幂等执行器和入库事务

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/idempotency/{IdempotencyRecord,IdempotencyRepository,RequestHasher,IdempotentExecutor}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/persistence/{InventoryEntryRepository,InventoryMovementRepository}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/command/{ReceiveInventoryCommand,ReceiveInventoryService}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/web/{InventoryCommandController,ReceiveInventoryRequest,InventoryCommandResponse}.java`
- Create: `backend/src/test/java/com/stocket/inventory/{ReceiveInventoryIntegrationTest,IdempotencyConcurrencyTest}.java`

- [ ] **Step 1：写入库和幂等失败测试**

覆盖批次入库、资产入库、目录默认保质期、显式日期优先、归档物品/位置拒绝、普通成员成功、只读成员 `403`、缺少幂等头 `400 IDEMPOTENCY_KEY_REQUIRED`、相同键重放相同响应、相同键不同请求 `409`、20 个并发同键请求只创建一个条目和一条 `RECEIVE` 流水。

Run: `cd backend && ./mvnw -Dtest=ReceiveInventoryIntegrationTest,IdempotencyConcurrencyTest test`

Expected: FAIL，入库端点不存在。

- [ ] **Step 2：实现确定性请求摘要和占位**

`RequestHasher` 将 operation、规范化 JSON 和 accountId 组合后计算 SHA-256；JSON 使用排序对象键，数量先转成规范十进制字符串。`IdempotentExecutor` 先 INSERT `PROCESSING` 占位并依赖唯一约束竞争；已完成记录直接返回保存的状态和响应，处理中记录返回 `409 IDEMPOTENCY_IN_PROGRESS`，摘要不同返回 `409 IDEMPOTENCY_KEY_REUSED`。业务异常回滚时占位也回滚，客户端可用同键重试。

- [ ] **Step 3：实现入库 API 和事务**

```text
POST /api/v1/inventory/receipts   ADMIN,MEMBER
Header: Idempotency-Key: 1..120 ASCII characters
```

请求固定字段为 `itemId,type,quantity,locationId,receivedAt,productionDate,expirationDate,shelfLifeValue,shelfLifeUnit,batchNumber,assetNumber,serialNumber,purchaseDate,warrantyExpiresOn,customAttributes`。服务通过公开目录/位置查询校验家庭和归档状态，在单事务内创建条目、详情、`RECEIVE` 流水、完成幂等记录并发布 `InventoryChanged`。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ReceiveInventoryIntegrationTest,IdempotencyConcurrencyTest test`

Expected: PASS，并发重放只产生一个业务结果。

```bash
git add backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket/inventory
git diff --cached --check
git commit -m "feat(inventory): 实现幂等入库事务"
```

## Task 4：实现消耗、退库、调整和损耗

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/command/{ConsumeInventoryService,AdjustInventoryService,AssetLifecycleService}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/web/{ConsumeRequest,AdjustmentRequest,AssetStatusRequest}.java`
- Modify: `backend/src/main/java/com/stocket/inventory/internal/web/InventoryCommandController.java`
- Create: `backend/src/test/java/com/stocket/inventory/{ConsumeConcurrencyIntegrationTest,AdjustmentIntegrationTest,AssetLifecycleIntegrationTest}.java`

- [ ] **Step 1：写并发扣减失败测试**

以数量 `10` 的批次并发执行 20 次数量 `1` 的消耗，断言恰好 10 次成功、10 次返回 `409 INSUFFICIENT_STOCK`，最终快照为零且流水累计为零。覆盖负调整必须有原因、退库不能超过历史消耗、资产丢失/报废后数量归零、资产恢复库存时数量回到一。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=ConsumeConcurrencyIntegrationTest,AdjustmentIntegrationTest,AssetLifecycleIntegrationTest test`

Expected: FAIL，命令端点不存在。

- [ ] **Step 3：实现锁定命令**

```text
POST /api/v1/inventory/entries/{id}/consume
POST /api/v1/inventory/entries/{id}/return
POST /api/v1/inventory/entries/{id}/adjust
POST /api/v1/inventory/entries/{id}/lost
POST /api/v1/inventory/entries/{id}/retire
```

所有端点复用幂等执行器。Repository 提供 `findByHouseholdIdAndIdForUpdate` 并使用 `@Lock(PESSIMISTIC_WRITE)`；服务持锁后重新校验余额和状态。调整请求用目标数量而非 delta，服务计算差值并记录 `ADJUSTMENT`；`reason` trim 后长度 1..240。每次成功操作发布一条 `InventoryChanged`。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ConsumeConcurrencyIntegrationTest,AdjustmentIntegrationTest,AssetLifecycleIntegrationTest test`

Expected: PASS，重复运行并发测试三次结果稳定。

```bash
git add backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket/inventory
git diff --cached --check
git commit -m "feat(inventory): 实现库存扣减与调整"
```

## Task 5：实现完整调拨和批次部分拆分

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/command/{TransferInventoryCommand,TransferInventoryService}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/web/TransferRequest.java`
- Modify: `backend/src/main/java/com/stocket/inventory/internal/web/InventoryCommandController.java`
- Create: `backend/src/test/java/com/stocket/inventory/TransferInventoryIntegrationTest.java`

- [ ] **Step 1：写调拨失败测试**

覆盖完整批次只修改位置、部分批次创建目标条目并继承生产/过期/批号、同一目标批次不自动合并、资产只能整件调拨、相同源目标拒绝、归档目标位置拒绝、幂等重放不重复拆分。断言源/目标流水通过 `relatedEntryId` 成对关联，数量累计保持不变。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=TransferInventoryIntegrationTest test`

Expected: FAIL，调拨端点不存在。

- [ ] **Step 3：实现调拨事务**

`POST /api/v1/inventory/entries/{id}/transfer` 接受 `targetLocationId,quantity`。完整调拨追加一条 delta 为零、同时记录 from/to 的 `TRANSFER` 流水；部分调拨锁源条目，源条目扣减并追加 `TRANSFER_OUT`，创建带 `sourceEntryId` 的目标条目并追加 `TRANSFER_IN`。两个流水共享 requestId 和 idempotencyRecordId。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=TransferInventoryIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket/inventory/TransferInventoryIntegrationTest.java
git diff --cached --check
git commit -m "feat(inventory): 实现库存调拨与批次拆分"
```

## Task 6：实现库存查询、流水和目录汇总

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/query/{InventoryQueryService,InventoryQueryController,InventoryEntryResponse,MovementResponse}.java`
- Create: `backend/src/main/java/com/stocket/inventory/internal/query/InventoryQueryRepository.java`
- Create: `backend/src/test/java/com/stocket/inventory/InventoryQueryIntegrationTest.java`

- [ ] **Step 1：写查询失败测试**

断言按物品、位置、类型、状态、到期区间筛选；分页顺序固定为 `expirationDate nulls last, receivedAt, id`；详情包含批次或资产字段；流水倒序稳定；汇总返回总可用量、活跃条目数和最早过期日；家庭隔离和只读权限正确。

- [ ] **Step 2：实现只读查询**

```text
GET /api/v1/inventory/entries
GET /api/v1/inventory/entries/{id}
GET /api/v1/inventory/entries/{id}/movements
GET /api/v1/inventory/availability?itemId={itemId}
```

Repository 的每条 SQL 首条件均为 `household_id = :householdId`。`InventoryQuery` 的公开实现只返回汇总 record，不暴露实体或 Repository。归档条目默认隐藏，`includeArchived=true` 仅管理员可用。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=InventoryQueryIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket/inventory/InventoryQueryIntegrationTest.java
git diff --cached --check
git commit -m "feat(inventory): 添加库存与流水查询"
```

## Task 7：实现完整性对账与模块边界

**Files:**
- Create: `backend/src/main/java/com/stocket/inventory/internal/reconciliation/{InventoryReconciler,ReconciliationIssue,ReconciliationRepository}.java`
- Create: `backend/src/test/java/com/stocket/inventory/{InventoryReconciliationIntegrationTest,InventoryModuleTest}.java`
- Modify: `backend/src/main/java/com/stocket/inventory/package-info.java`
- Modify: `backend/src/test/java/com/stocket/ArchitectureTest.java`

- [ ] **Step 1：写对账和边界失败测试**

测试正常条目无问题；测试中用 SQL 篡改快照后，对账创建一条 OPEN 问题但不修正数量；再次运行不重复；恢复快照后问题变为 RESOLVED。模块测试断言 `inventory` 只依赖 `identity :: api`、`catalog :: api`、`location :: api`，外部模块无法访问库存 Repository。

- [ ] **Step 2：实现批量对账**

对账 SQL 按 entry 聚合 `sum(quantity_delta)`，与快照比较；每批 500 条，使用稳定 UUID 游标。完整调拨的零 delta 不影响累计，部分调拨的 IN/OUT 分别计入对应条目。调度默认关闭，仅暴露管理员触发的 `POST /api/v1/admin/inventory/reconcile`，阶段八再开启定时执行。

- [ ] **Step 3：验证并提交**

Run: `cd backend && ./mvnw -Dtest=InventoryReconciliationIntegrationTest,InventoryModuleTest,ArchitectureTest test`

Expected: PASS，模块验证无循环依赖。

```bash
git add backend/src/main/java/com/stocket/inventory backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(inventory): 添加库存完整性对账"
```

## Task 8：实现库存前端与阶段验收

**Files:**
- Create: `frontend/src/api/inventory.ts`
- Create: `frontend/src/inventory/{inventoryModels,useInventoryCommands}.ts`
- Create: `frontend/src/components/inventory/{InventoryEntryList,MovementTimeline,QuantityInput}.vue`
- Create: `frontend/src/views/{InventoryEntryView,InventoryReceiveView,InventoryOperateView}.vue`
- Create: `frontend/src/views/{InventoryEntryView,InventoryReceiveView,InventoryOperateView}.spec.ts`
- Create: `backend/src/test/java/com/stocket/inventory/InventoryLedgerAcceptanceTest.java`
- Modify: `frontend/src/App.vue`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`

- [ ] **Step 1：写前端失败测试**

覆盖批次/资产字段切换、数量字符串不经浮点转换、提交时生成并复用幂等键、冲突后保留输入、最早过期批次提示、只读成员不显示写操作、流水时间线展示原因和操作者。

Run: `cd frontend && npm test -- src/views/InventoryEntryView.spec.ts src/views/InventoryReceiveView.spec.ts src/views/InventoryOperateView.spec.ts`

Expected: FAIL，库存视图不存在。

- [ ] **Step 2：实现 API 客户端和界面**

`inventory.ts` 与后端命令一一对应；`useInventoryCommands` 在用户发起一次意图时创建 UUID，网络重试复用，用户修改表单后生成新键。页面复用设计系统，移动端主操作固定在底部但不得遮挡内容；状态文字与图标同时表达，不只依赖颜色。

- [ ] **Step 3：写并运行后端阶段验收**

`InventoryLedgerAcceptanceTest` 完成管理员建目录和位置、成员批次入库、并发消耗、部分调拨、资产入库与报废、幂等重放、流水/快照对账的完整流程。

Run: `cd backend && ./mvnw -Dtest=InventoryLedgerAcceptanceTest test`

Expected: PASS。

- [ ] **Step 4：运行阶段验证矩阵并收口**

Run: `make test`

Expected: 后端和前端全套测试 PASS。

Run: `make build && make aot`

Expected: JVM、前端生产构建和 Spring AOT 成功。

更新 README 的库存 API/一致性说明，并在路线图阶段四添加本计划链接和实际验收日期。

```bash
git add frontend/src backend/src/test/java/com/stocket/inventory/InventoryLedgerAcceptanceTest.java README.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "feat: 完成阶段四库存台账"
```

## 最终验收清单

- [ ] 并发消耗不会产生负库存，所有库存写操作均可幂等重放。
- [ ] 流水不可修改或删除，快照与流水累计一致，异常只告警不自动改历史。
- [ ] 部分批次调拨产生可追溯拆分，资产只能整件操作。
- [ ] 显式过期日期优先，月/年保质期使用日历运算。
- [ ] 所有库存读写都隔离家庭，角色权限由后端强制。
- [ ] `inventory` 不引用其他模块的 `internal` 包，架构测试通过。
- [ ] 前端数量全程保持十进制字符串，冲突和重试不丢用户输入。
- [ ] `make test`、`make build`、`make aot` 通过。
- [ ] 阶段四未实现提醒投递、摄像头扫码、附件或离线写队列。
