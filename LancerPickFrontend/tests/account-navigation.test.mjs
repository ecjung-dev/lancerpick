import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-account-navigation',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { WalletPreviewPage } = await server.ssrLoadModule('/src/features/wallet/pages/WalletPage.tsx')
const { OrderListPage } = await server.ssrLoadModule('/src/features/orders/pages/OrderListPage.tsx')
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
  assert.match(html, /href="\/orders\/9007199254740993"/)
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
  const invalid = renderOrders('/orders?side=BUYER&status=UNKNOWN&page=0', 'BUYER', 0)
  assert.match(invalid, /value="" selected=""/)
  assert.match(invalid, /BUYER saved page 0/)
})
