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
    expect(base).toContain(':focus-visible')
    expect(base).toContain('@media (prefers-reduced-motion: reduce)')
  })
})
