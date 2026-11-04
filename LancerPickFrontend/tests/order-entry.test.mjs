import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-order-entry', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { OrderCreatePage } = await server.ssrLoadModule('/src/features/orders/pages/OrderCreatePage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(() => server.close())
function render(memberId) {
  const client = new QueryClient()
  client.setQueryData(['order-entry-session'], { id: memberId, displayName: '회원' })
  client.setQueryData(['products', 'detail', '1'], { id: '1', title: '상품', seller: { id: '2', displayName: '판매자' }, priceAmount: 50000, durationDays: 3, scopeDescription: '범위', preparationDescription: '준비', version: 1 })
  try { return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/products/1/order'] }, createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path: '/products/:id/order', element: createElement(OrderCreatePage) }))))) }
  finally { client.clear() }
}
test('order entry displays requirements, server price and the buyer order recovery link', () => {
  const html = render('3')
  assert.match(html, /요구사항/)
  assert.match(html, /50,000원/)
  assert.match(html, /주문 요청 보내기/)
  assert.match(html, /\/orders\?side=BUYER/)
})
test('own product entry never renders the order form', () => {
  const html = render('2')
  assert.match(html, /내 상품에는 주문할 수 없어요/)
  assert.doesNotMatch(html, /textarea|주문 요청 보내기/)
})

test('expired session returns to this product order form after login', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  const query = client.getQueryCache().build(client, { queryKey: ['order-entry-session'], queryFn: async () => undefined })
  query.setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(401, {
    code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', fieldErrors: [],
  }) })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/products/1/order'] },
      createElement(QueryClientProvider, { client },
        createElement(Routes, null, createElement(Route, { path: '/products/:id/order', element: createElement(OrderCreatePage) })))))
    assert.match(html, /href="\/login\?next=%2Fproducts%2F1%2Forder"/)
    assert.doesNotMatch(html, /주문 요청 보내기/)
  } finally { client.clear() }
})
