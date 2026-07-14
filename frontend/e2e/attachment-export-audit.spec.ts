import { expect, test } from '@playwright/test'
import { installApiFixture } from './fixtures'

test('管理员可完成附件、审计和诊断工作流', async ({ page }) => {
  await installApiFixture(page, { role:'ADMIN' })
  await page.goto('/items')
  await page.getByLabel('搜索物品').fill('鲜牛奶')
  await page.getByRole('button', { name:/鲜牛奶/ }).click()
  await expect(page.getByText('正面.png', { exact:true })).toBeVisible()
  await page.getByLabel('上传图片').setInputFiles({ name:'new.png', mimeType:'image/png', buffer:Buffer.from('png') })
  await page.getByRole('button', { name:'开始上传' }).click()
  await expect(page.getByText('新图片.png', { exact:true })).toBeVisible()

  await page.getByRole('link', { name:'审计日志' }).click()
  await expect(page.getByText('AttachmentUploaded')).toBeVisible()
  await expect(page.getByText('e2e-request-123')).toBeVisible()
  await page.getByRole('link', { name:'系统诊断' }).click()
  await expect(page.getByText('正常')).toBeVisible()
  await expect(page.getByText('需要处理')).toBeVisible()
  await expect(page.getByText(/检查附件存储并运行恢复任务/)).toBeVisible()
})
