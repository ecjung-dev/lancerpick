import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { validateProduct, createProduct, patchProduct, getOwnedProduct } from '../src/features/products/management.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })
const input = { categoryId: '9007199254740993', title: '  상품 😀  ', description: ' 설명\r\n둘째 줄 ', scopeDescription: '범위', preparationDescription: '없음', priceAmount: 50000, durationDays: 3 }
test('normalization uses code points and excludes non-contract fields', () => {
  const result = validateProduct({ ...input, sellerId: '3', isHidden: true })
  assert.equal(result.title, '상품 😀')
  assert.equal(result.description, '설명\n둘째 줄')
  assert.equal(result.sellerId, undefined)
  assert.equal(validateProduct({ ...input, title: '😀'.repeat(100) }).title.length, 200)
  for (const patch of [{ title: '😀'.repeat(101) }, { description: ' ' }, { priceAmount: 0 }, { durationDays: 31 }, { priceAmount: 1.5 }, { categoryId: '9223372036854775808' }]) assert.throws(() => validateProduct({ ...input, ...patch }))
})
test('create and partial patch use CSRF and keep server-owned identity out of requests', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    calls.push({ url, method: options.method, csrf: options.headers.get('X-CSRF-TOKEN'), body: options.body && JSON.parse(options.body) })
    return Response.json({ id: '9007199254740993', version: 1 })
  }
  await createProduct(input)
  await patchProduct('9007199254740993', { title: '수정' }, 0)
  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].csrf, 'test')
  assert.deepEqual(calls[1].body, { title: '수정', expectedVersion: 0 })
  await getOwnedProduct('9007199254740993')
  assert.equal(calls[2].url, '/api/me/products/9007199254740993')
})
test('version conflict is propagated without an automatic overwrite or retry', async () => {
  let count = 0
  globalThis.fetch = async url => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    count++
    return Response.json({ code: 'PRODUCT_VERSION_CONFLICT', message: '변경됨', fieldErrors: [], traceId: 'test' }, { status: 409 })
  }
  await assert.rejects(patchProduct('1', { title: '수정' }, 0), error => error.code === 'PRODUCT_VERSION_CONFLICT')
  assert.equal(count, 1)
})
