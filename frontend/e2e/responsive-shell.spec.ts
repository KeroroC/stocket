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
    if (width < 1024) {
      expect(layout.mobileVisible).toBe(true)
      expect(layout.contentPaddingBottom).toBeGreaterThanOrEqual(layout.mobileHeight)
    } else {
      expect(layout.mobileVisible).toBe(false)
      await expect(page.getByRole('navigation', { name: '桌面主导航' })).toBeVisible()
      await expect(page.getByRole('banner', { name: '桌面顶栏' })).toBeVisible()
      await expect(page.getByRole('searchbox', { name: '全局搜索' })).toBeVisible()
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

const primaryTabs = [
  { label: '首页', path: '/', heading: '今天需要关注什么？' },
  { label: '物品', path: '/items', heading: '物品目录' },
  { label: '入库', path: '/receive', heading: '把物品放到正确的位置' },
  { label: '提醒', path: '/reminders', heading: '提醒中心' },
  { label: '我的', path: '/profile', heading: '我的账户' },
]

const allPages = [
  { path: '/', heading: '今天需要关注什么？' },
  { path: '/items', heading: '物品目录' },
  { path: '/receive', heading: '把物品放到正确的位置' },
  { path: '/reminders', heading: '提醒中心' },
  { path: '/inventory', heading: '库存台账' },
  { path: '/profile', heading: '我的账户' },
  { path: '/notification-settings', heading: '通知设置' },
  { path: '/admin/members', heading: '成员管理' },
  { path: '/admin/invites', heading: '邀请管理' },
  { path: '/admin/categories', heading: '分类管理' },
  { path: '/admin/locations', heading: '位置管理' },
  { path: '/admin/delivery-failures', heading: '通知失败' },
  { path: '/admin/audit-logs', heading: '审计日志' },
  { path: '/admin/diagnostics', heading: '系统诊断' },
]

for (const route of allPages) {
  test(`${route.path} 页面在移动和桌面视口布局合理且控件可触达`, async ({ page }) => {
    await installApiFixture(page, { role: 'ADMIN' })
    await page.goto(route.path)
    await expect(page.getByRole('heading', { name: route.heading })).toBeVisible()
    for (const viewport of [{ width: 390, height: 844 }, { width: 1440, height: 900 }]) {
      await page.setViewportSize(viewport)
      await expect(page.getByRole('heading', { name: route.heading })).toBeVisible()
      const layout = await page.evaluate(() => {
        const controls = [...document.querySelectorAll('button, input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]), select, textarea, a.st-button, label:has(input[type="checkbox"]), label:has(input[type="radio"])')]
          .filter(element => {
            const style = getComputedStyle(element)
            const rect = element.getBoundingClientRect()
            return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0
          })
          .map(element => ({ height: element.getBoundingClientRect().height, html: element.outerHTML.slice(0, 180) }))
        return {
          scrollWidth: document.documentElement.scrollWidth,
          innerWidth: window.innerWidth,
          minimumControl: controls.length
            ? controls.reduce((minimum, control) => control.height < minimum.height ? control : minimum,
              { height: Number.POSITIVE_INFINITY, html: '' })
            : { height: 44, html: '' },
        }
      })
      expect(layout.scrollWidth, `${route.path} 在 ${viewport.width}px 不应横向溢出`).toBeLessThanOrEqual(layout.innerWidth)
      expect(layout.minimumControl.height, `${route.path} 在 ${viewport.width}px 的可见控件高度不应小于 44px：${layout.minimumControl.html}`).toBeGreaterThanOrEqual(44)
    }
  })
}

test('主 Tab 切换保持应用外壳稳定', async ({ page }, testInfo) => {
  const viewport = testInfo.project.name === 'mobile-chromium'
    ? { width: 390, height: 844, navigationName: '移动主导航' }
    : { width: 1440, height: 900, navigationName: '桌面主导航' }
  await installApiFixture(page)
  await page.setViewportSize({ width: viewport.width, height: viewport.height })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: primaryTabs[0].heading })).toBeVisible()

  const readShellGeometry = () => page.evaluate(() => {
    const shell = document.querySelector('.pwa-shell')!.getBoundingClientRect()
    const content = document.querySelector('.pwa-shell__content')!.getBoundingClientRect()
    const navigation = document.querySelector(
      getComputedStyle(document.querySelector('.mobile-tab-bar')!).display === 'none'
        ? '.desktop-sidebar'
        : '.mobile-tab-bar',
    )!.getBoundingClientRect()
    return {
      shell: { left: shell.left, width: shell.width },
      content: { left: content.left, width: content.width },
      navigation: { left: navigation.left, top: navigation.top, width: navigation.width, height: navigation.height },
    }
  })

  const baseline = await readShellGeometry()
  for (const tab of primaryTabs.slice(1)) {
    await page.getByRole('navigation', { name: viewport.navigationName })
      .getByRole('link', { name: tab.label, exact: true })
      .click()
    await expect(page).toHaveURL(tab.path)
    await expect(page.getByRole('heading', { name: tab.heading })).toBeVisible()
    expect(await readShellGeometry(), `${tab.label} 不应改变应用外壳几何`).toEqual(baseline)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(viewport.width)
  }

  await page.getByRole('navigation', { name: viewport.navigationName })
    .getByRole('link', { name: '我的', exact: true })
    .click()
  await expect(page.getByRole('heading', { name: '我的账户' })).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(0)
  await page.getByRole('navigation', { name: viewport.navigationName })
    .getByRole('link', { name: '首页', exact: true })
    .click()
  await expect(page.getByRole('heading', { name: primaryTabs[0].heading })).toBeVisible()
  expect(await page.evaluate(() => window.scrollY), '切换 Tab 后应从新页面顶部开始').toBe(0)
})
