# 阶段六：移动优先 PWA 工作流实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可安装、移动优先的任务首页、全局搜索、目录与位置浏览、四步入库向导、条码/位置码扫描、库存操作、提醒视图、七天 IndexedDB 草稿和离线应用壳。

**Architecture:** Vue Router 组织五个移动主导航和桌面管理路由；PWA 只缓存带版本的静态应用壳，业务查询使用在线优先且不把认证 API 响应写入 Cache Storage。入库向导由纯状态机驱动并通过 `DraftRepository` 持久化到 IndexedDB；扫码通过 `Scanner` 接口隔离 ZXing 和摄像头，测试使用确定性 fake。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Vue Router 4、Element Plus 2.14、Vite 8、vite-plugin-pwa、IndexedDB/idb、ZXing Browser、Vitest 4、Testing Library、Playwright、Service Worker

---

## 执行约束

- 前置条件：阶段二至五 API 已完成，阶段三已有目录/位置客户端，阶段四已有库存命令，阶段五已有提醒接口。
- 离线只允许打开应用壳、查看当前会话内已经加载的非敏感摘要和编辑草稿；所有库存写命令在线提交，不实现 Background Sync、离线写队列或自动冲突合并。
- 草稿按 `accountId + draftId` 隔离，默认七天过期；登出、账号切换和会话失效必须清除该账号草稿、内存查询和 scanner 状态。
- Cache Storage 不保存 `/api/**`、附件、会话响应、Problem Detail 或通知密钥。Service Worker 更新不得静默丢失未提交草稿。
- 浏览器摄像头只在 HTTPS/localhost、用户点击“扫描”后请求；停止、路由离开、页面隐藏和组件卸载都必须释放 MediaStream track。
- 所有移动固定栏使用安全区 inset，并为正文预留稳定高度；320px 宽度下无横向滚动、无文字遮挡。桌面复用同一业务组件，不维护第二套逻辑。
- 数量仍以十进制字符串传递；每次明确提交意图生成幂等键，网络重试复用，编辑后重新生成。
- 本阶段不实现附件上传、CSV 导出、审计搜索或运维页面。

## 文件结构

```text
frontend/src/
├── router/index.ts
├── pwa/{registerServiceWorker,updateCoordinator}.ts
├── offline/{DraftRepository,QuerySnapshotStore,sessionCleanup}.ts
├── scanner/{Scanner,BarcodeScanner,ZxingScanner,scanPayload}.ts
├── receive/{ReceiveDraft,ReceiveWizardState,useReceiveWizard}.ts
├── components/navigation/{MobileTabBar,DesktopSidebar}.vue
├── components/scanner/ScannerSheet.vue
├── components/receive/{IdentifyStep,MatchStep,DetailsStep,ConfirmStep}.vue
└── views/{HomeView,ItemsView,ReceiveWizardView,RemindersView,ProfileView}.vue

frontend/e2e/
├── mobile-receive.spec.ts
├── offline-draft.spec.ts
└── responsive-shell.spec.ts
```

## Task 1：建立路由、PWA 依赖和可安装应用壳

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/pwa/{registerServiceWorker,updateCoordinator}.ts`
- Create: `frontend/src/components/navigation/{MobileTabBar,DesktopSidebar}.vue`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/AppShell.spec.ts`

- [ ] **Step 1：安装并锁定依赖**

执行时使用 `npm install --save-exact` 锁定 Vue Router、`idb`、`@zxing/browser`，使用 `npm install --save-dev --save-exact` 锁定 `vite-plugin-pwa` 和 `@playwright/test`；提交 `package-lock.json`，不得使用 `^`、`~` 或 `latest`。

- [ ] **Step 2：写应用壳失败测试**

断言手机显示“首页/物品/入库/提醒/我的”五个导航项，桌面显示侧栏，中央入库按钮语义名称唯一，当前路由有 `aria-current=page`，未登录路由重定向登录，强制改密状态只能进入改密页。

Run: `cd frontend && npm test -- src/AppShell.spec.ts`

Expected: FAIL，router 和新导航不存在。

- [ ] **Step 3：配置 PWA**

manifest 固定 `name=Stocket`、`short_name=Stocket`、`display=standalone`、`start_url=/`、主题色和背景色来自已批准设计 token。Workbox 仅 precache 构建产物，对 `/api/` 使用 `NetworkOnly`，导航回退到 `index.html`。更新协调器检测 waiting worker 后显示非阻断更新操作；刷新前先等待 DraftRepository 当前写入完成。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/AppShell.spec.ts && npm run typecheck && npm run build`

Expected: PASS，构建产物包含 manifest 和 service worker。

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/src
git diff --cached --check
git commit -m "feat(pwa): 建立可安装移动应用壳"
```

## Task 2：实现七天草稿、会话清理和在线写入门禁

**Files:**
- Create: `frontend/src/offline/{DraftRepository,IndexedDbDraftRepository,MemoryDraftRepository,sessionCleanup,onlineGuard}.ts`
- Create: `frontend/src/offline/{DraftRepository.spec,sessionCleanup.spec,onlineGuard.spec}.ts`
- Modify: `frontend/src/auth/useAuth.ts`
- Modify: `frontend/src/api/http.ts`

- [ ] **Step 1：写存储和清理失败测试**

用 fake IndexedDB 覆盖保存/读取、账号隔离、七天边界、过期清理、登出清理当前账号但不删其他账号、401 会话失效清理、并发保存以较新 `updatedAt` 胜出。在线门禁在 `navigator.onLine=false` 时阻止 mutation，但允许草稿保存。

Run: `cd frontend && npm test -- src/offline`

Expected: FAIL，草稿仓库不存在。

- [ ] **Step 2：实现草稿契约**

```ts
export interface DraftRepository<T> {
  save(accountId: string, draft: T & { id: string; updatedAt: string }): Promise<void>
  get(accountId: string, draftId: string): Promise<T | undefined>
  list(accountId: string): Promise<T[]>
  delete(accountId: string, draftId: string): Promise<void>
  clearAccount(accountId: string): Promise<void>
  purgeExpired(now: Date): Promise<number>
}
```

数据库名 `stocket-drafts-v1`，store key 为 `[accountId,id]`，索引为 `expiresAt`。保存时 `expiresAt=updatedAt+7d`。`http.ts` 只对标记为 mutation 的请求应用在线门禁并返回稳定客户端错误 `OFFLINE_WRITE_BLOCKED`。

- [ ] **Step 3：验证并提交**

Run: `cd frontend && npm test -- src/offline && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/offline frontend/src/auth/useAuth.ts frontend/src/api/http.ts
git diff --cached --check
git commit -m "feat(pwa): 添加离线草稿与会话清理"
```

## Task 3：实现可替换的条码与位置码扫描器

**Files:**
- Create: `frontend/src/scanner/{Scanner,ZxingScanner,FakeScanner,scanPayload}.ts`
- Create: `frontend/src/scanner/{scanPayload.spec,ZxingScanner.spec}.ts`
- Create: `frontend/src/components/scanner/ScannerSheet.vue`
- Create: `frontend/src/components/scanner/ScannerSheet.spec.ts`

- [ ] **Step 1：写解析和生命周期失败测试**

覆盖商品条码 trim/uppercase、`stocket:location:<code>` 解析、未知载荷、重复帧 1500ms 去抖、权限拒绝、无摄像头、切换前后摄像头、关闭 sheet 后所有 track stopped、页面 hidden 自动停止。

Run: `cd frontend && npm test -- src/scanner src/components/scanner/ScannerSheet.spec.ts`

Expected: FAIL，scanner 不存在。

- [ ] **Step 2：实现接口和 ZXing 适配器**

```ts
export type ScanResult =
  | { kind: 'PRODUCT_BARCODE'; value: string }
  | { kind: 'LOCATION_CODE'; value: string }

export interface Scanner {
  start(video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void>
  stop(): Promise<void>
}
```

`ZxingScanner` 集中拥有 reader 和 MediaStream，不把库类型泄露给组件。ScannerSheet 提供相机选择、手电筒能力存在时的 icon toggle、手工输入后备入口和明确的权限错误；不绘制自定义 SVG 图标。

- [ ] **Step 3：验证并提交**

Run: `cd frontend && npm test -- src/scanner src/components/scanner/ScannerSheet.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/scanner frontend/src/components/scanner
git diff --cached --check
git commit -m "feat(pwa): 添加条码与位置码扫描"
```

## Task 4：实现四步入库向导状态机

**Files:**
- Create: `frontend/src/receive/{ReceiveDraft,ReceiveWizardState,useReceiveWizard}.ts`
- Create: `frontend/src/receive/useReceiveWizard.spec.ts`
- Create: `frontend/src/components/receive/{IdentifyStep,MatchStep,DetailsStep,ConfirmStep,WizardProgress}.vue`
- Create: `frontend/src/views/ReceiveWizardView.vue`
- Create: `frontend/src/views/ReceiveWizardView.spec.ts`

- [ ] **Step 1：写状态机失败测试**

覆盖四步顺序、扫码命中已有物品、未命中进入快速建档、位置码填入位置、分类默认 BATCH/ASSET、目录默认保质期、返回不丢字段、每次变更 300ms 自动保存、恢复草稿、提交预览、离线阻止提交、版本冲突保留草稿、成功后删除草稿。

Run: `cd frontend && npm test -- src/receive src/views/ReceiveWizardView.spec.ts`

Expected: FAIL，向导不存在。

- [ ] **Step 2：实现判别联合状态**

状态固定为 `IDENTIFY | MATCH | DETAILS | CONFIRM | SUBMITTING | CONFLICT | COMPLETED`；每个转换函数返回新状态，不由组件直接修改步骤号。`ReceiveDraft` 保存物品/位置 ID 和各自版本、表单值、最近扫码结果、创建时间、更新时间；不保存 session、CSRF 或附件二进制。

- [ ] **Step 3：实现提交协议**

确认页展示“当前库存 + 本次变化 = 提交后库存”、目标位置和计算过期日。提交前在线重新读取物品与位置；版本失效进入 CONFLICT 并指出变更字段。调用阶段四 receipts API 时数量保持字符串，同一提交尝试复用幂等键。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/receive src/views/ReceiveWizardView.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src/receive frontend/src/components/receive frontend/src/views/ReceiveWizardView.vue frontend/src/views/ReceiveWizardView.spec.ts
git diff --cached --check
git commit -m "feat(pwa): 实现四步入库向导"
```

## Task 5：实现任务首页、全局搜索和物品位置浏览

**Files:**
- Create: `frontend/src/api/dashboard.ts`
- Create: `frontend/src/dashboard/{DashboardSummary,useGlobalSearch}.ts`
- Create: `frontend/src/components/home/{TaskSummary,QuickReceive,AttentionList}.vue`
- Create: `frontend/src/components/search/GlobalSearch.vue`
- Create: `frontend/src/views/{HomeView,ItemsView}.vue`
- Create: `frontend/src/views/{HomeView,ItemsView}.spec.ts`

- [ ] **Step 1：写首页和搜索失败测试**

断言首页顺序为搜索、快捷入库、30 天内到期、已过期、低库存、待处理项目；搜索精确条码优先并展示总量/位置/最近批次/最早过期；物品页可切换分类和位置视图；空态、加载态、错误态尺寸稳定；只读成员仍可查询。

- [ ] **Step 2：补充聚合查询 API**

若阶段三搜索结果尚无库存字段，在后端新增只读 `GET /api/v1/dashboard` 与搜索组合查询适配器，由 catalog 调用公开 `InventoryQuery` 和 reminder summary，禁止前端对每个搜索结果发 N+1 请求。为组合查询新增 `DashboardApiIntegrationTest`，验证家庭隔离和单页 SQL 数量上限。

- [ ] **Step 3：实现页面**

首页在 360px 宽度首屏展示搜索和快捷入库，并露出第一组提醒摘要。全局搜索 250ms 防抖、取消旧请求；精确条码立即查询。列表项保持固定图像占位尺寸，阶段七附件落地前使用设计系统图标占位。

- [ ] **Step 4：验证并提交**

Run: `cd frontend && npm test -- src/views/HomeView.spec.ts src/views/ItemsView.spec.ts && npm run typecheck`

Run: `cd backend && ./mvnw -Dtest=DashboardApiIntegrationTest test`

Expected: PASS。

```bash
git add frontend/src backend/src/main/java/com/stocket backend/src/test/java/com/stocket
git diff --cached --check
git commit -m "feat(pwa): 实现任务首页与全局搜索"
```

## Task 6：整合库存操作、提醒和个人页面

**Files:**
- Create: `frontend/src/components/inventory/{ConsumeSheet,TransferSheet,AdjustSheet}.vue`
- Create: `frontend/src/views/{InventoryEntryView,RemindersView,ProfileView}.vue`
- Create: `frontend/src/views/{InventoryEntryView,RemindersView,ProfileView}.spec.ts`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1：写移动工作流失败测试**

覆盖最早过期批次推荐但允许改选、消耗数量不足冲突、扫描目标位置后调拨、调整原因必填、提醒确认、个人页退出后清草稿、VIEWER 不渲染库存写按钮、后端 403 仍正确显示。

- [ ] **Step 2：实现紧凑操作界面**

消耗/调拨/调整用 bottom sheet，主操作固定单一且带图标；数量输入使用 decimal inputmode，不转 number。提醒页复用阶段五 API，个人页包含账号、会话和通知设置入口。路由 meta 同时声明认证状态和角色，仅用于 UX，服务端仍是权限边界。

- [ ] **Step 3：验证并提交**

Run: `cd frontend && npm test -- src/views/InventoryEntryView.spec.ts src/views/RemindersView.spec.ts src/views/ProfileView.spec.ts && npm run typecheck`

Expected: PASS。

```bash
git add frontend/src
git diff --cached --check
git commit -m "feat(pwa): 整合移动库存与提醒操作"
```

## Task 7：建立 Playwright 移动端、离线和可访问性验收

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/{mobile-receive,offline-draft,responsive-shell,scanner-simulation}.spec.ts`
- Modify: `frontend/package.json`
- Create: `backend/src/test/java/com/stocket/pwa/PwaWorkflowAcceptanceTest.java`

- [ ] **Step 1：配置确定性 E2E 环境**

增加 `test:e2e` 脚本；Playwright 使用 390x844 手机和 1440x900 桌面两个 project。测试通过 API fixture 初始化家庭/目录/位置，使用 fake scanner 注入条码，不依赖真实摄像头或公网。

- [ ] **Step 2：实现验收场景**

`mobile-receive` 从首页进入向导并在 30 秒预算内完成已有物品入库；`offline-draft` 在第三步断网、刷新、恢复草稿并阻止提交，联网后成功；`responsive-shell` 检查 320/390/768/1440 宽度无横向滚动和元素重叠；`scanner-simulation` 覆盖商品码和位置码。

- [ ] **Step 3：运行自动验收**

Run: `cd frontend && npm run test:e2e`

Expected: 四组 E2E 在 mobile/desktop 适用 project 中 PASS，无失败截图。

Run: `cd backend && ./mvnw -Dtest=PwaWorkflowAcceptanceTest test`

Expected: PASS，组合 API 和权限满足移动工作流。

- [ ] **Step 4：提交 E2E 边界**

```bash
git add frontend/playwright.config.ts frontend/e2e frontend/package.json frontend/package-lock.json backend/src/test/java/com/stocket/pwa
git diff --cached --check
git commit -m "test(pwa): 覆盖移动端离线工作流"
```

## Task 8：完成构建、真实设备检查和阶段收口

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-11-delivery-roadmap.md`
- Create: `docs/operations/pwa-device-verification.md`

- [ ] **Step 1：运行完整验证矩阵**

Run: `make test && make build`

Expected: JVM、前端和全部单元/集成测试 PASS。

Run: `cd frontend && npm run test:e2e`

Expected: Playwright PASS。

- [ ] **Step 2：执行 HTTPS 真实设备检查**

在一台真实手机记录浏览器/系统、安装结果、摄像头授权、商品码识别、位置码识别、页面隐藏释放摄像头、断网草稿恢复和安全区布局。文档只记录结果和问题编号，不保存真实家庭数据、条码照片或 Cookie。

- [ ] **Step 3：更新文档并提交**

README 增加 PWA 安装、离线边界和浏览器要求；路线图阶段六添加本计划链接与验收日期。

```bash
git add README.md docs/operations/pwa-device-verification.md docs/superpowers/plans/2026-07-11-delivery-roadmap.md
git diff --cached --check
git commit -m "feat: 完成阶段六移动 PWA 工作流"
```

## 最终验收清单

- [ ] 手机端已有物品入库在 30 秒预算内可完成。
- [ ] 四步向导可返回、自动保存、恢复、冲突保留和成功清理。
- [ ] 离线时应用壳可打开、草稿可编辑，任何库存写入均被阻止。
- [ ] 登出、会话失效和账号切换清除当前账号客户端数据。
- [ ] 扫描器正确释放摄像头并有手工输入后备路径。
- [ ] 首页、搜索、目录、位置、库存、提醒和个人页在移动/桌面均可用。
- [ ] Cache Storage 不包含认证 API 或敏感响应。
- [ ] 单元、类型、构建、Playwright 和真实设备检查均完成。
- [ ] 阶段六未实现附件、导出、审计搜索或离线库存写队列。
