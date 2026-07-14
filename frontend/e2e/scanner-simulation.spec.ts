import { expect, test } from '@playwright/test'
import { emitScan, installApiFixture } from './fixtures'

for (const width of [320, 390]) {
  test(`${width}px 扫描面板在移动视口内完整显示`, async ({ page }) => {
    await installApiFixture(page)
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/receive')
    await page.getByRole('button', { name: '扫描商品条码' }).click()
    const dialog = page.getByRole('dialog', { name: '扫描条码或位置码' })
    await expect(dialog).toBeVisible()

    const layout = await dialog.evaluate((element) => {
      const rect = element.getBoundingClientRect()
      const video = element.querySelector('video')!.getBoundingClientRect()
      const buttons = [...element.querySelectorAll('button')].map(button => button.getBoundingClientRect())
      return {
        position: getComputedStyle(element.parentElement!).position,
        dialog: { top: rect.top, right: rect.right, bottom: rect.bottom, left: rect.left },
        video: { width: video.width, right: video.right, left: video.left },
        buttonHeights: buttons.map(button => button.height),
      }
    })

    expect(layout.position).toBe('fixed')
    expect(layout.dialog.top).toBeGreaterThanOrEqual(0)
    expect(layout.dialog.left).toBeGreaterThanOrEqual(0)
    expect(layout.dialog.right).toBeLessThanOrEqual(width)
    expect(layout.dialog.bottom).toBeLessThanOrEqual(844)
    expect(layout.video.width).toBeGreaterThan(0)
    expect(layout.video.left).toBeGreaterThanOrEqual(layout.dialog.left)
    expect(layout.video.right).toBeLessThanOrEqual(layout.dialog.right)
    expect(layout.buttonHeights.every(height => height >= 44)).toBe(true)

    await page.getByRole('button', { name: '关闭扫描器' }).click()
    await expect(dialog).toBeHidden()
  })
}

test('fake scanner 注入商品码和位置码', async ({ page }) => {
  await installApiFixture(page)
  await page.goto('/receive')
  await page.getByRole('button', { name: '扫描商品条码' }).click()
  await emitScan(page, '690001')
  await expect(page.getByText('鲜牛奶')).toBeVisible()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '扫描位置码' }).click()
  await emitScan(page, 'stocket:location:FRIDGE')
  await expect(page.getByText('位置：冰箱')).toBeVisible()
})
