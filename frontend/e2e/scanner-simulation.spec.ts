import { expect, test } from '@playwright/test'
import { emitScan, installApiFixture } from './fixtures'

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
