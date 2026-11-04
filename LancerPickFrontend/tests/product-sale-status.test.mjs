import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { changeProductSaleStatus } from '../src/features/products/saleStatus.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })

test('sale status uses the dedicated PATCH with string ID, CSRF and only two contract fields', async () => {
  let request
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    request = { url, method: options.method, body: JSON.parse(options.body), csrf: options.headers.get('X-CSRF-TOKEN'), credentials: options.credentials }
    return Response.json({ id: '9007199254740993', saleStatus: 'STOPPED', version: 4 })
  }
  await changeProductSaleStatus('9007199254740993', { saleStatus: 'STOPPED', expectedVersion: 3, sellerId: '2', isHidden: false })
  assert.deepEqual(request, { url: '/api/products/9007199254740993/sale-status', method: 'PATCH', body: { saleStatus: 'STOPPED', expectedVersion: 3 }, csrf: 'test', credentials: 'same-origin' })
})
test('invalid status or version never reaches the server', () => {
  globalThis.fetch = async () => { throw new Error('unexpected network') }
  for (const input of [{ saleStatus: 'DELETED', expectedVersion: 0 }, { saleStatus: 'STOPPED', expectedVersion: -1 }, { saleStatus: 'STOPPED', expectedVersion: 1.5 }]) assert.throws(() => changeProductSaleStatus('1', input))
  assert.throws(() => changeProductSaleStatus('9223372036854775808', { saleStatus: 'ON_SALE', expectedVersion: 0 }))
})
test('conflict and network failure are surfaced without replay', async () => {
  for (const status of [409, 500]) {
    clearCsrfToken()
    let calls = 0
    globalThis.fetch = async url => {
      if (url === '/api/auth/csrf') return Response.json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
      calls++
      return Response.json({ code: 'PRODUCT_VERSION_CONFLICT', message: '확인 필요', fieldErrors: [], traceId: 'test' }, { status })
    }
    await assert.rejects(changeProductSaleStatus('1', { saleStatus: 'ON_SALE', expectedVersion: 0 }), error => error.status === status)
    assert.equal(calls, 1)
  }
})
