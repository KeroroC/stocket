import { expect, test } from '@playwright/test'
import { installApiFixture, reachDetailsStep } from './fixtures'

test('手机端在 30 秒预算内完成已有物品入库', async ({ page }) => {
  const startedAt = Date.now()
  const fixture = await installApiFixture(page)
  await reachDetailsStep(page)
  await page.getByLabel('数量').fill('1.2500')
  await page.getByRole('button', { name: '下一步' }).click()
  await expect(page.getByText('2 + 1.2500 = 提交后库存')).toBeVisible()
  await page.getByRole('button', { name: '确认入库' }).click()

  await expect(page.getByText('入库完成')).toBeVisible()
  expect(fixture.receiptBodies).toHaveLength(1)
  expect(fixture.receiptBodies[0]).toMatchObject({ quantity: '1.2500', itemId: 'item-1', locationId: 'location-1' })
  await page.getByRole('link', { name: '查看入库物品' }).click()
  await expect(page).toHaveURL('/inventory/entry-new')
  await expect(page.getByRole('heading', { name: '鲜牛奶' })).toBeVisible()
  await expect(page.getByLabel('库存条目').locator('.inventory-card.selected')).toContainText('1.25')
  await expect(page.getByLabel('库存流水').getByText('1.25', { exact: true })).toBeVisible()
  await expect(page.getByText('可用量：3.25')).toBeVisible()
  expect(Date.now() - startedAt).toBeLessThan(30_000)
})
