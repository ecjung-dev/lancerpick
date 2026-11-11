import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { orderDetailPresentation } from '../src/features/orders/detailPresentation.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-order-detail-layout',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { OrderDetailPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderDetailPage.tsx')
after(() => server.close())

const order = {
  id: '9007199254740993', policyVersion: 'CONTRACT_V3', productId: '3', status: 'COMPLETED',
  buyer: { id: '1', displayName: '구매자' }, seller: { id: '2', displayName: '판매자' },
  productSnapshot: { title: '보관된 상품명', description: '주문 당시 설명', scopeDescription: '원래 작업 범위',
    preparationDescription: '준비 자료', productVersion: 1 },
  priceAmount: 100001, requirements: '원래 요구사항', termsSnapshot: '확정한 규칙', durationDays: 3,
  currentDueAt: null, originalDueAt: null, revisionDueAt: null, deliveries: [],
  histories: [], cancellationRequests: [], allowedActions: [], review: null, revisionRequest: null,
  payment: { id: '1', amount: 100001, feeRateBps: 1000, status: 'PAID', paidAt: '2026-11-11T00:00:00Z', refundedAt: null },
}

function render({ memberId = '2', memberError = false, orderError = false, change = {} } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['auth', 'me'], { id: memberId, displayName: '회원', role: 'USER', hasSellerProfile: true })
  client.setQueryData(['wallet', 'order', order.id], { ...order, ...change })
  client.setQueryData(['wallet', 'order-funds', order.id], {
    status: 'RELEASED', amount: 100001, heldAmount: 0, refundedAmount: 0,
    feeAmount: 10000, sellerPayout: 90001, paidAt: null, resolvedAt: null,
  })
  client.setQueryData(['wallet', 'order-review-timer', order.id], { status: 'NOT_IN_REVIEW', canStart: false })
  if (memberError) client.getQueryCache().find({ queryKey: ['auth', 'me'] })
    .setState({ status: 'error', error: new Error('회원 확인 실패') })
  if (orderError) client.getQueryCache().find({ queryKey: ['wallet', 'order', order.id] })
    .setState({ status: 'error', error: new Error('주문 접근 거절') })
  try {
    const route = h(Route, { path: '/orders/:id', element: h(OrderDetailPage) })
    const tree = h(QueryClientProvider, { client },
      h(MemoryRouter, { initialEntries: [`/orders/${order.id}?side=sell&page=2`] }, h(Routes, null, route)))
    return renderToStaticMarkup(tree)
  } finally { client.clear() }
}

test('only verified seller gets earnings link and large order ID stays intact in its filter', () => {
  assert.match(render(), /href="\/mypage\/settlements\?q=9007199254740993"/)
  assert.doesNotMatch(render({ memberId: '1' }), /mypage\/settlements/)
  assert.doesNotMatch(render({ memberError: true }), /mypage\/settlements/)
  assert.match(render({ change: { status: 'IN_PROGRESS' } }), /href="\/mypage\/settlements"/)
})

test('tabs retain mounted condition and history panels while progress is initially selected', () => {
  const html = render()
  assert.match(html, /id="order-tab-progress"[^>]*aria-selected="true"/)
  assert.match(html, /id="order-panel-conditions"[^>]*hidden/)
  assert.match(html, /id="order-panel-records"[^>]*hidden/)
  assert.match(html, /원래 요구사항|원래 작업 범위/)
  assert.match(html, /href="\/orders\?side=sell&amp;page=2"/)
})

test('failed order recheck hides cached product and financial data', () => {
  const html = render({ orderError: true })
  assert.match(html, /주문을 확인할 수 없어요/)
  assert.doesNotMatch(html, /보관된 상품명|100,001|90,001|mypage\/settlements/)
})

test('terminal rejection, cancellation and unknown states never appear as a completed progress bar', () => {
  for (const status of ['CANCELLED', 'REJECTED', 'TERMINATED', 'UNRECOGNIZED'])
    assert.equal(orderDetailPresentation({ ...order, status }).stage, null)
  const legacy = orderDetailPresentation({ ...order, policyVersion: 'LEGACY_V1' })
  assert.equal(legacy.steps[1], '수락')
  assert.equal(orderDetailPresentation(order).steps[1], '계약')
})

test('pending cancellation remains prominent even if order is in inspection', () => {
  const value = { ...order, status: 'AWAITING_CONFIRMATION',
    cancellationRequests: [{ id: '1', status: 'PENDING', reason: '사유', requestedAt: '2026-11-11T00:00:00Z' }] }
  assert.equal(orderDetailPresentation(value).pendingCancellation, true)
  assert.match(render({ change: value }), /<details[^>]*open/)
  assert.match(orderDetailPresentation(value).title, /취소 요청/)
})

test('completed banner copy depends on the verified viewer role', () => {
  const done = { ...order, status: 'COMPLETED', cancellationRequests: [] }
  const seller = orderDetailPresentation(done, true).description
  const buyer = orderDetailPresentation(done, false).description
  const unknown = orderDetailPresentation(done).description
  assert.match(seller, /이 주문의 판매 수익/)
  assert.doesNotMatch(buyer, /판매 수익/)
  assert.equal(unknown, buyer)
})
