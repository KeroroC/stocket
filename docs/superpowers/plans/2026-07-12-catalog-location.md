# 阶段三：目录与位置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在阶段二身份与家庭能力之上，实现家庭隔离的分类树、属性模式、位置树与二维码、可复用物品档案、标签、唯一条码、归档规则和目录搜索，使成员能按名称或精确条码找到物品。

**Architecture:** `catalog` 和 `location` 保持独立 Spring Modulith 模块，各自拥有实体、Repository、应用服务和 REST API；它们只通过 `identity` 的公开 `CurrentHousehold` 契约取得家庭与成员上下文。目录写事务同步维护 `catalog_search_projection`，精确条码优先，普通文本用 PostgreSQL `pg_trgm` 排序；阶段四通过公开事件扩展库存汇总，不在本阶段创建库存条目或流水。

**Tech Stack:** Java 25、Spring Boot 4.0.3、Spring Data JPA 4、Spring Security、Spring Modulith 2.0.5、PostgreSQL 17、Flyway、Testcontainers、Vue 3.5、TypeScript 5.9、Element Plus 2.14、Vitest 4

---

## 执行约束

- 前置条件：先完整执行 `2026-07-12-identity-household.md` 和 `2026-07-12-frontend-design-system.md`；若实际文件名与计划有偏差，以已落地的公开契约为准，但不得从 `catalog` 或 `location` 引用 `identity.internal`。
- 本阶段不创建 `inventory_entry`、库存数量、批次、资产、附件、提醒或离线扫码；搜索响应中的库存字段留给阶段四扩展。
- 所有数据查询必须带 `householdId`，客户端提交的家庭 ID 一律忽略。管理员可维护分类和位置；管理员与普通成员可维护物品；只读成员只有读权限。
- 所有更新请求携带 `version`，不匹配返回 `409` 和稳定错误码 `VERSION_CONFLICT`。所有写入使用数据库事务，所有业务错误继续由 `ApiExceptionHandler` 输出 `application/problem+json`。
- 分类和位置禁止形成环；归档父节点前必须没有活跃子节点，归档分类前必须没有活跃物品。恢复子节点前父节点必须活跃。
- 条码规范化为 `trim + uppercase(Locale.ROOT)` 后比较，并在家庭内唯一；原始展示值保留。空白名称按 Unicode 空白折叠后比较，搜索文本使用 PostgreSQL `lower`、包含匹配和 `pg_trgm`，不引入英文全文分词。
- 分类属性模式仅支持 `TEXT`、`NUMBER`、`BOOLEAN`、`DATE`、`ENUM`。子分类不继承父分类模式；修改模式时必须验证现有活跃物品，不能使已有数据失效。
- 每个任务均按 Red-Green-Refactor 执行；任务内指定测试通过后才提交。提交信息使用中文 Semantic Commit。

## 文件结构

```text
backend/src/main/java/com/stocket/
├── identity/
│   ├── CurrentHousehold.java                 # 跨模块只读身份上下文
│   └── CurrentHouseholdProvider.java
├── catalog/
│   ├── CatalogItemChanged.java               # 阶段四可订阅的公开事件
│   ├── CatalogItemSummary.java               # 跨模块只读目录契约
│   └── internal/
│       ├── category/                          # 分类聚合、模式校验与 API
│       ├── item/                              # 物品、标签、条码与 API
│       └── search/                            # 同步投影与查询 API
└── location/
    ├── LocationSummary.java                  # 跨模块只读位置契约
    └── internal/                              # 位置聚合、二维码与 API

frontend/src/
├── api/{catalog,location}.ts
├── catalog/{catalogModels,useCatalogSearch}.ts
├── components/catalog/{CategoryTree,ItemForm,ItemSearchResults}.vue
├── components/location/LocationTree.vue
└── views/{Items,ItemDetail,CategoryAdmin,LocationAdmin}.vue
```

数据库迁移统一放在 `V3__catalog_and_location.sql`。JPA 实体留在模块 `internal` 包；Controller 不直接注入 Repository。公开事件和查询 DTO 放在模块根包，避免后续模块依赖内部实现。

## Task 1：建立身份上下文公开契约与数据库迁移

**Files:**
- Create: `backend/src/main/java/com/stocket/identity/CurrentHousehold.java`
- Create: `backend/src/main/java/com/stocket/identity/CurrentHouseholdProvider.java`
- Create: `backend/src/main/java/com/stocket/identity/internal/security/SecurityCurrentHouseholdProvider.java`
- Create: `backend/src/main/resources/db/migration/V3__catalog_and_location.sql`
- Modify: `backend/src/test/java/com/stocket/DatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/stocket/catalog/CatalogSchemaIntegrationTest.java`

- [ ] **Step 1：写迁移和家庭隔离失败测试**

在 `CatalogSchemaIntegrationTest` 使用现有 PostgreSQL Testcontainer，插入两个家庭并验证同一条码可跨家庭复用、同一家庭重复条码被唯一索引拒绝；在 `DatabaseMigrationTest` 将期望迁移版本改为 `3`，并断言以下表存在：

```java
assertThat(tableNames()).contains(
    "category", "location", "item_definition", "item_barcode",
    "item_tag", "catalog_search_projection"
);
assertThat(appliedMigrationVersions()).containsExactly("1", "2", "3");
```

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,CatalogSchemaIntegrationTest test`

Expected: FAIL，Flyway 最高版本仍为 `2`，目录表不存在。

- [ ] **Step 3：实现公开身份契约**

```java
package com.stocket.identity;

import java.util.UUID;

public record CurrentHousehold(UUID householdId, UUID memberId, IdentityRole role) {}
```

```java
package com.stocket.identity;

public interface CurrentHouseholdProvider {
    CurrentHousehold requireCurrent();
}
```

`SecurityCurrentHouseholdProvider` 从阶段二 `SecurityContext` 的 `IdentityPrincipal` 映射上述 record；匿名访问抛出阶段二已有的未认证异常。只有该适配器允许引用 `identity.internal.security.IdentityPrincipal`。

- [ ] **Step 4：创建 V3 迁移**

迁移必须创建 UUID 主键、`household_id` 外键、`version bigint not null default 0`、`created_at/updated_at timestamptz` 和可空 `archived_at`。核心约束如下：

```sql
create table category (
  id uuid primary key,
  household_id uuid not null references household(id),
  parent_id uuid references category(id),
  name varchar(120) not null,
  normalized_name varchar(120) not null,
  default_inventory_type varchar(16) not null check (default_inventory_type in ('BATCH','ASSET')),
  attribute_schema jsonb not null default '[]'::jsonb check (jsonb_typeof(attribute_schema) = 'array'),
  version bigint not null default 0,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index uq_category_active_sibling_name
  on category (household_id, coalesce(parent_id, '00000000-0000-0000-0000-000000000000'), normalized_name)
  where archived_at is null;
```

`location` 使用同样的父子和活跃同级名称约束，并增加家庭内唯一的 `public_code varchar(64)`。`item_definition` 包含 `category_id`、`name`、`normalized_name`、`brand`、`model`、`specification`、`default_unit`、`default_shelf_life_value integer`、`default_shelf_life_unit`、`custom_attributes jsonb`；保质期单位限制为 `DAY/MONTH/YEAR`。`item_barcode` 保存 `raw_value` 和 `normalized_value`，唯一索引为 `(household_id, normalized_value)`。`item_tag` 唯一索引为 `(item_definition_id, normalized_value)`。

`catalog_search_projection` 以 `item_definition_id` 为主键，保存 `household_id`、`display_name`、`category_path`、`brand`、`model`、`specification`、`tags text[]`、`raw_barcodes text[]`、`normalized_barcodes text[]`、`searchable_text`、`archived boolean`、`updated_at`；对 `searchable_text gin_trgm_ops` 建 GIN 索引，对规范化条码数组建 GIN 索引，并为 `(household_id, archived, display_name, item_definition_id)` 建稳定分页索引。V1 已启用 `pg_trgm`，V3 不重复修改扩展权限。

- [ ] **Step 5：验证迁移并提交**

Run: `cd backend && ./mvnw -Dtest=DatabaseMigrationTest,CatalogSchemaIntegrationTest test`

Expected: PASS；重复家庭内条码触发唯一约束，不同家庭相同条码成功。

```bash
git add backend/src/main/java/com/stocket/identity backend/src/main/resources/db/migration/V3__catalog_and_location.sql backend/src/test/java/com/stocket/DatabaseMigrationTest.java backend/src/test/java/com/stocket/catalog/CatalogSchemaIntegrationTest.java
git diff --cached --check
git commit -m "feat(catalog): 建立目录位置数据基线"
```

## Task 2：实现分类树与循环保护

**Files:**
- Create: `backend/src/main/java/com/stocket/catalog/internal/category/{Category,InventoryType,AttributeDefinition,AttributeType}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/category/{CategoryRepository,CategoryService,CategoryMapper}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/category/{CategoryController,CategoryRequest,CategoryResponse}.java`
- Create: `backend/src/test/java/com/stocket/catalog/CategoryIntegrationTest.java`

- [ ] **Step 1：写分类行为失败测试**

测试以管理员会话创建“食品 > 冷藏食品”，覆盖树顺序、同级重名 `409 CATEGORY_NAME_CONFLICT`、把父节点移动到子孙下返回 `409 CATEGORY_CYCLE`、普通成员写入 `403`、另一家庭读取 `404`。

```java
mockMvc.perform(patch("/api/v1/categories/{id}", foodId)
        .with(adminSession())
        .contentType(APPLICATION_JSON)
        .content(json(Map.of("name", "食品", "parentId", chilledId, "version", 0))))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
```

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=CategoryIntegrationTest test`

Expected: FAIL，分类 API 不存在。

- [ ] **Step 3：实现分类聚合和服务**

`CategoryService` 固定提供 `listTree()`、`create(request)`、`update(id, request)`、`archive(id, version)`、`restore(id, version)`。所有 Repository 方法都以 `householdId` 开头；移动节点前从目标父节点向上遍历，遇到当前 ID 即拒绝。响应使用平铺节点列表，每个节点携带 `id,parentId,name,defaultInventoryType,attributeSchema,version,archived`，由前端组树，避免递归 JSON 造成懒加载和循环引用。

- [ ] **Step 4：实现 REST 与权限**

```text
GET    /api/v1/categories?includeArchived=false  ADMIN,MEMBER,READ_ONLY
POST   /api/v1/categories                        ADMIN
PATCH  /api/v1/categories/{id}                   ADMIN
POST   /api/v1/categories/{id}/archive           ADMIN
POST   /api/v1/categories/{id}/restore           ADMIN
```

写请求通过 `@PreAuthorize("hasRole('ADMIN')")`，服务仍验证家庭边界。名称为 1..120 字符；根节点 `parentId=null`。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=CategoryIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/catalog/internal/category backend/src/test/java/com/stocket/catalog/CategoryIntegrationTest.java
git diff --cached --check
git commit -m "feat(catalog): 实现分类树管理"
```

## Task 3：实现属性模式和物品属性校验器

**Files:**
- Create: `backend/src/main/java/com/stocket/catalog/internal/category/{AttributeSchemaValidator,AttributeValidationException}.java`
- Create: `backend/src/test/java/com/stocket/catalog/internal/category/AttributeSchemaValidatorTest.java`
- Modify: `backend/src/main/java/com/stocket/catalog/internal/category/CategoryService.java`
- Modify: `backend/src/test/java/com/stocket/catalog/CategoryIntegrationTest.java`

- [ ] **Step 1：写模式与值校验失败测试**

覆盖重复 key、非法 key、ENUM 无选项、NUMBER 默认值类型错误、必填字段缺失、未知属性、ISO 日期、合法小数和枚举值。模式对象固定为：

```json
{"key":"storageTemperature","label":"储存温度","type":"NUMBER","required":true,"defaultValue":4,"options":[],"order":10}
```

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=AttributeSchemaValidatorTest,CategoryIntegrationTest test`

Expected: FAIL，校验器不存在。

- [ ] **Step 3：实现确定性校验**

key 必须匹配 `[a-z][A-Za-z0-9]{0,63}`，label 长度 1..80，order 非负且唯一。`validateValues(schema, values)` 先填充非空默认值，再拒绝未知 key，最后逐字段验证 JSON 类型；DATE 必须是可由 `LocalDate.parse` 解析的字符串，ENUM 值必须包含于非空且无重复的 `options`。

修改分类模式前，`CategoryService` 分页读取该分类所有活跃物品属性并用新模式验证；任何一条失败返回 `409 ATTRIBUTE_SCHEMA_INCOMPATIBLE`，响应 detail 指出物品 ID 和第一个失败 key，但不回显敏感内容。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=AttributeSchemaValidatorTest,CategoryIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/catalog/internal/category backend/src/test/java/com/stocket/catalog
git diff --cached --check
git commit -m "feat(catalog): 校验分类属性模式"
```

## Task 4：实现位置树和稳定二维码标识

**Files:**
- Create: `backend/src/main/java/com/stocket/location/LocationSummary.java`
- Create: `backend/src/main/java/com/stocket/location/internal/{Location,LocationRepository,LocationService,LocationCodeGenerator}.java`
- Create: `backend/src/main/java/com/stocket/location/internal/{LocationController,LocationRequest,LocationResponse,ResolveLocationCodeRequest}.java`
- Create: `backend/src/test/java/com/stocket/location/{LocationIntegrationTest,LocationCodeGeneratorTest}.java`

- [ ] **Step 1：写位置树和扫码失败测试**

覆盖“家 > 厨房 > 冰箱”、循环移动、同级重名、跨家庭隔离、普通成员不能维护位置、已登录三角色均可解析本家庭二维码、错误家庭或未知 code 返回 `404 LOCATION_CODE_NOT_FOUND`。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=LocationIntegrationTest,LocationCodeGeneratorTest test`

Expected: FAIL，位置 API 不存在。

- [ ] **Step 3：实现位置聚合与二维码格式**

新位置生成 128 bit 随机 Base64 URL-safe 无填充 `publicCode`，二维码载荷固定为 `stocket:location:<publicCode>`。解析器只接受该前缀、总长度不超过 128；二维码只是定位符，不代替身份认证。`LocationSummary` 暴露 `id,name,fullPath,archived`，fullPath 在服务层通过祖先链生成。

```text
GET    /api/v1/locations?includeArchived=false
POST   /api/v1/locations
PATCH  /api/v1/locations/{id}
POST   /api/v1/locations/{id}/archive
POST   /api/v1/locations/{id}/restore
POST   /api/v1/locations/resolve-code
```

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=LocationIntegrationTest,LocationCodeGeneratorTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/location backend/src/test/java/com/stocket/location
git diff --cached --check
git commit -m "feat(location): 实现位置树与二维码解析"
```

## Task 5：实现物品档案、标签和条码事务

**Files:**
- Create: `backend/src/main/java/com/stocket/catalog/{CatalogItemChanged,CatalogItemSummary}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/item/{ItemDefinition,ItemBarcode,ItemTag,ShelfLifeUnit}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/item/{ItemRepository,ItemBarcodeRepository,ItemService,ItemMapper}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/item/{ItemController,ItemRequest,ItemResponse}.java`
- Create: `backend/src/test/java/com/stocket/catalog/ItemIntegrationTest.java`

- [ ] **Step 1：写物品事务失败测试**

覆盖创建含两个条码和标签的物品、精确读取、分类属性默认值、重复条码整体回滚、更新版本冲突、普通成员可写、只读成员 `403`、归档分类不能新增物品、跨家庭分类 ID 返回 `404`。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=ItemIntegrationTest test`

Expected: FAIL，物品 API 不存在。

- [ ] **Step 3：实现聚合和公开契约**

`ItemRequest` 字段固定为 `name,categoryId,brand,model,specification,defaultUnit,defaultShelfLifeValue,defaultShelfLifeUnit,customAttributes,barcodes,tags,version`。创建时 version 省略，更新时必填。条码和标签集合用 replace-all 语义在同一事务更新，先规范化并检查请求内重复，再依赖数据库唯一索引处理并发冲突。

```java
public record CatalogItemChanged(UUID householdId, UUID itemId) {}

public record CatalogItemSummary(
    UUID id, String name, UUID categoryId, String defaultUnit, boolean archived
) {}
```

成功提交前发布 `CatalogItemChanged`；Spring Modulith 事务监听器在 Task 6 更新投影。

- [ ] **Step 4：实现 API 和归档规则**

```text
GET    /api/v1/items/{id}             ADMIN,MEMBER,READ_ONLY
POST   /api/v1/items                  ADMIN,MEMBER
PATCH  /api/v1/items/{id}             ADMIN,MEMBER
POST   /api/v1/items/{id}/archive     ADMIN,MEMBER
POST   /api/v1/items/{id}/restore     ADMIN,MEMBER
```

归档物品保留条码占用，防止旧标签指向新物品；恢复前分类必须活跃。分类归档前检查活跃物品，返回 `409 CATEGORY_NOT_EMPTY`。

- [ ] **Step 5：验证并提交**

Run: `cd backend && ./mvnw -Dtest=ItemIntegrationTest test`

Expected: PASS。

```bash
git add backend/src/main/java/com/stocket/catalog backend/src/test/java/com/stocket/catalog/ItemIntegrationTest.java
git diff --cached --check
git commit -m "feat(catalog): 实现物品标签与条码"
```

## Task 6：实现同步目录搜索投影

**Files:**
- Create: `backend/src/main/java/com/stocket/catalog/internal/search/{CatalogSearchProjection,CatalogSearchRepository,CatalogProjectionUpdater}.java`
- Create: `backend/src/main/java/com/stocket/catalog/internal/search/{CatalogSearchService,CatalogSearchController,CatalogSearchResult}.java`
- Create: `backend/src/test/java/com/stocket/catalog/CatalogSearchIntegrationTest.java`

- [ ] **Step 1：写搜索排序失败测试**

准备“蒙牛纯牛奶”“伊利牛奶”和条码 `6900000000012`，断言条码精确结果唯一且 `matchType=BARCODE_EXACT`；查询“牛奶”返回名称匹配并按相似度、名称、UUID 稳定排序；空白折叠与大小写不影响结果；归档物品默认隐藏；另一家庭数据永不出现。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd backend && ./mvnw -Dtest=CatalogSearchIntegrationTest test`

Expected: FAIL，搜索端点不存在。

- [ ] **Step 3：实现事务内投影更新**

`CatalogProjectionUpdater` 使用 `@TransactionalEventListener(phase = BEFORE_COMMIT)` 读取物品、分类、标签和条码，将名称、品牌、型号、规格、分类名和标签用单空格连接并 lowercase 写入投影。物品归档仍保留投影行并标记 archived，方便管理员显式查询。

搜索算法先规范化 query；若与条码精确命中则直接返回。否则执行参数化原生 SQL：

```sql
where p.household_id = :householdId
  and (:includeArchived or not p.archived)
  and (p.searchable_text like '%' || :query || '%'
       or similarity(p.searchable_text, :query) >= 0.15)
order by
  case when p.searchable_text like :query || '%' then 0 else 1 end,
  similarity(p.searchable_text, :query) desc,
  p.display_name asc,
  p.item_definition_id asc
limit :limit offset :offset
```

请求参数 `q` 长度 1..120，`page>=0`，`size` 默认 20 且最大 100。响应提供 `items,page,size,total`；每项仅含目录字段、分类路径、标签、条码和 `matchType`，不伪造库存数量。

- [ ] **Step 4：验证查询计划和测试**

Run: `cd backend && ./mvnw -Dtest=CatalogSearchIntegrationTest test`

Expected: PASS；测试额外执行 `EXPLAIN` 并断言存在 `gin_catalog_search_text` 索引名，避免退化为无索引全表扫描。

- [ ] **Step 5：提交搜索投影**

```bash
git add backend/src/main/java/com/stocket/catalog/internal/search backend/src/test/java/com/stocket/catalog/CatalogSearchIntegrationTest.java
git diff --cached --check
git commit -m "feat(catalog): 添加目录搜索投影"
```

## Task 7：补齐归档、并发和模块边界验收

**Files:**
- Create: `backend/src/test/java/com/stocket/catalog/CatalogArchivalIntegrationTest.java`
- Create: `backend/src/test/java/com/stocket/catalog/CatalogConcurrencyIntegrationTest.java`
- Modify: `backend/src/main/java/com/stocket/catalog/package-info.java`
- Modify: `backend/src/main/java/com/stocket/location/package-info.java`
- Modify: `backend/src/test/java/com/stocket/ArchitectureTest.java`

- [ ] **Step 1：写跨聚合归档和并发失败测试**

测试分类含活跃子节点或物品时不能归档，位置含活跃子节点时不能归档，恢复子节点时父节点必须活跃；两个事务用相同 version 更新物品时恰好一个成功、另一个得到 `VERSION_CONFLICT`；并发创建同条码时恰好一个成功且失败事务不遗留物品。

- [ ] **Step 2：运行测试并定位失败**

Run: `cd backend && ./mvnw -Dtest=CatalogArchivalIntegrationTest,CatalogConcurrencyIntegrationTest,ArchitectureTest test`

Expected: 若前序实现缺少并发异常映射或模块公开契约，测试 FAIL 并指出具体规则。

- [ ] **Step 3：最小修正异常映射和模块契约**

将 `ObjectOptimisticLockingFailureException` 映射为 `409 VERSION_CONFLICT`，条码唯一约束映射为 `409 BARCODE_CONFLICT`。在两个 `package-info.java` 的 `@ApplicationModule` 上声明 `allowedDependencies = "identity"`；全局 `system` 异常处理器反向观察 Web 异常，不要求业务模块依赖 `system`。`ArchitectureTest` 继续运行 `modules.verify()`，并增加 ArchUnit 断言，禁止 `catalog..` 或 `location..` 引用任何其他模块的 `..internal..` 类型。

- [ ] **Step 4：验证并提交**

Run: `cd backend && ./mvnw -Dtest=CatalogArchivalIntegrationTest,CatalogConcurrencyIntegrationTest,ArchitectureTest test`

Expected: PASS。

```bash
git add backend/src/test/java/com/stocket backend/src/main/java/com/stocket/system/ApiExceptionHandler.java backend/src/main/java/com/stocket/catalog backend/src/main/java/com/stocket/location
git diff --cached --check
git commit -m "test(catalog): 强化归档并发与模块边界"
```

## Task 8：建立前端目录与位置 API 客户端

**Files:**
- Create: `frontend/src/api/catalog.ts`
- Create: `frontend/src/api/location.ts`
- Create: `frontend/src/catalog/catalogModels.ts`
- Create: `frontend/src/catalog/useCatalogSearch.ts`
- Create: `frontend/src/catalog/useCatalogSearch.spec.ts`

- [ ] **Step 1：写搜索状态机失败测试**

使用 fake timers 断言输入后 250ms 防抖、后发请求取消前发请求、空查询不发请求、条码查询结果保留 `BARCODE_EXACT`，HTTP problem 映射为可展示错误。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/catalog/useCatalogSearch.spec.ts`

Expected: FAIL，模块不存在。

- [ ] **Step 3：实现类型和客户端**

`catalogModels.ts` 定义与后端一致的 `CategoryNode`、`AttributeDefinition`、`ItemDefinition`、`CatalogSearchResult`；数量型 JSON 值保持 `number`，保质期字段保持整数。`catalog.ts` 提供 `listCategories/createCategory/updateCategory/archiveCategory/restoreCategory/getItem/createItem/updateItem/archiveItem/restoreItem/searchCatalog`。`location.ts` 提供对称树操作和 `resolveLocationCode(payload)`。全部复用阶段二 `http.ts` 的 Cookie、CSRF 和 problem 处理。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/catalog/useCatalogSearch.spec.ts && npm run typecheck`

Expected: PASS，类型检查退出码 0。

```bash
git add frontend/src/api frontend/src/catalog
git diff --cached --check
git commit -m "feat(frontend): 添加目录位置客户端"
```

## Task 9：实现分类与位置管理界面

**Files:**
- Create: `frontend/src/components/catalog/{CategoryTree,CategoryEditor}.vue`
- Create: `frontend/src/components/location/{LocationTree,LocationEditor}.vue`
- Create: `frontend/src/views/{CategoryAdminView,LocationAdminView}.vue`
- Create: `frontend/src/views/{CategoryAdminView,LocationAdminView}.spec.ts`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1：写管理流程失败测试**

分类测试断言树层级、添加子分类、属性模式字段编辑、冲突错误、归档确认和只读隐藏操作；位置测试断言完整路径、添加子位置、二维码文本复制按钮和解析结果。使用 accessible role/name 定位，不依赖 CSS class。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/views/CategoryAdminView.spec.ts src/views/LocationAdminView.spec.ts`

Expected: FAIL，视图不存在。

- [ ] **Step 3：实现响应式树管理**

复用 `StPageHeader`、`StEmptyState`、`StFormActions`。移动端树节点采用可展开列表，桌面端左树右编辑区；每页只保留一个主操作。属性类型使用下拉菜单，必填使用 checkbox，顺序使用 number input；状态不能只靠颜色。二维码区域展示可复制的 `stocket:location:<publicCode>`，本阶段不引入摄像头或二维码图片生成库。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/views/CategoryAdminView.spec.ts src/views/LocationAdminView.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/components/catalog frontend/src/components/location frontend/src/views frontend/src/App.vue
git diff --cached --check
git commit -m "feat(frontend): 实现分类位置管理界面"
```

## Task 10：实现物品表单、详情和目录搜索界面

**Files:**
- Create: `frontend/src/components/catalog/{ItemForm,ItemSearchResults}.vue`
- Create: `frontend/src/views/{ItemsView,ItemDetailView}.vue`
- Create: `frontend/src/views/{ItemsView,ItemDetailView}.spec.ts`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1：写关键用户流程失败测试**

覆盖名称搜索、条码精确结果标识、无结果状态、创建物品、按分类模式动态渲染 TEXT/NUMBER/BOOLEAN/DATE/ENUM、前端字段错误、重复条码 problem、版本冲突后保留用户输入、归档确认和只读模式。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/views/ItemsView.spec.ts src/views/ItemDetailView.spec.ts`

Expected: FAIL，物品视图不存在。

- [ ] **Step 3：实现业务界面**

`ItemsView` 顶部为紧凑搜索框，结果行依次显示名称、分类路径、品牌/规格、标签；条码精确命中显示文字“条码精确匹配”。`ItemForm` 由选中分类的模式生成控件，切换分类时保留同 key 且新模式兼容的值，移除不兼容值前要求确认。条码和标签使用可移除列表，Enter 添加，重复值在提交前提示。

`ItemDetailView` 展示档案字段、完整分类路径、条码和标签；管理员/普通成员显示编辑与归档操作，只读成员不渲染写按钮。前端隐藏操作仅改善体验，后端仍是权限边界。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/views/ItemsView.spec.ts src/views/ItemDetailView.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/components/catalog frontend/src/views frontend/src/App.vue
git diff --cached --check
git commit -m "feat(frontend): 实现物品目录与搜索"
```

## Task 11：完成后端验收、原生兼容与文档收口

**Files:**
- Create: `backend/src/test/java/com/stocket/catalog/CatalogLocationAcceptanceTest.java`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`

- [ ] **Step 1：写阶段三验收测试**

单个测试流程完成：管理员创建分类模式和位置层级；普通成员创建带标签、属性和条码的物品；只读成员按名称与精确条码检索；另一家庭无法读取；归档后默认搜索隐藏。每个写请求都带 CSRF 和真实阶段二会话。

- [ ] **Step 2：运行阶段验收测试**

Run: `cd backend && ./mvnw -Dtest=CatalogLocationAcceptanceTest test`

Expected: PASS。

- [ ] **Step 3：运行完整验证矩阵**

Run: `make test`

Expected: 后端与前端全套测试 PASS。

Run: `make build`

Expected: JVM 包与前端生产构建成功。

Run: `make aot`

Expected: Spring AOT 处理成功，无目录/位置反射提示。

Run: `make native-test`

Expected: GraalVM 原生测试 PASS；若本机没有 GraalVM 25，只记录环境阻塞，不把 JVM/AOT 结果描述为原生通过。

- [ ] **Step 4：更新文档**

README 增加目录与位置 API 能力、角色权限和搜索行为；路线图阶段三增加本计划链接并标记实际验收日期，不提前标记阶段四。记录精确命令及结果，不粘贴临时端口、Cookie 或令牌。

- [ ] **Step 5：提交阶段三收口**

```bash
git add backend/src/test/java/com/stocket/catalog/CatalogLocationAcceptanceTest.java README.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "docs: 记录阶段三目录位置验收"
```

## 最终验收清单

- [ ] 分类树和位置树均阻止循环、同级活跃重名与跨家庭访问。
- [ ] 分类属性模式覆盖五种类型，模式变更不会使已有活跃物品失效。
- [ ] 管理员可维护分类和位置；普通成员可维护物品；只读成员只能查询。
- [ ] 条码在家庭内唯一，精确条码优先于模糊文本结果，并发冲突不会留下半成品。
- [ ] 位置二维码载荷稳定、可解析、不可替代认证，且不会泄露其他家庭位置。
- [ ] 归档数据默认不出现在树和搜索中，恢复规则保持父链完整。
- [ ] 搜索投影在物品写事务中同步更新，中文包含和 `pg_trgm` 相似度查询使用索引。
- [ ] 所有更新使用乐观版本，所有错误保持 `application/problem+json` 和稳定业务码。
- [ ] `catalog`、`location` 不访问其他模块的 `internal` 包，`ApplicationModules.verify()` 通过。
- [ ] 前端在手机和桌面布局下可用，键盘可完成搜索和表单，状态不只靠颜色表达。
- [ ] `make test`、`make build`、`make aot` 通过；`make native-test` 已通过或明确记录唯一环境阻塞。
- [ ] 阶段三未引入库存条目、流水、提醒、附件、摄像头扫码或离线写队列。
