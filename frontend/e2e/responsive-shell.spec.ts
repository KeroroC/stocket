import { expect, test } from '@playwright/test'
import { installApiFixture } from './fixtures'

test('320/390/768/1440 宽度无横向滚动且导航不遮挡正文', async ({ page }) => {
  await installApiFixture(page)
  await page.goto('/')
  await expect(page.getByLabel('全局搜索')).toBeVisible()
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
      }
    })
    expect(layout.scrollWidth).toBeLessThanOrEqual(layout.innerWidth)
    if (width < 768) {
      expect(layout.mobileVisible).toBe(true)
      expect(layout.contentPaddingBottom).toBeGreaterThanOrEqual(layout.mobileHeight)
    } else {
      expect(layout.mobileVisible).toBe(false)
      await expect(page.getByRole('navigation', { name: '桌面主导航' })).toBeVisible()
    }
  }
})
