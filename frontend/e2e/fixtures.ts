import type { Page } from '@playwright/test'

const baseAccount = {
  id: 'account-1', username: 'member', displayName: '移动成员', role: 'MEMBER', mustChangePassword: false,
}
const item = {
  id: 'item-1', name: '鲜牛奶', categoryId: 'category-1', brand: null, model: null,
  specification: null, defaultUnit: '盒', defaultShelfLifeValue: 30, defaultShelfLifeUnit: 'DAY',
  customAttributes: {}, barcodes: ['690001'], tags: ['冷藏'], version: 1, archived: false,
}
const location = {
  id: 'location-1', parentId: null, name: '冰箱', fullPath: '厨房 > 冰箱', publicCode: 'FRIDGE', version: 1, archived: false,
}

function canonicalDecimal(value: unknown) {
  const [integer, fraction = ''] = String(value).split('.')
  const trimmedFraction = fraction.replace(/0+$/, '')
  return trimmedFraction ? `${integer}.${trimmedFraction}` : integer
}

export async function installApiFixture(page: Page, options: { role?: 'ADMIN' | 'MEMBER' | 'VIEWER' } = {}) {
  const receiptBodies: unknown[] = []
  const inventoryEntries: Array<Record<string, unknown>> = []
  const account = { ...baseAccount, role: options.role ?? baseAccount.role }
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const json = (body: unknown, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })

    if (path === '/api/v1/setup/status') return json({ initialized: true })
    if (path === '/api/v1/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/api/v1/account') return json(account)
    if (path === '/api/v1/account/sessions') return json([
      { id: 'session-1', createdAt: '2026-07-14T00:00:00Z', lastSeenAt: '2026-07-14T00:00:00Z', userAgent: 'E2E Browser', current: true },
    ])
    if (path === '/api/v1/admin/members') return json([])
    if (path === '/api/v1/admin/invites') return json([])
    if (path === '/api/v1/dashboard') return json({ summary: { expiring: 1, expired: 0, lowStock: 1, openTotal: 2 }, search: [] })
    if (path === '/api/v1/catalog/search') return json({
      items: [{ id: item.id, name: item.name, categoryPath: '食品', brand: null, model: null, specification: null, tags: item.tags, barcodes: item.barcodes, matchType: 'BARCODE_EXACT' }],
      page: 0, size: 20, total: 1,
    })
    if (path === `/api/v1/items/${item.id}`) return json(item)
    if (path === '/api/v1/attachments' && request.method() === 'GET') return json([
      { id:'attachment-1',ownerType:'ITEM_DEFINITION',ownerId:item.id,purpose:'ITEM_IMAGE',filename:'正面.png',mediaType:'image/png',sizeBytes:12,status:'AVAILABLE',createdAt:'2026-07-14T00:00:00Z' },
    ])
    if (path === '/api/v1/attachments' && request.method() === 'POST') return json(
      { id:'attachment-new',ownerType:'ITEM_DEFINITION',ownerId:item.id,purpose:url.searchParams.get('purpose'),filename:'新图片.png',mediaType:'image/png',sizeBytes:12,status:'AVAILABLE',createdAt:'2026-07-14T00:00:00Z' }, 201,
    )
    if (path === '/api/v1/admin/audit-logs') return json({ items:[
      { id:'audit-1',occurredAt:'2026-07-14T00:00:00Z',eventType:'AttachmentUploaded',outcome:'SUCCESS',actorDisplayName:'移动成员',subjectType:'ATTACHMENT',subjectId:'attachment-1',requestId:'e2e-request-123',source:'api',details:{purpose:'ITEM_IMAGE'} },
    ] })
    if (path === '/api/v1/admin/diagnostics') return json({ checks:{
      database:{status:'OK',count:0,checkedAt:'2026-07-14T00:00:00Z',actionCode:'CHECK_DATABASE'},
      missingAttachments:{status:'WARN',count:1,checkedAt:'2026-07-14T00:00:00Z',actionCode:'REPAIR_ATTACHMENT_STORAGE'},
    } })
    if (path === '/api/v1/admin/notification/deliveries') return json({ content: [], page: 0, size: 20, total: 0 })
    if (path === '/api/v1/notification/channels') return json([])
    if (path.startsWith('/api/v1/exports/')) return route.fulfill({ status:200, contentType:'text/csv;charset=UTF-8', body:'\ufeffid,name\nitem-1,鲜牛奶\n' })
    if (path === '/api/v1/locations/resolve-code') return json(location)
    if (path === '/api/v1/locations') return json([location])
    if (path === '/api/v1/inventory/availability') return json({
      itemId: item.id,
      totalAvailable: inventoryEntries.length
        ? String(2 + Number(inventoryEntries.find(entry => entry.id === 'entry-new')?.quantity ?? 0))
        : '2',
      earliestExpiration: '2026-07-20',
      activeEntryCount: inventoryEntries.length ? 2 : 1,
    })
    if (path === '/api/v1/inventory/receipts') {
      const receipt = request.postDataJSON()
      const quantity = canonicalDecimal(receipt.quantity)
      receiptBodies.push(receipt)
      inventoryEntries.splice(0, inventoryEntries.length,
        {
          id: 'entry-existing', itemId: item.id, itemName: item.name,
          locationId: location.id, locationName: location.name,
          type: 'BATCH', quantity: '2', receivedAt: '2026-07-01T00:00:00Z',
          productionDate: null, expirationDate: '2026-07-20',
          batchNumber: 'B-EXISTING', customAttributes: {}, archived: false, version: 0,
        },
        {
          id: 'entry-new', itemId: item.id, itemName: item.name,
          locationId: location.id, locationName: location.name,
          type: receipt.type, quantity, receivedAt: receipt.receivedAt,
          productionDate: receipt.productionDate, expirationDate: receipt.expirationDate,
          batchNumber: receipt.batchNumber, customAttributes: {}, archived: false, version: 0,
        },
      )
      return json({ id: 'entry-new', type: 'BATCH', quantity, locationId: location.id, version: 0 }, 201)
    }
    if (path === '/api/v1/reminders') return json({ content: [], page: 0, size: 50, total: 0 })
    if (path === '/api/v1/categories') return json([{ id:'category-1',parentId:null,name:'食品',defaultInventoryType:'BATCH',attributeSchema:[],version:0,archived:false }])
    if (path === '/api/v1/inventory/entries') return json({ items: inventoryEntries, page: 0, size: 50, total: inventoryEntries.length })
    if (path === '/api/v1/inventory/entries/entry-new/movements') return json([{
      id: 'movement-new', type: 'RECEIVE', quantityDelta: inventoryEntries.find(entry => entry.id === 'entry-new')?.quantity,
      actorAccountId: account.id, actorDisplayName: account.displayName, requestId: 'e2e-receive', occurredAt: '2026-07-15T00:00:00Z',
    }])
    if (path === '/api/v1/inventory/entries/entry-existing/movements') return json([{
      id: 'movement-existing', type: 'RECEIVE', quantityDelta: '2', actorAccountId: account.id,
      actorDisplayName: account.displayName, requestId: 'e2e-existing', occurredAt: '2026-07-01T00:00:00Z',
    }])
    return json({ code: 'E2E_FIXTURE_MISSING', detail: path }, 404)
  })
  return { receiptBodies }
}

export async function emitScan(page: Page, payload: string) {
  await page.evaluate(value => window.dispatchEvent(new CustomEvent('stocket:test-scan', { detail: value })), payload)
}

export async function reachDetailsStep(page: Page) {
  await page.goto('/receive')
  await page.getByRole('button', { name: '扫描商品条码' }).click()
  await page.getByRole('dialog', { name: '扫描条码或位置码' }).waitFor()
  await emitScan(page, '690001')
  await page.getByText('已选择：鲜牛奶').waitFor()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '扫描位置码' }).click()
  await page.getByRole('dialog', { name: '扫描条码或位置码' }).waitFor()
  await emitScan(page, 'stocket:location:FRIDGE')
  await page.getByText('位置：厨房 > 冰箱').waitFor()
}
