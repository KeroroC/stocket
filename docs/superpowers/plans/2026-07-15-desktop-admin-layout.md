# 桌面后台布局优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ≥1024px 视口把 Stocket 做成标准后台壳（分组侧栏 + 顶栏 + 主内容），并让全部业务/管理页具备桌面后台布局；移动端保持现状。

**Architecture:** 继续走方案 A：增强现有 `PwaAppShell` / `DesktopSidebar`，新增 `DesktopTopBar` 与共享导航配置；页面通过桌面断点 CSS 与少量结构 class 提升信息密度，不引入第二套路由或 Element Plus Layout 体系。严格红—绿—重构，移动底栏与现有路由权限 meta 不动。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Vue Router 4、Element Plus Icons、CSS 自定义属性（mint cream tokens）、Vitest 4、Testing Library Vue、Playwright

**Spec:** `docs/superpowers/specs/2026-07-15-desktop-admin-layout-design.md`

---

## 执行约束

- 优先在独立 worktree（如 `codex/desktop-admin-layout`）执行；若用户要求直接在当前分支改，需在任务开始前明确。
- 不修改后端 API、权限模型或业务规则。
- 不改移动底栏五项入口与移动主路径。
- 不引入 Tailwind、深色主题、新 UI 框架依赖。
- 每次提交只暂存本任务文件，先 `git diff --cached --check`。
- 提交信息使用中文或英文均可，保持仓库现有风格（`feat(frontend): ...` / `test(frontend): ...`）。

## 文件结构

```text
frontend/src/
├── components/
│   ├── PwaAppShell.vue                      # 组装侧栏/顶栏/主内容/底栏
│   ├── navigation/
│   │   ├── navConfig.ts                     # 分组导航与角色过滤（单一数据源）
│   │   ├── DesktopSidebar.vue               # 分组侧栏
│   │   ├── DesktopTopBar.vue                # 顶栏：搜索/快捷入库/账户菜单
│   │   └── MobileTabBar.vue                 # 不变
│   ├── search/GlobalSearch.vue              # 支持 compact 变体供顶栏
│   ├── catalog/ItemSearchResults.vue        # 桌面表格变体
│   ├── inventory/InventoryEntryList.vue     # 桌面表格变体
│   └── home/AttentionList.vue / TaskSummary # 桌面统计横排强化
├── styles/
│   ├── main.css                             # pwa-shell / sidebar / topbar 断点
│   └── pages.css                            # 各业务页桌面布局
├── views/*                                  # 管理页/业务页 class 与结构微调
└── AppShell.spec.ts                         # 外壳与导航回归
frontend/e2e/responsive-shell.spec.ts        # 桌面顶栏与分组可见性
```

---

### Task 1: 共享导航配置 + 分组侧栏

**Files:**
- Create: `frontend/src/components/navigation/navConfig.ts`
- Modify: `frontend/src/components/navigation/DesktopSidebar.vue`
- Modify: `frontend/src/AppShell.spec.ts`
- Test: `frontend/src/AppShell.spec.ts`

- [ ] **Step 1: 扩展失败测试（分组、角色过滤、入口完整性）**

在 `frontend/src/AppShell.spec.ts` 的桌面侧栏相关 describe 中新增/改写：

```ts
it('桌面侧栏按业务分组，并按角色过滤入库与系统管理', async () => {
  const member = {
    id: 'account-1',
    username: 'member',
    displayName: '家庭成员',
    role: 'MEMBER',
  }
  const authState = ref<AuthState>({ kind: 'authenticated', account: member })
  const router = createStocketRouter(authState, createMemoryHistory())
  await router.push('/')
  await router.isReady()

  const { unmount } = render(DesktopSidebar, {
    props: { account: member },
    global: { plugins: [router] },
  })

  const navigation = screen.getByRole('navigation', { name: '桌面主导航' })
  expect(within(navigation).getByText('概览')).toBeInTheDocument()
  expect(within(navigation).getByText('库存业务')).toBeInTheDocument()
  expect(within(navigation).getByText('个人')).toBeInTheDocument()
  expect(within(navigation).queryByText('系统管理')).not.toBeInTheDocument()
  expect(within(navigation).getByRole('link', { name: '入库' })).toBeInTheDocument()
  expect(within(navigation).getByRole('link', { name: '库存台账' })).toHaveAttribute('href', '/inventory')
  expect(within(navigation).getByRole('link', { name: '通知设置' })).toHaveAttribute('href', '/notification-settings')
  expect(within(navigation).queryByRole('link', { name: '成员管理' })).not.toBeInTheDocument()
  unmount()

  const admin = { ...member, role: 'ADMIN', displayName: '管理员' }
  const adminRouter = createStocketRouter(ref({ kind: 'authenticated', account: admin }), createMemoryHistory())
  await adminRouter.push('/')
  await adminRouter.isReady()
  render(DesktopSidebar, { props: { account: admin }, global: { plugins: [adminRouter] } })
  const adminNav = screen.getByRole('navigation', { name: '桌面主导航' })
  expect(within(adminNav).getByText('系统管理')).toBeInTheDocument()
  for (const label of ['成员管理', '邀请管理', '分类管理', '位置管理', '通知失败', '审计日志', '系统诊断']) {
    expect(within(adminNav).getByRole('link', { name: label })).toBeInTheDocument()
  }
})

it('VIEWER 不显示入库入口', async () => {
  const viewer = {
    id: 'v1',
    username: 'viewer',
    displayName: '只读',
    role: 'VIEWER',
  }
  const router = createStocketRouter(ref({ kind: 'authenticated', account: viewer }), createMemoryHistory())
  await router.push('/')
  await router.isReady()
  render(DesktopSidebar, { props: { account: viewer }, global: { plugins: [router] } })
  expect(screen.queryByRole('link', { name: '入库' })).not.toBeInTheDocument()
})
```

同步更新既有用例中侧栏标签：
- 首页链接文案可为「首页」
- 物品链接文案改为「物品目录」（与规格一致）
- 提醒链接文案改为「提醒中心」
- 「我的」改为「我的账户」

若旧测试写死「物品」「提醒」「我的」，一并改成新文案。

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd frontend && npm test -- src/AppShell.spec.ts
```

Expected: FAIL（找不到分组标题或新文案/库存台账链接）

- [ ] **Step 3: 实现 `navConfig.ts`**

```ts
import {
  Bell,
  Box,
  DocumentChecked,
  FirstAidKit,
  HomeFilled,
  Location,
  Plus,
  Setting,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

export type NavItem = {
  to: string
  label: string
  icon: Component
  roles?: string[]
}

export type NavGroup = {
  id: string
  label: string
  items: NavItem[]
}

export const desktopNavGroups: NavGroup[] = [
  {
    id: 'overview',
    label: '概览',
    items: [{ to: '/', label: '首页', icon: HomeFilled }],
  },
  {
    id: 'inventory',
    label: '库存业务',
    items: [
      { to: '/items', label: '物品目录', icon: Box },
      { to: '/receive', label: '入库', icon: Plus, roles: ['ADMIN', 'MEMBER'] },
      { to: '/inventory', label: '库存台账', icon: Location },
      { to: '/reminders', label: '提醒中心', icon: Bell },
    ],
  },
  {
    id: 'admin',
    label: '系统管理',
    items: [
      { to: '/admin/members', label: '成员管理', icon: UserFilled, roles: ['ADMIN'] },
      { to: '/admin/invites', label: '邀请管理', icon: Plus, roles: ['ADMIN'] },
      { to: '/admin/categories', label: '分类管理', icon: Box, roles: ['ADMIN'] },
      { to: '/admin/locations', label: '位置管理', icon: HomeFilled, roles: ['ADMIN'] },
      { to: '/admin/delivery-failures', label: '通知失败', icon: Bell, roles: ['ADMIN'] },
      { to: '/admin/audit-logs', label: '审计日志', icon: DocumentChecked, roles: ['ADMIN'] },
      { to: '/admin/diagnostics', label: '系统诊断', icon: FirstAidKit, roles: ['ADMIN'] },
    ],
  },
  {
    id: 'personal',
    label: '个人',
    items: [
      { to: '/profile', label: '我的账户', icon: User },
      { to: '/notification-settings', label: '通知设置', icon: Setting },
    ],
  },
]

export function visibleNavGroups(role: string): NavGroup[] {
  return desktopNavGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.roles || item.roles.includes(role)),
    }))
    .filter((group) => group.items.length > 0)
}
```

若项目中 `Location` / `Setting` / `UserFilled` 图标导入名与 `@element-plus/icons-vue` 实际导出不一致，以包内真实导出为准（可用 `rg "export.*Location" node_modules/@element-plus/icons-vue` 核对），保持语义等价即可。

- [ ] **Step 4: 重写 `DesktopSidebar.vue`**

```vue
<script setup lang="ts">
import type { CurrentAccount } from '../../auth/AuthState'
import { visibleNavGroups } from './navConfig'

const props = defineProps<{ account: CurrentAccount }>()
const groups = visibleNavGroups(props.account.role)
</script>

<template>
  <aside class="desktop-sidebar">
    <div class="desktop-sidebar__brand">Stocket</div>
    <nav aria-label="桌面主导航">
      <section v-for="group in groups" :key="group.id" class="desktop-sidebar__group">
        <h2 class="desktop-sidebar__group-label">{{ group.label }}</h2>
        <RouterLink
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          class="desktop-sidebar__link"
        >
          <component :is="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </section>
    </nav>
  </aside>
</template>
```

说明：账户信息与退出移到顶栏（Task 2）。若暂时仍保留 logout emit 以兼容，可不在模板渲染退出按钮，但 props/emit 可先删净并修调用方。

- [ ] **Step 5: 补充侧栏分组样式（`main.css` ≥1024 块）**

在现有 `.desktop-sidebar` 桌面样式中增加：

```css
.desktop-sidebar__group {
  display: grid;
  gap: var(--st-space-1);
}

.desktop-sidebar__group-label {
  margin: var(--st-space-3) 0 var(--st-space-1);
  padding: 0 var(--st-space-3);
  color: var(--st-color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.desktop-sidebar__group:first-child .desktop-sidebar__group-label {
  margin-top: 0;
}
```

移除对 `.desktop-sidebar__account` / `.desktop-sidebar__logout` 的依赖（若顶栏尚未就位，可暂时保留 class 但模板不再使用）。

- [ ] **Step 6: 跑测试至通过**

Run:

```bash
cd frontend && npm test -- src/AppShell.spec.ts
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/navigation/navConfig.ts \
  frontend/src/components/navigation/DesktopSidebar.vue \
  frontend/src/styles/main.css \
  frontend/src/AppShell.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 桌面侧栏按业务分组并按角色过滤"
```

---

### Task 2: DesktopTopBar + GlobalSearch compact

**Files:**
- Create: `frontend/src/components/navigation/DesktopTopBar.vue`
- Create: `frontend/src/components/navigation/DesktopTopBar.spec.ts`
- Modify: `frontend/src/components/search/GlobalSearch.vue`
- Modify: `frontend/src/styles/main.css`

- [ ] **Step 1: 写顶栏失败测试**

创建 `DesktopTopBar.spec.ts`：

```ts
import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue'
import { createMemoryHistory } from 'vue-router'
import { ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthState } from '../../auth/AuthState'
import { createStocketRouter } from '../../router'
import DesktopTopBar from './DesktopTopBar.vue'

afterEach(cleanup)

const member = {
  id: 'a1',
  username: 'member',
  displayName: '家庭成员',
  role: 'MEMBER',
}

async function renderTopBar(account = member) {
  const authState = ref<AuthState>({ kind: 'authenticated', account })
  const router = createStocketRouter(authState, createMemoryHistory())
  await router.push('/')
  await router.isReady()
  return render(DesktopTopBar, {
    props: { account },
    global: { plugins: [router] },
  })
}

describe('DesktopTopBar', () => {
  it('展示全局搜索、快捷入库与账户菜单，并可退出', async () => {
    const { emitted } = await renderTopBar()
    expect(screen.getByRole('searchbox', { name: '全局搜索' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '快捷入库' })).toHaveAttribute('href', '/receive')

    await fireEvent.click(screen.getByRole('button', { name: /家庭成员/ }))
    const menu = screen.getByRole('menu')
    expect(within(menu).getByRole('menuitem', { name: '我的账户' })).toHaveAttribute('href', '/profile')
    expect(within(menu).getByRole('menuitem', { name: '通知设置' })).toHaveAttribute('href', '/notification-settings')
    await fireEvent.click(within(menu).getByRole('menuitem', { name: '退出登录' }))
    expect(emitted().logout).toBeTruthy()
  })

  it('VIEWER 不显示快捷入库', async () => {
    await renderTopBar({ ...member, role: 'VIEWER', displayName: '只读成员' })
    expect(screen.queryByRole('link', { name: '快捷入库' })).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run:

```bash
cd frontend && npm test -- src/components/navigation/DesktopTopBar.spec.ts
```

Expected: FAIL（模块不存在）

- [ ] **Step 3: GlobalSearch 支持 compact**

```vue
<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { useGlobalSearch } from '../../dashboard/useGlobalSearch'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
const search = useGlobalSearch()
</script>

<template>
  <section class="global-search" :class="{ 'global-search--compact': compact }" aria-label="全局搜索">
    <label class="global-search__field">
      <Search aria-hidden="true" />
      <span class="sr-only">全局搜索</span>
      <input
        v-model="search.query.value"
        type="search"
        aria-label="全局搜索"
        placeholder="搜索名称、条码或位置"
      />
    </label>
    <p v-if="search.loading.value" class="global-search__status">搜索中…</p>
    <p v-if="search.error.value" class="global-search__status global-search__status--error" role="alert">
      {{ search.error.value }}
    </p>
    <ul v-if="search.results.value.length" class="global-search__results">
      <li v-for="item in search.results.value" :key="item.id">
        <strong>{{ item.name }}</strong>
        <span>总量 {{ item.totalAvailable }}</span>
        <span>位置 {{ item.locations.join('、') || '未设置' }}</span>
        <span>最近批次 {{ item.recentBatch ?? '无' }}</span>
        <span>最早过期 {{ item.earliestExpiration ?? '无' }}</span>
      </li>
    </ul>
  </section>
</template>
```

- [ ] **Step 4: 实现 `DesktopTopBar.vue`**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { CurrentAccount } from '../../auth/AuthState'
import GlobalSearch from '../search/GlobalSearch.vue'

const props = defineProps<{ account: CurrentAccount }>()
const emit = defineEmits<{ logout: [] }>()

const menuOpen = ref(false)
const canReceive = ['ADMIN', 'MEMBER'].includes(props.account.role)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function onDocumentClick(event: MouseEvent) {
  const target = event.target as Node | null
  const root = document.querySelector('.desktop-topbar__account')
  if (root && target && !root.contains(target)) closeMenu()
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <header class="desktop-topbar">
    <div class="desktop-topbar__search">
      <GlobalSearch compact />
    </div>
    <div class="desktop-topbar__actions">
      <RouterLink v-if="canReceive" class="st-button st-button--primary" to="/receive">快捷入库</RouterLink>
      <div class="desktop-topbar__account">
        <button
          type="button"
          class="desktop-topbar__account-btn"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          :aria-label="`${account.displayName} 账户菜单`"
          @click.stop="toggleMenu"
        >
          <strong>{{ account.displayName }}</strong>
          <span>{{ account.role }}</span>
        </button>
        <div v-if="menuOpen" class="desktop-topbar__menu" role="menu">
          <RouterLink role="menuitem" to="/profile" @click="closeMenu">我的账户</RouterLink>
          <RouterLink role="menuitem" to="/notification-settings" @click="closeMenu">通知设置</RouterLink>
          <button role="menuitem" type="button" @click="emit('logout')">退出登录</button>
        </div>
      </div>
    </div>
  </header>
</template>
```

- [ ] **Step 5: 顶栏与 compact 搜索样式**

在 `main.css` 增加（默认隐藏，≥1024 显示）：

```css
.desktop-topbar {
  display: none;
}

@media (min-width: 1024px) {
  .pwa-shell {
    display: grid;
    grid-template-columns: 16rem minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    min-height: 100dvh;
  }

  .desktop-sidebar {
    grid-row: 1 / span 2;
  }

  .desktop-topbar {
    position: sticky;
    top: 0;
    z-index: 15;
    display: flex;
    align-items: center;
    gap: var(--st-space-4);
    min-height: 4rem;
    padding: var(--st-space-3) var(--st-space-6);
    border-bottom: 1px solid var(--st-color-border);
    background: color-mix(in srgb, var(--st-color-surface) 94%, transparent);
    backdrop-filter: blur(12px);
  }

  .desktop-topbar__search {
    flex: 1;
    min-width: 0;
    max-width: 36rem;
  }

  .desktop-topbar__actions {
    display: flex;
    align-items: center;
    gap: var(--st-space-3);
    margin-left: auto;
  }

  .desktop-topbar__account {
    position: relative;
  }

  .desktop-topbar__account-btn {
    display: grid;
    min-height: var(--st-control-min-size);
    min-width: 8rem;
    justify-items: start;
    gap: 2px;
    padding: 0.4rem 0.75rem;
    border: 1px solid var(--st-color-border);
    border-radius: var(--st-radius-control);
    background: var(--st-color-surface);
    color: var(--st-color-text);
    cursor: pointer;
  }

  .desktop-topbar__account-btn span {
    color: var(--st-color-text-muted);
    font-size: 0.75rem;
  }

  .desktop-topbar__menu {
    position: absolute;
    right: 0;
    top: calc(100% + 0.35rem);
    display: grid;
    min-width: 12rem;
    padding: var(--st-space-2);
    gap: var(--st-space-1);
    border: 1px solid var(--st-color-border);
    border-radius: var(--st-radius-card);
    background: var(--st-color-surface);
    box-shadow: var(--st-shadow-card);
  }

  .desktop-topbar__menu a,
  .desktop-topbar__menu button {
    display: flex;
    min-height: var(--st-control-min-size);
    align-items: center;
    padding: 0 var(--st-space-3);
    border: 0;
    border-radius: var(--st-radius-control);
    background: transparent;
    color: var(--st-color-text);
    text-decoration: none;
    cursor: pointer;
    text-align: left;
    font: inherit;
  }

  .desktop-topbar__menu a:hover,
  .desktop-topbar__menu button:hover {
    background: var(--st-color-primary-soft);
    color: var(--st-color-primary);
  }

  .global-search--compact .global-search__field {
    min-height: 2.75rem;
    box-shadow: none;
  }

  .global-search--compact .global-search__field input {
    min-height: 2.5rem;
  }
}
```

注意：与现有 `@media (min-width: 1024px)` 中 `.pwa-shell` 规则合并，避免重复冲突；侧栏宽度统一为 `16rem`（规格 15–16rem）。

- [ ] **Step 6: 跑测试**

Run:

```bash
cd frontend && npm test -- src/components/navigation/DesktopTopBar.spec.ts src/AppShell.spec.ts
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/navigation/DesktopTopBar.vue \
  frontend/src/components/navigation/DesktopTopBar.spec.ts \
  frontend/src/components/search/GlobalSearch.vue \
  frontend/src/styles/main.css
git diff --cached --check
git commit -m "feat(frontend): 新增桌面顶栏与紧凑全局搜索"
```

---

### Task 3: 组装 PwaAppShell 桌面网格

**Files:**
- Modify: `frontend/src/components/PwaAppShell.vue`
- Modify: `frontend/src/styles/main.css`
- Modify: `frontend/src/AppShell.spec.ts`（如需外壳集成断言）
- Modify: `frontend/e2e/responsive-shell.spec.ts`

- [ ] **Step 1: 写外壳集成失败测试**

在 `AppShell.spec.ts` 增加：

```ts
import PwaAppShell from './components/PwaAppShell.vue'

it('PwaAppShell 在路由模式下渲染桌面侧栏、顶栏和主内容地标', async () => {
  const account = {
    id: 'account-1',
    username: 'member',
    displayName: '家庭成员',
    role: 'MEMBER',
  }
  const authState = ref<AuthState>({ kind: 'authenticated', account })
  const router = createStocketRouter(authState, createMemoryHistory())
  await router.push('/')
  await router.isReady()

  render(PwaAppShell, {
    props: { account },
    global: { plugins: [router] },
  })

  expect(screen.getByRole('navigation', { name: '桌面主导航' })).toBeInTheDocument()
  expect(screen.getByRole('banner')).toBeInTheDocument()
  expect(document.getElementById('main-content')).not.toBeNull()
  expect(screen.getByRole('navigation', { name: '移动主导航' })).toBeInTheDocument()
})
```

`DesktopTopBar` 根节点需为 `<header class="desktop-topbar">` 以提供 `banner` 地标。

- [ ] **Step 2: 运行确认失败**

Run:

```bash
cd frontend && npm test -- src/AppShell.spec.ts
```

Expected: FAIL（缺少 banner 或 main-content）

- [ ] **Step 3: 更新 `PwaAppShell.vue`**

```vue
<script setup lang="ts">
import { inject } from 'vue'
import { routerKey } from 'vue-router'
import type { CurrentAccount } from '../auth/AuthState'
import DesktopSidebar from './navigation/DesktopSidebar.vue'
import DesktopTopBar from './navigation/DesktopTopBar.vue'
import MobileTabBar from './navigation/MobileTabBar.vue'
import LegacyAppShell from './AppShell.vue'

defineProps<{ account: CurrentAccount }>()

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

const hasRouter = Boolean(inject(routerKey, null))
</script>

<template>
  <LegacyAppShell
    v-if="!hasRouter"
    :account="account"
    @logout="emit('logout')"
    @force-password-change="emit('forcePasswordChange')"
  />
  <div v-else class="pwa-shell">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <DesktopSidebar :account="account" />
    <DesktopTopBar :account="account" @logout="emit('logout')" />
    <main id="main-content" class="pwa-shell__content" tabindex="-1">
      <RouterView v-slot="{ Component }">
        <component
          :is="Component"
          :account="account"
          :role="account.role"
          @logout="emit('logout')"
          @force-password-change="emit('forcePasswordChange')"
        />
      </RouterView>
    </main>
    <MobileTabBar />
  </div>
</template>
```

- [ ] **Step 4: skip-link 与桌面 content 样式**

```css
.skip-link {
  position: absolute;
  left: -999px;
  top: 0;
  z-index: 100;
  padding: 0.75rem 1rem;
  background: var(--st-color-primary);
  color: white;
}

.skip-link:focus {
  left: var(--st-space-4);
  top: var(--st-space-4);
}

@media (min-width: 1024px) {
  .pwa-shell__content {
    grid-column: 2;
    grid-row: 2;
    padding: var(--st-space-6);
    padding-bottom: var(--st-space-6);
    overflow: auto;
  }

  .mobile-tab-bar {
    display: none;
  }
}
```

确保桌面不再保留 `padding-bottom: calc(5.5rem + env(safe-area-inset-bottom))`。

- [ ] **Step 5: 更新 E2E 断言顶栏**

在 `frontend/e2e/responsive-shell.spec.ts` 桌面分支（width ≥ 1024）增加：

```ts
await expect(page.getByRole('navigation', { name: '桌面主导航' })).toBeVisible()
await expect(page.getByRole('banner')).toBeVisible()
await expect(page.getByRole('searchbox', { name: '全局搜索' })).toBeVisible()
```

首页测试仍应能找到全局搜索（顶栏）。若首页精简掉正文搜索，确保至少顶栏有一个。

- [ ] **Step 6: 单测通过**

Run:

```bash
cd frontend && npm test -- src/AppShell.spec.ts src/components/navigation/DesktopTopBar.spec.ts
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/PwaAppShell.vue \
  frontend/src/styles/main.css \
  frontend/src/AppShell.spec.ts \
  frontend/e2e/responsive-shell.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 组装桌面后台壳网格与 skip link"
```

---

### Task 4: 共享桌面表格/工具条样式 + 管理列表页

**Files:**
- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/views/AdminMembersView.vue`
- Modify: `frontend/src/views/AdminInvitesView.vue`
- Modify: `frontend/src/views/DeliveryFailuresView.vue`（已有 table，对齐工具条）
- Modify: `frontend/src/views/AuditLogView.vue`
- Modify: `frontend/src/views/AdminMembersView.spec.ts`（若断言 class/结构）
- Modify: `frontend/src/views/AdminInvitesView.spec.ts`

- [ ] **Step 1: 确认现有管理页测试基线**

Run:

```bash
cd frontend && npm test -- src/views/AdminMembersView.spec.ts src/views/AdminInvitesView.spec.ts
```

Expected: PASS（记录当前断言，后续改结构时保持行为断言优先）

- [ ] **Step 2: 增加共享桌面后台列表样式**

在 `pages.css` 追加：

```css
.st-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--st-space-3);
  align-items: center;
  margin-bottom: var(--st-space-4);
}

.st-table-wrapper {
  width: 100%;
  overflow-x: auto;
  border: 1px solid var(--st-color-border);
  border-radius: var(--st-radius-card);
  background: var(--st-color-surface);
}

.st-table {
  width: 100%;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

.st-table th,
.st-table td {
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--st-color-border);
  text-align: left;
  vertical-align: middle;
}

.st-table th {
  color: var(--st-color-text-muted);
  font-size: 0.8125rem;
  font-weight: 700;
  background: color-mix(in srgb, var(--st-color-bg) 80%, var(--st-color-surface));
}

.st-table tr:last-child td {
  border-bottom: 0;
}

@media (min-width: 1024px) {
  .admin-members-view,
  .admin-invites-view,
  .audit-page {
    width: min(100%, 75rem);
  }

  .member-list,
  .invite-list {
    display: none;
  }

  .admin-table-fallback {
    display: block;
  }
}

@media (max-width: 1023px) {
  .admin-table-fallback {
    display: none;
  }
}
```

策略：移动端保留现有 `member-list` 卡片列表；桌面显示 `st-table`。若双渲染成本可接受，用 CSS 显隐两套 DOM；也可只用一套 table 并在小屏变成堆叠行（优先双渲染以降低移动回归风险）。

- [ ] **Step 3: AdminMembersView 桌面表格**

在现有 `member-list` 旁增加桌面表格（字段：显示名、用户名、角色、状态、操作）。页头改为 `StPageHeader`：

```vue
<section class="st-page admin-members-view">
  <StPageHeader title="成员管理" description="管理家庭成员角色与访问状态">
    <template #actions>
      <button class="st-button st-button--primary" type="button" @click="openCreateDialog">创建成员</button>
    </template>
  </StPageHeader>
  <!-- error/loading -->
  <div v-if="members.length" class="st-table-wrapper admin-table-fallback">
    <table class="st-table">
      <thead>
        <tr>
          <th>显示名</th><th>用户名</th><th>角色</th><th>状态</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="member in members" :key="member.id">
          <td>{{ member.displayName }}</td>
          <td>@{{ member.username }}</td>
          <td>{{ member.role }}</td>
          <td>{{ member.enabled ? '启用' : '停用' }}</td>
          <td class="st-table__actions">
            <button class="st-button st-button--text" type="button" @click="openEditRoleDialog(member)">修改角色</button>
            <button class="st-button st-button--text" type="button" @click="handleToggleEnabled(member)">
              {{ member.enabled ? '停用' : '启用' }}
            </button>
            <button class="st-button st-button--text" type="button" @click="handleResetPassword(member)">重置密码</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <ul v-if="members.length" class="member-list">...</ul>
</section>
```

字段名以 `MemberInfo` 实际类型为准（`enabled`/`status` 等）。对话框逻辑保持不变。

- [ ] **Step 4: AdminInvitesView 同样模式**

页头 `StPageHeader` + 桌面表格列：邀请码/链接摘要、角色、过期时间、状态、操作。移动列表保留。

- [ ] **Step 5: AuditLogView 桌面表格化**

保留筛选表单；`≥1024px` 将 `audit-list` 卡片改为表格列：时间、事件、结果、操作人、对象、Request ID。可用 CSS 双模板或单一 table。实现时保持复制 Request ID 行为与既有测试。

- [ ] **Step 6: 跑管理页测试**

Run:

```bash
cd frontend && npm test -- src/views/AdminMembersView.spec.ts src/views/AdminInvitesView.spec.ts src/views/AuditLogView.spec.ts src/views/DeliveryFailuresView.spec.ts
```

Expected: PASS（必要时小幅更新选择器，不改业务断言语义）

- [ ] **Step 7: Commit**

```bash
git add frontend/src/styles/pages.css \
  frontend/src/views/AdminMembersView.vue \
  frontend/src/views/AdminInvitesView.vue \
  frontend/src/views/AuditLogView.vue \
  frontend/src/views/DeliveryFailuresView.vue \
  frontend/src/views/AdminMembersView.spec.ts \
  frontend/src/views/AdminInvitesView.spec.ts \
  frontend/src/views/AuditLogView.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 管理列表页桌面表格化"
```

---

### Task 5: 分类/位置双栏与诊断卡片对齐

**Files:**
- Modify: `frontend/src/views/CategoryAdminView.vue`
- Modify: `frontend/src/views/LocationAdminView.vue`
- Modify: `frontend/src/views/DiagnosticsView.vue`
- Modify: `frontend/src/styles/pages.css`
- Modify: 对应 `*.spec.ts`（若有结构断言）

- [ ] **Step 1: 跑基线测试**

```bash
cd frontend && npm test -- src/views/CategoryAdminView.spec.ts src/views/LocationAdminView.spec.ts src/views/DiagnosticsView.spec.ts
```

- [ ] **Step 2: 强化 admin-grid 桌面尺寸**

```css
@media (min-width: 1024px) {
  .admin-grid {
    display: grid;
    grid-template-columns: minmax(16rem, 0.8fr) minmax(22rem, 1.2fr);
    gap: var(--st-space-6);
    align-items: start;
  }

  .admin-detail,
  .admin-grid > form,
  .admin-grid > article {
    padding: var(--st-space-6);
    border: 1px solid var(--st-color-border);
    border-radius: var(--st-radius-card);
    background: var(--st-color-surface);
  }

  .diagnostics-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--st-space-4);
  }
}
```

- [ ] **Step 3: 确保分类/位置页使用 `st-page` + `StPageHeader` + `admin-grid`**

`CategoryAdminView` / `LocationAdminView` 已接近目标；补齐：
- 统一空态与错误反馈 class；
- 桌面下树区域可滚动 `max-height: calc(100dvh - 12rem)`。

- [ ] **Step 4: 测试通过并提交**

```bash
cd frontend && npm test -- src/views/CategoryAdminView.spec.ts src/views/LocationAdminView.spec.ts src/views/DiagnosticsView.spec.ts
git add frontend/src/views/CategoryAdminView.vue \
  frontend/src/views/LocationAdminView.vue \
  frontend/src/views/DiagnosticsView.vue \
  frontend/src/styles/pages.css
git diff --cached --check
git commit -m "feat(frontend): 强化分类位置诊断页桌面双栏布局"
```

---

### Task 6: 物品目录桌面（左树右表）

**Files:**
- Modify: `frontend/src/views/ItemsView.vue`
- Modify: `frontend/src/components/catalog/ItemSearchResults.vue`
- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/views/ItemsView.spec.ts`

- [ ] **Step 1: 扩展 Items 测试**

在 `ItemsView.spec.ts` 增加：

```ts
it('渲染分类/位置浏览与结果列表区域', async () => {
  // 既有 mock 基础上
  render(ItemsView, { props: { role: 'ADMIN' } })
  expect(screen.getByRole('heading', { name: '物品目录' })).toBeInTheDocument()
  expect(screen.getByRole('group', { name: '浏览方式' })).toBeInTheDocument()
  expect(screen.getByLabelText('搜索物品')).toBeInTheDocument()
})
```

- [ ] **Step 2: ItemsView 结构**

保持逻辑，模板调整为：

```vue
<section class="st-page catalog-page">
  <StPageHeader ... />
  <div class="catalog-page__toolbar st-toolbar">
    <div class="browse-switch" role="group" aria-label="浏览方式">...</div>
    <label class="search-label">搜索物品<input ... /></label>
  </div>
  <div class="catalog-page__workspace">
    <aside class="catalog-page__tree">
      <ul v-if="browseMode === 'category'" class="browse-list" aria-label="分类浏览">...</ul>
      <ul v-else class="browse-list" aria-label="位置浏览">...</ul>
    </aside>
    <div class="catalog-page__main">
      <ItemForm v-if="creating" ... />
      <ItemDetailView v-else-if="selectedId" ... />
      <ItemSearchResults v-else ... />
    </div>
  </div>
</section>
```

详情暂可继续页内切换；若成本可控，桌面可用简单 fixed 右侧抽屉包裹 `ItemDetailView`，但 **YAGNI**：先页内双栏，抽屉作为可选增强，不阻塞本任务。

- [ ] **Step 3: ItemSearchResults 桌面表格**

```vue
<template>
  <StEmptyState v-if="!loading && !items.length" ... />
  <div v-else-if="items.length" class="st-table-wrapper item-results-table">
    <table class="st-table">
      <thead>
        <tr><th>名称</th><th>分类</th><th>规格</th><th>匹配</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td><button type="button" class="st-button st-button--text" @click="$emit('select', item)">{{ item.name }}</button></td>
          <td>{{ item.categoryPath }}</td>
          <td>{{ [item.brand, item.specification].filter(Boolean).join(' · ') }}</td>
          <td>
            <span v-if="item.matchType === 'BARCODE_EXACT'">条码精确匹配</span>
            <span v-for="tag in item.tags" :key="tag">{{ tag }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <ul v-if="items.length" class="search-results">...existing mobile cards...</ul>
</template>
```

CSS：`<1024` 隐藏 table，显示 list；`≥1024` 相反。

- [ ] **Step 4: catalog workspace CSS**

```css
@media (min-width: 1024px) {
  .catalog-page__workspace {
    display: grid;
    grid-template-columns: minmax(14rem, 0.7fr) minmax(0, 1.3fr);
    gap: var(--st-space-6);
    align-items: start;
  }

  .catalog-page__tree {
    position: sticky;
    top: 5.5rem;
    max-height: calc(100dvh - 8rem);
    overflow: auto;
    padding: var(--st-space-4);
    border: 1px solid var(--st-color-border);
    border-radius: var(--st-radius-card);
    background: var(--st-color-surface);
  }

  .catalog-page .search-results { display: none; }
  .catalog-page .item-results-table { display: block; }
}

@media (max-width: 1023px) {
  .catalog-page .item-results-table { display: none; }
}
```

- [ ] **Step 5: 测试与提交**

```bash
cd frontend && npm test -- src/views/ItemsView.spec.ts src/components/catalog/ItemSearchResults.vue
# 若 ItemSearchResults 无单测，至少 ItemsView + typecheck
cd frontend && npm run typecheck
git add frontend/src/views/ItemsView.vue \
  frontend/src/components/catalog/ItemSearchResults.vue \
  frontend/src/styles/pages.css \
  frontend/src/views/ItemsView.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 物品目录桌面左树右表布局"
```

---

### Task 7: 库存台账与操作对话框桌面化

**Files:**
- Modify: `frontend/src/views/InventoryEntryView.vue`
- Modify: `frontend/src/components/inventory/InventoryEntryList.vue`
- Modify: `frontend/src/styles/main.css`（inventory-sheet 桌面已有居中，核对）
- Modify: `frontend/src/styles/pages.css` / `base.css`
- Modify: `frontend/src/views/InventoryEntryView.spec.ts`

- [ ] **Step 1: 基线测试**

```bash
cd frontend && npm test -- src/views/InventoryEntryView.spec.ts
```

- [ ] **Step 2: InventoryEntryList 增加桌面表格**

列：物品、位置、类型、数量、到期日。选中行高亮。移动卡片列表保留。

```vue
<div class="st-table-wrapper inventory-table">
  <table class="st-table">
    <thead>
      <tr><th>物品</th><th>位置</th><th>类型</th><th>数量</th><th>到期日</th></tr>
    </thead>
    <tbody>
      <tr
        v-for="entry in entries"
        :key="entry.id"
        :class="{ selected: selectedId === entry.id }"
        tabindex="0"
        @click="emit('select', entry)"
        @keydown.enter="emit('select', entry)"
      >
        <td>{{ entry.itemName }}</td>
        <td>{{ entry.locationName }}</td>
        <td>{{ entry.type === 'BATCH' ? '批次' : '资产' }}</td>
        <td>{{ entry.quantity }}</td>
        <td>{{ entry.expirationDate ?? '无到期日' }}</td>
      </tr>
    </tbody>
  </table>
</div>
```

- [ ] **Step 3: 工具条 class**

`InventoryEntryView` 的 actions 已在 `StPageHeader`；确保筛选/导出在桌面横排。`inventory-workspace` 已有双栏，桌面确认列表用表格、详情 sticky。

- [ ] **Step 4: 确认 inventory-sheet 桌面为居中对话框**

`main.css` ≥1024 已有 transform 居中；补：

```css
@media (min-width: 1024px) {
  .inventory-sheet {
    width: min(34rem, calc(100vw - 4rem));
  }
}
```

- [ ] **Step 5: 测试提交**

```bash
cd frontend && npm test -- src/views/InventoryEntryView.spec.ts
git add frontend/src/views/InventoryEntryView.vue \
  frontend/src/components/inventory/InventoryEntryList.vue \
  frontend/src/styles/main.css \
  frontend/src/styles/pages.css \
  frontend/src/styles/base.css
git diff --cached --check
git commit -m "feat(frontend): 库存台账桌面表格与对话框布局"
```

---

### Task 8: 提醒、入库、个人设置桌面形态

**Files:**
- Modify: `frontend/src/views/RemindersView.vue`
- Modify: `frontend/src/views/ReceiveWizardView.vue`（仅样式/壳宽度）
- Modify: `frontend/src/views/ProfileView.vue`
- Modify: `frontend/src/views/NotificationSettingsView.vue`
- Modify: `frontend/src/styles/pages.css`
- Modify: 对应 spec（若有）

- [ ] **Step 1: 提醒页工具条 + 桌面密度**

```css
@media (min-width: 1024px) {
  .reminders-page .reminder-filter {
    max-width: 16rem;
  }

  .reminders-page {
    display: grid;
    gap: var(--st-space-4);
  }

  .reminder-section {
    padding: var(--st-space-4);
  }
}
```

如 `ReminderList` 适合表格，可桌面表格列：类型、物品、状态、时间、操作；移动保留卡片。

- [ ] **Step 2: 入库向导桌面居中收窄**

```css
@media (min-width: 1024px) {
  .receive-wizard {
    width: min(100%, 48rem);
    margin-inline: auto;
  }

  .wizard-progress li {
    font-size: 0.875rem;
  }
}
```

不改变四步状态机。

- [ ] **Step 3: Profile / 通知设置表单宽**

```css
@media (min-width: 1024px) {
  .profile-view,
  .settings-page {
    width: min(100%, 40rem);
    margin-inline: 0;
  }
}
```

- [ ] **Step 4: 测试**

```bash
cd frontend && npm test -- src/views/RemindersView.spec.ts src/views/ReceiveWizardView.spec.ts src/views/ProfileView.spec.ts src/views/NotificationSettingsView.spec.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/RemindersView.vue \
  frontend/src/views/ReceiveWizardView.vue \
  frontend/src/views/ProfileView.vue \
  frontend/src/views/NotificationSettingsView.vue \
  frontend/src/styles/pages.css \
  frontend/src/components/reminder/ReminderList.vue
git diff --cached --check
git commit -m "feat(frontend): 提醒入库与个人页桌面形态"
```

---

### Task 9: 首页桌面后台化

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/components/home/QuickReceive.vue`
- Modify: `frontend/src/components/home/AttentionList.vue`
- Modify: `frontend/src/styles/main.css`
- Modify: `frontend/src/views/HomeView.spec.ts`
- Modify: `frontend/e2e/responsive-shell.spec.ts`（搜索位置）

- [ ] **Step 1: 更新首页单测**

```ts
it('桌面首页保留关注事项与快捷入口', async () => {
  render(HomeView)
  expect(screen.getByRole('heading', { name: '今天需要关注什么？' })).toBeInTheDocument()
  // 顶栏搜索后首页可无搜索；若仍渲染 GlobalSearch，断言存在即可
  expect(screen.getByText('待关注事项')).toBeInTheDocument()
})
```

根据实现选择：
- **推荐**：首页移除 `<GlobalSearch />`，只保留顶栏搜索，避免双搜索；
- E2E 首页断言改为 `getByRole('searchbox', { name: '全局搜索' })` 在 banner/顶栏范围内。

- [ ] **Step 2: HomeView 结构调整**

```vue
<section class="home-view">
  <header class="home-view__header">
    <div>
      <p class="home-view__eyebrow">家庭库存概览</p>
      <h1>今天需要关注什么？</h1>
    </div>
    <p>快速找到物品、完成入库，并及时处理库存提醒。</p>
    <div class="home-view__actions">
      <QuickReceive />
    </div>
  </header>
  <p v-if="error" class="home-view__error" role="alert">{{ error }}</p>
  <AttentionList :summary="summary" />
</section>
```

- [ ] **Step 3: QuickReceive 桌面变体**

移动保持大卡片；桌面可用较扁的主按钮卡：

```css
@media (min-width: 1024px) {
  .home-view {
    grid-template-columns: 1fr;
    width: min(100%, 75rem);
  }

  .home-view__header {
    display: flex;
    justify-content: space-between;
    align-items: end;
    gap: var(--st-space-6);
  }

  .quick-receive {
    min-height: 3.5rem;
    grid-template-columns: auto minmax(0, 1fr);
  }

  .attention-list {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
```

- [ ] **Step 4: 测试与 E2E 搜索选择器**

```bash
cd frontend && npm test -- src/views/HomeView.spec.ts src/AppShell.spec.ts
```

更新 `responsive-shell.spec.ts`：全局搜索在桌面来自顶栏，不依赖 `.home-view` 内节点。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/HomeView.vue \
  frontend/src/components/home/QuickReceive.vue \
  frontend/src/components/home/AttentionList.vue \
  frontend/src/styles/main.css \
  frontend/src/views/HomeView.spec.ts \
  frontend/e2e/responsive-shell.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 首页桌面后台化并去重搜索入口"
```

---

### Task 10: 全量回归与规格验收核对

**Files:**
- 可能小修：各 view/spec、e2e fixtures
- 不改业务逻辑

- [ ] **Step 1: 前端单测 + 类型检查 + 构建**

```bash
cd frontend && npm test && npm run typecheck && npm run build
```

Expected: 全部 PASS

- [ ] **Step 2: Playwright 响应式壳与关键路径**

```bash
cd frontend && npm run test:e2e -- responsive-shell
```

Expected: 320/390/768 底栏可见；1440 桌面导航+顶栏可见、无底栏、无横向滚动。

若环境无浏览器依赖，先：

```bash
cd frontend && npx playwright install chromium
```

- [ ] **Step 3: 手工验收清单（对照规格 §9）**

- [ ] ≥1024：侧栏分组、顶栏、主内容齐全，无移动底栏
- [ ] <1024：底栏与移动路径不变
- [ ] ADMIN/MEMBER/VIEWER 导航可见性正确
- [ ] 业务页与管理页具备桌面后台布局
- [ ] mint cream 令牌未被替换

- [ ] **Step 4: 最终提交（若有修复）**

```bash
git add -A
git diff --cached --check
git commit -m "test(frontend): 补齐桌面后台布局回归"
```

---

## Spec 覆盖自检

| 规格要点 | 对应任务 |
| --- | --- |
| ≥1024 后台壳：侧栏+顶栏+主内容 | Task 1–3 |
| <1024 移动底栏不变 | Task 3、10 |
| 侧栏业务分组与角色过滤 | Task 1 |
| 顶栏搜索/快捷入库/账户菜单 | Task 2 |
| 管理页表格/双栏 | Task 4–5 |
| 物品/库存/提醒/入库桌面形态 | Task 6–8 |
| 首页桌面化与搜索策略 | Task 9 |
| mint cream、无新框架 | 全任务约束 |
| 测试与验收 | Task 1–3、10 |

## Placeholder 扫描

计划中不含 TBD/TODO/“类似 Task N” 空步骤；图标导出名允许以包内实际导出做等价替换（已注明核对方式）。

## 类型/命名一致性

- 账户类型：`CurrentAccount`
- 导航：`NavItem` / `NavGroup` / `visibleNavGroups(role)`
- 顶栏事件：`logout`
- 主内容 id：`main-content`
- 断点：统一 `1024px`
