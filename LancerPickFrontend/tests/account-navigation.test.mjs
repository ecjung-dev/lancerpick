import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-account-navigation',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { WalletPreviewPage, ConnectedWalletPage } = await server.ssrLoadModule('/src/features/wallet/pages/WalletPage.tsx')
const { SettlementPage } = await server.ssrLoadModule('/src/features/settlements/pages/SettlementPage.tsx')
const { OrderListPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderListPage.tsx')
const { OrderDetailPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderDetailPage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

test('wallet content leaves account navigation to the shared layout', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(WalletPreviewPage)))
  assert.match(html, /적립금 · 이용 내역/)
  assert.doesNotMatch(html, /aria-label="마이페이지 메뉴"|TEST CREDIT|테스트 적립금/)
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
  assert.match(html, /<h1>판매 내역<\/h1>/)
  assert.match(html, /SELLER saved page 2/)
  assert.match(html, /3페이지/)
  assert.match(html, /href="\/orders\/9007199254740993\?side=SELLER&amp;page=2"/)
})

test('invalid side or page uses the buyer first page rather than an invalid API query', () => {
  for (const page of ['-1', '1.5', '2147483648', 'abc']) {
    const html = renderOrders(`/orders?side=invalid&page=${page}`, 'BUYER', 0)
    assert.match(html, /<h1>구매 내역<\/h1>/)
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

test('direct cancellation explains whether a paid amount will be refunded', () => {
  for (const [status, payment, expected, absent] of [
    ['REQUESTED', null, /결제 전 주문은 적립금 차감·환불 없이 취소됩니다/, /전액 적립금 환불과 함께 취소됩니다/],
    ['AWAITING_PAYMENT', null, /결제 전 주문은 적립금 차감·환불 없이 취소됩니다/, /전액 적립금 환불과 함께 취소됩니다/],
    ['IN_PROGRESS', { status: 'PAID', amount: 1000, paidAt: '2026-11-04T00:00:00Z', refundedAt: null },
      /결제된 주문은 전액 적립금 환불과 함께 취소됩니다/, /결제 전 주문은 적립금 차감·환불 없이 취소됩니다/],
  ]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(['wallet', 'order', '1'], {
      id: '1', status, priceAmount: 1000, requirements: '요구사항',
      buyer: { id: '2', displayName: '구매자' }, seller: { id: '3', displayName: '판매자' },
      productSnapshot: { title: '상품', description: '설명', scopeDescription: '범위', preparationDescription: '준비', productVersion: 0 },
      termsSnapshot: '거래 규칙', durationDays: 1, originalDueAt: null, revisionDueAt: null, currentDueAt: null,
      allowedActions: ['CANCEL'], deliveries: [], revisionRequest: null, histories: [], payment, review: null, cancellationRequests: [],
    })
    try {
      const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders/1?side=BUYER'] },
        createElement(QueryClientProvider, { client },
          createElement(Routes, null, createElement(Route, { path: '/orders/:id', element: createElement(OrderDetailPage) })))))
      assert.match(html, expected)
      assert.doesNotMatch(html, absent)
    } finally { client.clear() }
  }
})

function renderFinance(kind, url, page, empty = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const orderId = '9007199254740993'
  const createdAt = '2026-11-04T00:00:00Z'
  const items = kind === 'wallet' ? [
    { id: '1', orderId, type: 'PAYMENT', amountDelta: -999, balanceAfter: 999001, createdAt },
    { id: '2', orderId, type: 'REFUND', amountDelta: 999, balanceAfter: 1000000, createdAt },
    { id: '3', orderId: null, type: 'INITIAL_GRANT', amountDelta: 1000000, balanceAfter: 1000000, createdAt },
    { id: '4', orderId: null, type: 'TOPUP', amountDelta: 50000, balanceAfter: 1050000, createdAt },
  ] : [{ id: '1', orderId, grossAmount: 999, feeRateBps: 1000, feeAmount: 99, sellerAmount: 900, createdAt }]
  const data = { items: empty ? [] : items, page, size: 20, totalElements: 60, totalPages: 3 }
  if (kind === 'wallet') {
    client.setQueryData(['wallet', 'member', 'balance'], { balance: 1050000, currency: 'KRW', isTest: true })
    client.setQueryData(['wallet', 'member', 'entries', { page, size: 20 }], data)
  } else {
    client.setQueryData(['wallet', 'settlements', page], data)
    client.setQueryData(['wallet', 'settlement-summary'], { completedCount: 60, grossAmount: 59940, feeAmount: 5940, sellerAmount: 54000 })
  }
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
      createElement(QueryClientProvider, { client }, kind === 'wallet'
        ? createElement(ConnectedWalletPage, { sessionKey: 'member' }) : createElement(SettlementPage))))
  } finally { client.clear() }
}

test('wallet restores URL page and links only entries associated with orders', () => {
  const html = renderFinance('wallet', '/mypage?page=2', 2)
  assert.match(html, /3 \/ 3/)
  assert.equal((html.match(/href="\/orders\/9007199254740993"/g) ?? []).length, 2)
  assert.doesNotMatch(html, /href="\/orders\/(null|undefined)"/)
  assert.match(html, /가입 적립금/)
  assert.match(html, /적립금 충전/)
})

test('settlements restore URL page and preserve large order IDs in detail links', () => {
  const html = renderFinance('settlements', '/mypage/settlements?page=2', 2)
  assert.match(html, /3페이지/)
  assert.match(html, /href="\/orders\/9007199254740993"/)
  assert.match(html, /판매 수익 900원/)
  assert.match(html, /구매 적립금으로 지급됩니다/)
  assert.match(html, /기존 거래의 수익은 별도 기록으로 유지/)
  assert.doesNotMatch(html, /구매 적립금과 별도로 관리/)
})

test('finance lists reject invalid page values and keep the first page available', () => {
  for (const kind of ['wallet', 'settlements']) {
    for (const page of ['-1', '1.5', '2147483648', 'abc']) {
      const html = renderFinance(kind, `/mypage?page=${page}`, 0)
      assert.match(html, /주문 #9007199254740993 상세 보기/)
      assert.match(html, kind === 'wallet' ? /1 \/ 3/ : /1페이지/)
    }
  }
})

test('out-of-range empty finance pages provide a first-page recovery action', () => {
  for (const kind of ['wallet', 'settlements']) {
    const html = renderFinance(kind, '/mypage?page=8', 8, true)
    assert.match(html, /첫 페이지로/)
    assert.doesNotMatch(html, /9 \/ 3|9페이지/)
  }
})

test('preview order numbers never lead to real orders', () => {
  const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(WalletPreviewPage)))
  assert.doesNotMatch(html, /href="\/orders\/\d+"/)
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
      if (status === 401) assert.match(html, /href="\/login\?next=%2Forders%3Fside%3DBUYER%26status%3DCOMPLETED"/)
      else assert.match(html, /다시 조회/)
    } finally { client.clear() }
  }
})

test('expired order detail session returns to the same order and list filters after login', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  const query = client.getQueryCache().build(client, { queryKey: ['wallet', 'order', '1'], queryFn: async () => undefined })
  query.setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(401, {
    code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', fieldErrors: [],
  }) })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders/1?side=SELLER&status=COMPLETED&page=2'] },
      createElement(QueryClientProvider, { client },
        createElement(Routes, null, createElement(Route, { path: '/orders/:id', element: createElement(OrderDetailPage) })))))
    assert.match(html, /href="\/login\?next=%2Forders%2F1%3Fside%3DSELLER%26status%3DCOMPLETED%26page%3D2"/)
    assert.match(html, /href="\/orders\?side=SELLER&amp;status=COMPLETED&amp;page=2"/)
  } finally { client.clear() }
})


function renderDeliveryLink(resultUrl) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'order', '1'], {
    id: '1', status: 'AWAITING_CONFIRMATION', priceAmount: 1000, requirements: 'requirements',
    buyer: { id: '2', displayName: 'buyer' }, seller: { id: '3', displayName: 'seller' },
    productSnapshot: { title: 'product', description: 'description', scopeDescription: 'scope', preparationDescription: 'prep', productVersion: 0 },
    termsSnapshot: 'terms', durationDays: 1, originalDueAt: null, revisionDueAt: null, currentDueAt: null,
    allowedActions: [], deliveries: [{ id: '10', sequenceNo: 1, description: 'delivery', resultUrl, submittedAt: '2026-11-04T00:00:00Z' }],
    revisionRequest: null, histories: [], payment: null, review: null, cancellationRequests: [],
  })
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/orders/1'] },
      createElement(QueryClientProvider, { client },
        createElement(Routes, null, createElement(Route, { path: '/orders/:id', element: createElement(OrderDetailPage) })))))
  } finally { client.clear() }
}

test('delivery links preserve HTTPS schemes regardless of letter case', () => {
  for (const scheme of ['https', 'HTTPS', 'hTtPs']) {
    const url = `${scheme}://example.com/result?file=1&version=2`
    const html = renderDeliveryLink(url)
    assert.ok(html.includes(`href="${scheme}://example.com/result?file=1&amp;version=2"`))
    assert.match(html, /target="_blank" rel="noopener noreferrer"/)
    assert.match(html, /결과물 확인/)
  }
})

test('delivery result display does not link non-HTTPS schemes', () => {
  for (const url of ['http://example.com/result', 'javascript:alert(1)', 'data:text/html,example', '//example.com/result', 'httpsx://example.com']) {
    assert.doesNotMatch(renderDeliveryLink(url), /결과물 확인/)
  }
})
