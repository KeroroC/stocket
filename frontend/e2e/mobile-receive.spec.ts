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
  expect(Date.now() - startedAt).toBeLessThan(30_000)
})
