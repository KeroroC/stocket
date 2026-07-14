import { expect, test } from '@playwright/test'
import { installApiFixture } from './fixtures'

test('320/390/768/1440 宽度无横向滚动且导航不遮挡正文', async ({ page }) => {
  await installApiFixture(page)
  await page.goto('/')
  await expect(page.getByRole('searchbox', { name: '全局搜索' })).toBeVisible()
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: width < 768 ? 844 : 900 })
    const layout = await page.evaluate(() => {
      const content = document.querySelector('.pwa-shell__content') as HTMLElement
      const mobile = document.querySelector('.mobile-tab-bar') as HTMLElement
      return {
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth,
        contentPaddingBottom: Number.parseFloat(getComputedStyle(content).paddingBottom),
        mobileHeight: mobile.getBoundingClientRect().height,
        mobileVisible: getComputedStyle(mobile).display !== 'none',
        searchIconWidth: document.querySelector('.global-search svg')?.getBoundingClientRect().width ?? 0,
        quickReceiveWidth: document.querySelector('.quick-receive')?.getBoundingClientRect().width ?? 0,
      }
    })
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.innerWidth)
    expect(layout.searchIconWidth).toBeLessThanOrEqual(24)
    expect(layout.quickReceiveWidth).toBeGreaterThan(0)
    if (width < 768) {
      expect(layout.mobileVisible).toBe(true)
      expect(layout.contentPaddingBottom).toBeGreaterThanOrEqual(layout.mobileHeight)
    } else {
      expect(layout.mobileVisible).toBe(false)
      await expect(page.getByRole('navigation', { name: '桌面主导航' })).toBeVisible()
    }
  }

})

const routes = [
  { path: '/items', heading: '物品目录' },
  { path: '/receive', heading: '把物品放到正确的位置' },
  { path: '/reminders', heading: '提醒中心' },
  { path: '/inventory', heading: '库存台账' },
]

for (const route of routes) {
  test(`${route.path} 核心页面在各断点无横向溢出`, async ({ page }) => {
    await installApiFixture(page)
    await page.goto(route.path)
    await expect(page.getByRole('heading', { name: route.heading })).toBeVisible()
    for (const width of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: width < 768 ? 844 : 900 })
      const dimensions = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth,
      }))
      expect(dimensions.scrollWidth, `${route.path} 在 ${width}px 不应横向溢出`).toBeLessThanOrEqual(dimensions.innerWidth)
    }
  })
}
