# Stocket 前端设计系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将“薄荷奶油”设计规范落实为可测试、可复用的 CSS 令牌、Element Plus 主题映射、基础语义组件和响应式应用外壳。

**Architecture:** 以 CSS 自定义属性作为唯一视觉令牌源，通过独立主题文件映射 Element Plus 变量；Vue 语义组件封装状态、空状态、页面标题和表单操作，业务页面只组合组件而不复制品牌样式。当前 `App.vue` 改为设计系统展示与后端状态页，既保留工程健康检查，又形成后续身份页面可复用的应用外壳。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Element Plus 2.14、CSS Custom Properties、Vitest 4、Testing Library Vue、Vite 8

---

## 执行约束

- 在独立 `codex/frontend-design-system` worktree 执行，不直接在 `main` 修改。
- 严格遵循红—绿—重构：先运行并观察测试按预期失败，再写最小实现。
- 不引入 Tailwind、CSS-in-JS、图标库、Web Font、Storybook 或新的运行时依赖。
- 不实现身份、目录、库存或提醒业务页面；这些页面由对应阶段计划消费本设计系统。
- 每次提交只暂存任务清单内文件，并先运行 `git diff --cached --check`。
- 所有组件公开接口使用业务语义命名；不得把 Element Plus 类型泄漏给业务调用方。

## 文件结构

```text
frontend/src/
├── styles/
│   ├── tokens.css                 # 品牌、语义、空间、圆角、阴影和动效令牌
│   ├── element-theme.css          # Element Plus CSS 变量映射
│   ├── base.css                   # reset、排版、焦点、减少动态效果
│   └── main.css                   # 仅聚合样式入口
├── components/
│   ├── StPageHeader.vue           # 页面标题、说明和操作区
│   ├── StStatusTag.vue            # 库存及系统状态语义标签
│   ├── StEmptyState.vue           # 空状态与单一下一步
│   ├── StFormActions.vue          # 主次表单操作和移动端粘滞行为
│   ├── AppShell.vue               # 移动底栏与桌面侧栏外壳
│   └── __tests__/                 # 各组件行为和无障碍测试
├── App.vue                        # 设计系统基线页和系统连接状态
└── App.spec.ts                    # 应用级响应与语义回归测试
```

## Task 1：建立视觉令牌与全局基础样式

**Files:**
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/styles/base.css`
- Create: `frontend/src/styles/element-theme.css`
- Modify: `frontend/src/styles/main.css`
- Create: `frontend/src/styles/styles.spec.ts`

- [ ] **Step 1：写令牌与可访问性失败测试**

创建 `styles.spec.ts`，读取三个 CSS 文件并断言关键契约：

```ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const readStyle = (name: string) =>
  readFileSync(resolve(process.cwd(), `src/styles/${name}`), 'utf8')

describe('Stocket design tokens', () => {
  it('定义薄荷奶油品牌与状态令牌', () => {
    const css = readStyle('tokens.css')
    expect(css).toContain('--st-color-bg: #fbf8ef')
    expect(css).toContain('--st-color-primary: #167a63')
    expect(css).toContain('--st-color-warning: #e5a33d')
    expect(css).toContain('--st-control-min-size: 44px')
  })

  it('映射 Element Plus 并保留焦点与减少动态效果', () => {
    expect(readStyle('element-theme.css')).toContain('--el-color-primary: var(--st-color-primary)')
    const base = readStyle('base.css')
    expect(base).toContain(':focus-visible')
    expect(base).toContain('@media (prefers-reduced-motion: reduce)')
  })
})
```

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/styles/styles.spec.ts`

Expected: FAIL，提示 `tokens.css` 不存在。

- [ ] **Step 3：实现令牌、主题映射和基础规则**

`tokens.css` 必须包含规范中的颜色令牌，以及以下基础令牌：

```css
:root {
  --st-color-bg: #fbf8ef;
  --st-color-surface: #fffef9;
  --st-color-primary-soft: #dff4ec;
  --st-color-primary: #167a63;
  --st-color-primary-hover: #126b56;
  --st-color-primary-active: #0f5d4b;
  --st-color-text: #20332c;
  --st-color-text-muted: #6c7b75;
  --st-color-border: #e4e8df;
  --st-color-warning: #e5a33d;
  --st-color-danger: #d65353;
  --st-color-accent: #ef765f;
  --st-color-info: #3d7f8b;
  --st-space-1: 4px;
  --st-space-2: 8px;
  --st-space-3: 12px;
  --st-space-4: 16px;
  --st-space-6: 24px;
  --st-space-8: 32px;
  --st-radius-control: 12px;
  --st-radius-card: 16px;
  --st-radius-feature: 20px;
  --st-control-min-size: 44px;
  --st-motion-fast: 160ms;
  --st-motion-normal: 220ms;
}
```

`element-theme.css` 映射 `--el-color-primary`、成功、警告、危险、信息、文字、边框、背景、圆角和控件高度。`base.css` 包含 box sizing、系统中文字体栈、奶油背景、等宽数字、可见焦点环和减少动态效果规则。`main.css` 只使用 `@import` 按 `tokens.css`、`element-theme.css`、`base.css` 顺序聚合。

- [ ] **Step 4：验证样式测试和类型检查**

Run: `cd frontend && npm test -- src/styles/styles.spec.ts && npm run typecheck`

Expected: 两条样式测试 PASS，`vue-tsc` 退出码为 0。

- [ ] **Step 5：提交令牌边界**

```bash
git add frontend/src/styles/tokens.css frontend/src/styles/base.css frontend/src/styles/element-theme.css frontend/src/styles/main.css frontend/src/styles/styles.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 建立薄荷奶油设计令牌"
```

## Task 2：实现页面标题与状态标签

**Files:**
- Create: `frontend/src/components/StPageHeader.vue`
- Create: `frontend/src/components/StStatusTag.vue`
- Create: `frontend/src/components/__tests__/StPageHeader.spec.ts`
- Create: `frontend/src/components/__tests__/StStatusTag.spec.ts`

- [ ] **Step 1：写组件行为失败测试**

页面标题测试断言标题层级、可选说明和操作插槽；状态测试覆盖文本、图标之外的可访问标签和未知状态回退：

```ts
import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import StStatusTag from '../StStatusTag.vue'

describe('StStatusTag', () => {
  it.each([
    ['healthy', '状态正常'],
    ['expiring', '即将到期'],
    ['expired', '已过期'],
    ['low-stock', '库存偏低'],
    ['archived', '已归档'],
  ] as const)('用文字表达 %s 状态', (status, label) => {
    render(StStatusTag, { props: { status, label } })
    expect(screen.getByText(label)).toHaveAttribute('data-status', status)
  })
})
```

`StPageHeader.spec.ts` 使用 `render` 的 `slots` 传入“添加物品”按钮，断言 `heading` 名称、说明文本和按钮均存在。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/components/__tests__/StPageHeader.spec.ts src/components/__tests__/StStatusTag.spec.ts`

Expected: FAIL，两个 Vue 组件无法解析。

- [ ] **Step 3：实现明确的组件接口**

`StPageHeader` 接口固定为：

```ts
defineProps<{
  title: string
  description?: string
  eyebrow?: string
  headingLevel?: 1 | 2
}>()
```

动态标题使用 `<component :is="`h${headingLevel ?? 1}`">`；操作插槽命名为 `actions`。小屏纵向排列，大屏标题与操作横向对齐。

`StStatusTag` 接口固定为：

```ts
export type StStatus =
  | 'healthy'
  | 'expiring'
  | 'expired'
  | 'low-stock'
  | 'archived'

defineProps<{ status: StStatus; label: string }>()
```

根元素渲染 `data-status`，每种状态使用不同背景、文字色和前置形状；不得依赖 Element Plus 的 `type` 命名作为公开接口。

- [ ] **Step 4：运行组件测试**

Run: `cd frontend && npm test -- src/components/__tests__/StPageHeader.spec.ts src/components/__tests__/StStatusTag.spec.ts`

Expected: 全部 PASS，且测试输出无 Vue warning。

- [ ] **Step 5：提交标题与状态组件**

```bash
git add frontend/src/components/StPageHeader.vue frontend/src/components/StStatusTag.vue frontend/src/components/__tests__/StPageHeader.spec.ts frontend/src/components/__tests__/StStatusTag.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 添加页面标题与状态组件"
```

## Task 3：实现空状态与表单操作组件

**Files:**
- Create: `frontend/src/components/StEmptyState.vue`
- Create: `frontend/src/components/StFormActions.vue`
- Create: `frontend/src/components/__tests__/StEmptyState.spec.ts`
- Create: `frontend/src/components/__tests__/StFormActions.spec.ts`

- [ ] **Step 1：写空状态和提交保护失败测试**

```ts
import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import StFormActions from '../StFormActions.vue'

describe('StFormActions', () => {
  it('提交中禁用主操作并显示具体状态', async () => {
    const onSubmit = vi.fn()
    render(StFormActions, {
      props: { primaryLabel: '完成入库', pending: true, onSubmit },
    })
    const button = screen.getByRole('button', { name: '正在完成入库' })
    expect(button).toBeDisabled()
    await fireEvent.click(button)
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
```

`StEmptyState.spec.ts` 传入 `title="还没有物品"`、说明和默认插槽按钮，断言标题、说明和“添加第一个物品”按钮可访问；再断言没有插槽时不渲染空操作容器。

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/components/__tests__/StEmptyState.spec.ts src/components/__tests__/StFormActions.spec.ts`

Expected: FAIL，组件无法解析。

- [ ] **Step 3：实现组件和响应式行为**

`StEmptyState` 接收 `title`、`description`、可选 `iconLabel`，默认插槽只容纳一个下一步操作。装饰图形设置 `aria-hidden="true"`；若传入 `iconLabel`，额外提供 visually-hidden 文本。

`StFormActions` 接口固定为：

```ts
defineProps<{
  primaryLabel: string
  secondaryLabel?: string
  pending?: boolean
  destructive?: boolean
}>()

defineEmits<{
  submit: []
  secondary: []
}>()
```

主按钮提交中禁用并把 accessible name 改为 `正在${primaryLabel}`；危险态使用危险令牌。移动端在容器添加 `data-sticky="true"` 时粘滞到底部，桌面端恢复普通文档流。

- [ ] **Step 4：运行组件测试与类型检查**

Run: `cd frontend && npm test -- src/components/__tests__/StEmptyState.spec.ts src/components/__tests__/StFormActions.spec.ts && npm run typecheck`

Expected: 全部 PASS，类型检查退出码为 0。

- [ ] **Step 5：提交反馈组件**

```bash
git add frontend/src/components/StEmptyState.vue frontend/src/components/StFormActions.vue frontend/src/components/__tests__/StEmptyState.spec.ts frontend/src/components/__tests__/StFormActions.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 添加空状态与表单操作组件"
```

## Task 4：建立响应式应用外壳

**Files:**
- Create: `frontend/src/components/AppShell.vue`
- Create: `frontend/src/components/__tests__/AppShell.spec.ts`

- [ ] **Step 1：写导航语义失败测试**

```ts
import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import AppShell from '../AppShell.vue'

describe('AppShell', () => {
  it('提供五个移动入口并标记当前页面', () => {
    render(AppShell, { props: { activeItem: 'home', accountLabel: '安心' } })
    expect(screen.getByRole('navigation', { name: '主要导航' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '首页' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: '物品' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '快速入库' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '提醒' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '我的' })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2：运行测试并确认失败**

Run: `cd frontend && npm test -- src/components/__tests__/AppShell.spec.ts`

Expected: FAIL，`AppShell.vue` 不存在。

- [ ] **Step 3：实现移动底栏与桌面侧栏**

组件接口固定为：

```ts
type NavigationItem = 'home' | 'items' | 'inbound' | 'reminders' | 'account'

defineProps<{
  activeItem: NavigationItem
  accountLabel: string
}>()
```

五个入口使用真实链接：`/`、`/items`、`/inbound`、`/reminders`、`/account`；当前项设置 `aria-current="page"`。小于 1024px 使用底栏，中央入库按钮高强调但无循环动画；1024px 起改为左侧导航，主内容宽度不超过 1440px。提供默认内容插槽和 `skip-link`，跳转目标固定为 `#main-content`。

- [ ] **Step 4：验证外壳行为**

Run: `cd frontend && npm test -- src/components/__tests__/AppShell.spec.ts && npm run typecheck`

Expected: PASS，类型检查退出码为 0。

- [ ] **Step 5：提交应用外壳**

```bash
git add frontend/src/components/AppShell.vue frontend/src/components/__tests__/AppShell.spec.ts
git diff --cached --check
git commit -m "feat(frontend): 建立响应式应用外壳"
```

## Task 5：改造工程基线页并完成回归验证

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/App.spec.ts`
- Modify: `frontend/src/main.ts`

- [ ] **Step 1：把应用测试改为设计系统验收测试**

保留系统 API 成功、失败和 `aria-live` 测试，并把页面断言更新为：

```ts
render(App)

expect(screen.getByRole('heading', { name: '家里一切，都井井有条。' })).toBeInTheDocument()
expect(screen.getByRole('navigation', { name: '主要导航' })).toBeInTheDocument()
expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite')
expect(await screen.findByText('后端 0.1.0-test 已连接')).toBeInTheDocument()
```

新增失败测试：API 返回 503 时显示“后端暂不可用”和可读警告状态；读取样式入口并断言不再包含旧 `.foundation-card` 与硬编码 `#2563eb`。

- [ ] **Step 2：运行应用测试并确认失败**

Run: `cd frontend && npm test -- src/App.spec.ts`

Expected: FAIL，当前标题仍为“家庭资产”，旧 foundation 样式仍存在。

- [ ] **Step 3：组合应用外壳和语义组件**

`App.vue` 使用 `AppShell`、`StPageHeader` 和 `StStatusTag`，保留 `getSystemStatus()` 调用。页面展示问候标题、系统连接卡片和三个设计系统原则摘要；不伪造库存、提醒或用户数据。连接中、成功和失败分别映射为 `healthy`、`healthy` 和 `expiring`，同时保留具体文本。

`main.ts` 继续先导入 Element Plus 原始 CSS，再导入 `./styles/main.css`，确保 Stocket 主题变量后加载并覆盖默认主题；不得在 `App.vue` 添加散落的全局品牌色。

- [ ] **Step 4：运行前端完整验证**

Run: `cd frontend && npm test`

Expected: 全部 Vitest 测试 PASS。

Run: `cd frontend && npm run typecheck && npm run build`

Expected: `vue-tsc` 与 Vite 均退出码为 0，产物生成在 `frontend/dist/`。

- [ ] **Step 5：执行差异和禁用模式检查**

Run: `rg -n '#2563eb|foundation-card|outline:\s*none|transition:\s*all' frontend/src || true`

Expected: 无输出。若组件需要移除默认 outline，必须在同一规则提供明显的 `:focus-visible` 替代样式；禁止 `transition: all`。

- [ ] **Step 6：提交应用集成**

```bash
git add frontend/src/App.vue frontend/src/App.spec.ts frontend/src/main.ts
git diff --cached --check
git commit -m "feat(frontend): 应用薄荷奶油设计系统"
```

## Task 6：项目级验收

**Files:**
- No file changes expected

- [ ] **Step 1：运行仓库前端门禁**

Run: `make frontend-test frontend-build`

Expected: 前端测试和生产构建均成功。

- [ ] **Step 2：检查工作区和提交边界**

Run: `git status --short`

Expected: 无计划内未提交文件；若存在用户原有改动，只记录并保留，不纳入本计划提交。

- [ ] **Step 3：人工响应式与无障碍冒烟**

Run: `cd frontend && npm run dev`

在 320px、768px、1024px 和 1440px 视口确认：无水平溢出；320px 使用底栏；1024px 起使用侧栏；键盘焦点始终可见；Tab 可到达跳过导航和五个导航入口；开启减少动态效果后没有非必要转场。

- [ ] **Step 4：记录验收结果**

若所有检查通过，在实施任务的最终交付说明中列出实际执行的测试命令、提交列表和人工检查结果；本步骤不新增文档或提交。
