import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-sale-status', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { ProductSaleStatusPage } = await server.ssrLoadModule('/src/features/products/pages/ProductSaleStatusPage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(() => server.close())
function render(client) {
  try { return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/mypage/products/1/sale-status'] }, createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path: '/mypage/products/:id/sale-status', element: createElement(ProductSaleStatusPage) }))))) }
  finally { client.clear() }
}
test('on-sale owner sees stop action and existing order preservation notice', () => {
  const client = new QueryClient()
  client.setQueryData(['product-sale-session'], { id: '2' })
  client.setQueryData(['product-sale-status', '2', '1'], { id: '1', title: '상품', seller: { id: '2' }, saleStatus: 'ON_SALE', isHidden: false, version: 3 })
  const html = render(client)
  assert.match(html, /판매 중지하기/)
  assert.match(html, /이미 생성된 주문과 보관된 거래 조건은 유지/)
  assert.match(html, /\/mypage\/products\/1\/edit/)
})
test('stopped hidden owner sees resume action without promising visibility restoration', () => {
  const client = new QueryClient()
  client.setQueryData(['product-sale-session'], { id: '2' })
  client.setQueryData(['product-sale-status', '2', '1'], { id: '1', title: '상품', seller: { id: '2' }, saleStatus: 'STOPPED', isHidden: true, version: 4 })
  const html = render(client)
  assert.match(html, /판매 재개하기/)
  assert.match(html, /관리자 숨김은 판매 재개로 해제되지 않아요/)
})
test('signed out sale page offers login return and no private status controls', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retryOnMount: false } } })
  client.getQueryCache().build(client, { queryKey: ['product-sale-session'] }).setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인', fieldErrors: [] }) })
  const html = render(client)
  assert.match(html, /\/login\?next=%2Fmypage%2Fproducts%2F1%2Fsale-status/)
  assert.doesNotMatch(html, /판매 중지하기|판매 재개하기/)
})
