import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createServer } from 'vite'

// Vite가 실제 앱과 같은 방식으로 TS 모듈을 읽는다. HTTP 서버는 열지 않는다.
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-product-search',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { getCategories, getProducts, getProductDetail } = await server.ssrLoadModule('/src/features/products/api.ts')
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
after(async () => { await server.close() })
const json = (body) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

test('detail preserves large string IDs, cookies and cancellation', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/products/9007199254740993')
    assert.equal(options.signal, controller.signal)
    assert.equal(options.credentials, 'same-origin')
    return json({ id: '9007199254740993', description: '<script>text</script>' })
  }
  assert.equal((await getProductDetail('9007199254740993', controller.signal)).id, '9007199254740993')
})

test('invalid detail IDs never reach network; missing and server errors remain errors', async () => {
  globalThis.fetch = async () => { throw new Error('unexpected network') }
  for (const id of ['0', '-1', 'abc', '1.5', '9223372036854775808', '../me']) {
    await assert.rejects(getProductDetail(id), /상품 번호/)
  }
  for (const status of [404, 500]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'RESOURCE_NOT_FOUND', message: '없음' }),
      { status, headers: { 'Content-Type': 'application/json' } })
    await assert.rejects(getProductDetail('1'), error => error.status === status)
  }
})

test('categories use the real public API and preserve cancellation', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/categories')
    assert.equal(options.signal, controller.signal)
    assert.equal(options.credentials, 'same-origin')
    return json([{ id: '9007199254740993', name: '분류', displayOrder: 0 }])
  }
  assert.equal((await getCategories(controller.signal))[0].id, '9007199254740993')
})

test('product search preserves literal keywords, string category IDs and page parameters', async () => {
  const search = { keyword: '로고 & 100%_! 😀', categoryId: '9007199254740993', page: 2, size: 4,
    minPrice: '10000', maxPrice: '50000', maxDurationDays: '3', sort: 'PRICE_ASC', sellerId: '9007199254740995' }
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    const request = new URL(url, 'http://localhost')
    assert.equal(request.pathname, '/api/products')
    assert.deepEqual(Object.fromEntries(request.searchParams), {
      keyword: search.keyword, categoryId: search.categoryId, page: '2', size: '4',
      minPrice: '10000', maxPrice: '50000', maxDurationDays: '3', sort: 'PRICE_ASC',
      sellerId: '9007199254740995',
    })
    assert.equal(options.signal, controller.signal)
    return json({ items: [], page: 2, size: 4, totalElements: 0, totalPages: 0 })
  }
  assert.equal((await getProducts(search, controller.signal)).page, 2)
})

test('unfiltered requests omit optional filters and preserve empty successful results', async () => {
  globalThis.fetch = async (url) => {
    assert.equal(url, '/api/products?page=0&size=4')
    return json({ items: [], page: 0, size: 4, totalElements: 0, totalPages: 0 })
  }
  assert.deepEqual(await getProducts({ keyword: '', categoryId: '', page: 0, size: 4 }),
    { items: [], page: 0, size: 4, totalElements: 0, totalPages: 0 })
})

test('server failure and request cancellation never turn into demo products', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('unavailable', { status: 503 }) }
  await assert.rejects(getProducts({ keyword: '', categoryId: '', page: 0, size: 4 }),
    (error) => error.status === 503)
  assert.equal(calls, 1)
  globalThis.fetch = async () => { throw new DOMException('cancelled', 'AbortError') }
  await assert.rejects(getCategories(), (error) => error.name === 'AbortError')
})
