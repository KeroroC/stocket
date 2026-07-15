import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const readStyle = (name: string) => readFileSync(resolve(process.cwd(), `src/styles/${name}`), 'utf8')

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
    expect(base).toContain('.sr-only')
    expect(base).toContain(':focus-visible')
    expect(base).toContain('@media (prefers-reduced-motion: reduce)')
  })

  it('为业务页面提供响应式布局基线', () => {
    const css = readStyle('pages.css')
    expect(css).toContain('.catalog-page')
    expect(css).toContain('.receive-wizard')
    expect(css).toContain('.reminders-page')
    expect(css).toContain('.reminder-kind--expired')
    expect(css).toContain('.st-table-wrapper')
    expect(css).toContain('.admin-grid')
    expect(readStyle('main.css')).toContain('.inventory-sheet')
    expect(css).toContain('flex-wrap: wrap')
    expect(css).not.toContain('.st-page-header__actions button:first-child')
    expect(css).toContain('@media (max-width: 767px)')
  })
})
