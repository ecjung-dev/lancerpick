import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-account-navigation',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { WalletPreviewPage } = await server.ssrLoadModule('/src/features/wallet/pages/WalletPage.tsx')
const { OrderListPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderListPage.tsx')
const { OrderDetailPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderDetailPage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

test('mypage exposes buyer, seller and settlement destinations as real links', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(WalletPreviewPage)))
  assert.match(html, /href="\/orders\?side=BUYER"/)
  assert.match(html, /href="\/orders\?side=SELLER"/)
  assert.match(html, /href="\/mypage\/settlements"/)
  assert.doesNotMatch(html, /구매 내역 <small>준비 중/)
})

function renderOrders(url, side, page, status = '') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'orders', side, status, page], {
    items: [{ id: '9007199254740993', productTitle: `${side} saved page ${page}`, priceAmount: 999,
      status: 'COMPLETED', counterparty: { id: '2', displayName: 'counterparty' }, createdAt: '2026-11-03T00:00:00Z', currentDueAt: null }],
    page, size: 20, totalElements: 60, totalPages: 3,
  })
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
      createElement(QueryClientProvider, { client }, createElement(OrderListPage))))
  } finally { client.clear() }
}

test('direct seller URL restores selected tab and page without converting order IDs', () => {
  const html = renderOrders('/orders?side=SELLER&page=2', 'SELLER', 2)
  assert.match(html, /aria-pressed="true">판매 내역/)
  assert.match(html, /SELLER saved page 2/)
  assert.match(html, /3페이지/)
  assert.match(html, /href="\/orders\/9007199254740993\?side=SELLER&amp;page=2"/)
})

test('invalid side or page uses the buyer first page rather than an invalid API query', () => {
  for (const page of ['-1', '1.5', '2147483648', 'abc']) {
    const html = renderOrders(`/orders?side=invalid&page=${page}`, 'BUYER', 0)
    assert.match(html, /aria-pressed="true">구매 내역/)
    assert.match(html, /BUYER saved page 0/)
    assert.match(html, /1페이지/)
  }
})

test('direct filtered URL restores status and page while invalid status selects all', () => {
  const filtered = renderOrders('/orders?side=SELLER&status=COMPLETED&page=2', 'SELLER', 2, 'COMPLETED')
  assert.match(filtered, /value="COMPLETED" selected=""/)
  assert.match(filtered, /SELLER saved page 2/)
  assert.match(filtered, /3페이지/)
  assert.match(filtered, /href="\/orders\/9007199254740993\?side=SELLER&amp;status=COMPLETED&amp;page=2"/)
  const invalid = renderOrders('/orders?side=BUYER&status=UNKNOWN&page=0', 'BUYER', 0)
  assert.match(invalid, /value="" selected=""/)
  assert.match(invalid, /BUYER saved page 0/)
})

test('order detail returns to the filtered list and displays a Korean status', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'order', '1'], {
    id: '1', status: 'COMPLETED', priceAmount: 1000, requirements: '요구사항',
    buyer: { id: '2', displayName: '구매자' }, seller: { id: '3', displayName: '판매자' },
    productSnapshot: { title: '상품', description: '설명', scopeDescription: '범위', preparationDescription: '준비', productVersion: 0 },
    termsSnapshot: '거래 규칙', durationDays: 1, originalDueAt: null, revisionDueAt: null, currentDueAt: null,
    allowedActions: [], deliveries: [], revisionRequest: null, histories: [], payment: null, review: null, cancellationRequests: [],
  })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders/1?side=SELLER&status=COMPLETED&page=2'] },
      createElement(QueryClientProvider, { client },
        createElement(Routes, null, createElement(Route, { path: '/orders/:id', element: createElement(OrderDetailPage) })))))
    assert.match(html, /href="\/orders\?side=SELLER&amp;status=COMPLETED&amp;page=2"/)
    assert.match(html, /주문 #1 · 구매 확정/)
  } finally { client.clear() }
})

test('empty filtered order list explains that no orders match', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'orders', 'BUYER', 'COMPLETED', 0], { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders?side=BUYER&status=COMPLETED'] },
      createElement(QueryClientProvider, { client }, createElement(OrderListPage))))
    assert.match(html, /이 조건에 맞는 거래 내역이 없습니다/)
    assert.match(html, /value="COMPLETED" selected=""/)
  } finally { client.clear() }
})

test('order detail failure shows server error and a route back to the filtered list', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  const query = client.getQueryCache().build(client, { queryKey: ['wallet', 'order', '1'], queryFn: async () => undefined })
  query.setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(502, {
    code: 'HTTP_ERROR', message: '요청을 처리하지 못했습니다. 다시 시도해 주세요.', fieldErrors: [],
  }) })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders/1?side=SELLER&status=COMPLETED&page=2'] },
      createElement(QueryClientProvider, { client },
        createElement(Routes, null, createElement(Route, { path: '/orders/:id', element: createElement(OrderDetailPage) })))))
    assert.match(html, /요청을 처리하지 못했습니다/)
    assert.match(html, /href="\/orders\?side=SELLER&amp;status=COMPLETED&amp;page=2"/)
    assert.doesNotMatch(html, /접근 권한이나 주문 상태/)
  } finally { client.clear() }
})

test('order list separates expired login from server failure while keeping filters', () => {
  for (const [status, message, expected] of [
    [401, '로그인이 필요합니다.', /로그인 후 거래 내역을 확인할 수 있습니다/],
    [502, '요청을 처리하지 못했습니다. 다시 시도해 주세요.', /요청을 처리하지 못했습니다/],
  ]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
    const query = client.getQueryCache().build(client, { queryKey: ['wallet', 'orders', 'BUYER', 'COMPLETED', 0], queryFn: async () => undefined })
    query.setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(status, { code: 'HTTP_ERROR', message, fieldErrors: [] }) })
    try {
      const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders?side=BUYER&status=COMPLETED'] },
        createElement(QueryClientProvider, { client }, createElement(OrderListPage))))
      assert.match(html, expected)
      assert.match(html, /value="COMPLETED" selected=""/)
      if (status === 401) assert.match(html, /href="\/login"/)
      else assert.match(html, /다시 조회/)
    } finally { client.clear() }
  }
})
