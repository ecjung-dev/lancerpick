import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-my-products', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { MyProductsPage } = await server.ssrLoadModule('/src/features/products/pages/MyProductsPage.tsx')
const { getMyProducts } = await server.ssrLoadModule('/src/features/products/api.ts')
const { myProductsReturnPath } = await server.ssrLoadModule('/src/features/products/myProductsNavigation.ts')
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
after(() => server.close())
test('own API uses session cookies and abort signal without a client seller ID', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/me/products?page=2&size=20')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.signal, controller.signal)
    return Response.json({ items: [{ id: '9007199254740993' }], page: 2 })
  }
  assert.equal((await getMyProducts(2, controller.signal)).items[0].id, '9007199254740993')
})
test('invalid page never reaches API and server failure never becomes an empty list', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('error', { status: 500 }) }
  for (const page of [-1, 1.5, 2147483648, NaN]) await assert.rejects(getMyProducts(page))
  assert.equal(calls, 0)
  await assert.rejects(getMyProducts(0), error => error.status === 500)
  assert.equal(calls, 1)
})
function render(url, page, items, totalPages = 3) {
  const client = new QueryClient()
  client.setQueryData(['my-products-session'], { id: '3' })
  client.setQueryData(['products', 'mine', '3', page], { items, page, size: 20, totalElements: items.length ? 41 : 1, totalPages })
  try { return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] }, createElement(QueryClientProvider, { client }, createElement(MyProductsPage)))) }
  finally { client.clear() }
}
const item = { id: '9007199254740993', title: '내 숨김 상품', category: { id: '2', name: '분류' }, seller: { id: '3', displayName: '나' }, priceAmount: 50000, durationDays: 3, saleStatus: 'STOPPED', isHidden: true, version: 1, createdAt: '2026-11-04T00:00:00Z' }
test('direct list page restores URL page and shows stopped/hidden state with private action links', () => {
  const html = render('/mypage/products?page=2', 2, [item])
  assert.match(html, /현재 요청 3페이지/)
  assert.match(html, /관리자 숨김/)
  assert.match(html, /판매 중지/)
  assert.match(html, /\/mypage\/products\/9007199254740993\/edit/)
  assert.match(html, /\/mypage\/products\/9007199254740993\/sale-status/)
  assert.doesNotMatch(html, /href="\/products\/9007199254740993"/)
})
test('wrong account data is hidden and out-of-range pages offer recovery', () => {
  const wrong = render('/mypage/products', 0, [{ ...item, seller: { id: '4' } }])
  assert.match(wrong, /로그인 계정을 다시 확인/)
  assert.doesNotMatch(wrong, /내 숨김 상품/)
  const beyond = render('/mypage/products?page=99', 99, [], 1)
  assert.match(beyond, /첫 페이지로/)
  assert.match(beyond, /이 페이지에 상품이 없어요/)
})
test('return path keeps only my-product list routes and query state', () => {
  assert.equal(myProductsReturnPath('/mypage/products?page=2'), '/mypage/products?page=2')
  for (const path of ['//evil.invalid', '/orders', undefined]) assert.equal(myProductsReturnPath(path), '/mypage/products')
})
