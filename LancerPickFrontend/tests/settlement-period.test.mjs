import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { readSettlementFilters, settlementSearch, validSettlementDate } from '../src/features/settlements/filters.ts'
import { koreanToday, recentMonths } from '../src/shared/lib/dateRange.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-settlement-period',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { SettlementPage } = await server.ssrLoadModule('/src/features/settlements/pages/SettlementPage.tsx')
const { getSettlements, getSettlementSummary } = await server.ssrLoadModule('/src/features/settlements/api.ts')
after(async () => { await server.close() })

test('period URL validates dates and sort without losing pagination or applying invalid ranges', () => {
  const filters = readSettlementFilters(new URLSearchParams('from=2026-11-01&to=2026-11-09&sort=oldest&page=2'))
  assert.deepEqual(filters, { from: '2026-11-01', to: '2026-11-09', sort: 'oldest', page: 2 })
  assert.equal(settlementSearch(filters).toString(), 'from=2026-11-01&to=2026-11-09&sort=oldest&page=2')
  for (const value of ['2026-02-30', '0000-01-01', '10000-01-01', '2026-1-01']) assert.equal(validSettlementDate(value), false)
  for (const value of ['2024-02-29', '1000-01-01', '9999-12-31']) assert.equal(validSettlementDate(value), true)
  assert.deepEqual(readSettlementFilters(new URLSearchParams('from=2026-11-10&to=2026-11-09&sort=price&page=-1')),
    { from: '', to: '', sort: 'latest', page: 0 })
  assert.deepEqual(readSettlementFilters(new URLSearchParams('to=2026-11-09')), { from: '', to: '2026-11-09', sort: 'latest', page: 0 })
})

test('shared date helpers preserve Korean midnight, calendar months and month-end clamping', () => {
  const instant = Date.parse('2026-03-30T15:00:00Z')
  assert.equal(koreanToday(instant), '2026-03-31')
  assert.deepEqual(recentMonths(1, instant), { from: '2026-02-28', to: '2026-03-31' })
  assert.deepEqual(recentMonths(3, instant), { from: '2025-12-31', to: '2026-03-31' })
})

test('list and totals send the same date conditions and never a caller member ID', async () => {
  const original = globalThis.fetch, urls = []
  globalThis.fetch = async (url) => {
    urls.push(url)
    return new Response('{}', { headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const period = { from: '2026-11-01', to: '2026-11-09' }
    await getSettlements(2, 20, undefined, period, 'oldest')
    await getSettlementSummary(undefined, period)
    await getSettlements(0)
    await getSettlementSummary()
  } finally { globalThis.fetch = original }
  assert.deepEqual(urls, [
    '/api/me/settlements?page=2&size=20&from=2026-11-01&to=2026-11-09&sort=oldest',
    '/api/me/settlements/summary?from=2026-11-01&to=2026-11-09',
    '/api/me/settlements?page=0&size=20', '/api/me/settlements/summary',
  ])
})

test('screen restores period and displays condition totals without using the all-time cache', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'settlements', 1, '2026-11-01', '2026-11-09', 'oldest'], {
    items: [{ id: '7', orderId: '9007199254740993', grossAmount: 1000, feeAmount: 100, sellerAmount: 900, createdAt: '2026-11-08T15:00:00Z' }],
    page: 1, size: 20, totalElements: 21, totalPages: 2,
  })
  client.setQueryData(['wallet', 'settlement-summary', '2026-11-01', '2026-11-09'], { completedCount: 21, grossAmount: 21000, feeAmount: 2100, sellerAmount: 18900 })
  client.setQueryData(['wallet', 'settlement-summary', '', ''], { completedCount: 999, grossAmount: 999000, feeAmount: 99900, sellerAmount: 899100 })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/mypage/settlements?from=2026-11-01&to=2026-11-09&sort=oldest&page=1'] },
      createElement(QueryClientProvider, { client }, createElement(SettlementPage))))
    assert.match(html, /type="date"[^>]*value="2026-11-01"/)
    assert.match(html, /type="date"[^>]*value="2026-11-09"/)
    assert.match(html, /value="oldest" selected=""/)
    assert.match(html, /조회 조건 합계/)
    assert.match(html, /총 21건/)
    assert.match(html, /18,900원/)
    assert.doesNotMatch(html, /899,100원/)
    assert.match(html, /2페이지/)
    assert.match(html, /href="\/orders\/9007199254740993"/)
  } finally { client.clear() }
})
