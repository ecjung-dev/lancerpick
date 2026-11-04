import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-order-entry', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { OrderCreatePage } = await server.ssrLoadModule('/src/features/orders/pages/OrderCreatePage.tsx')
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
