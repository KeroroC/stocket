import { expect, test } from '@playwright/test'
import { installApiFixture, reachDetailsStep } from './fixtures'

test('断网刷新后恢复第三步草稿并阻止提交', async ({ page, context }) => {
  await installApiFixture(page)
  await reachDetailsStep(page)
  await page.getByLabel('数量').fill('4.2500')
  await page.waitForTimeout(500)
  await page.evaluate(() => navigator.serviceWorker.ready)

  await context.setOffline(true)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByLabel('数量')).toHaveValue('4.2500')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '确认入库' }).click()
  await expect(page.getByRole('alert')).toContainText('当前离线')

  await context.setOffline(false)
  await page.evaluate(async () => {
    const registrations = await navigator.serviceWorker.getRegistrations()
    await Promise.all(registrations.map(registration => registration.unregister()))
  })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByLabel('数量')).toHaveValue('4.2500')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '确认入库' }).click()
  await expect(page.getByText('入库完成')).toBeVisible()
})
