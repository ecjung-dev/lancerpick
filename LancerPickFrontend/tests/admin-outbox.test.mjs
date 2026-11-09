import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getOutboxIssues, outboxErrorLabel, readOutboxFilter } from '../src/features/admin/outboxApi.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin-outbox',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminOutboxPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminOutboxPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const orderId = '9007199254740993'

test('list request is a GET with only documented parameters', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => { sent.push([url, options.method, options.body]); return json(empty) }
  await getOutboxIssues({ status: 'FAILED', orderId: '' }, 0)
  await getOutboxIssues({ status: 'RETRYING', orderId }, 2)
  assert.deepEqual(sent, [
    ['/api/admin/outbox-events?page=0&size=20&status=FAILED', 'GET', undefined],
    [`/api/admin/outbox-events?page=2&size=20&status=RETRYING&orderId=${orderId}`, 'GET', undefined],
  ])
})

test('URL filter keeps only documented values and defaults to FAILED', () => {
  assert.deepEqual(readOutboxFilter(new URLSearchParams('')), { status: 'FAILED', orderId: '' })
  assert.deepEqual(readOutboxFilter(new URLSearchParams(`status=RETRYING&orderId=${orderId}`)), { status: 'RETRYING', orderId })
  assert.deepEqual(readOutboxFilter(new URLSearchParams('status=retrying&orderId=0')), { status: 'FAILED', orderId: '' })
  assert.deepEqual(readOutboxFilter(new URLSearchParams('status=PENDING&orderId=9223372036854775808')), { status: 'FAILED', orderId: '' })
})

test('error codes read in Korean, mark permanent failures and keep unknown codes as-is', () => {
  assert.equal(outboxErrorLabel('INVALID_RECIPIENT'), '받는 사람이 주문 당사자가 아님 · 재시도해도 같은 결과 (INVALID_RECIPIENT)')
  assert.equal(outboxErrorLabel('LOCK_UNAVAILABLE'), '잠금 대기 시간 초과 (LOCK_UNAVAILABLE)')
  assert.equal(outboxErrorLabel('java.lang.IllegalStateException'), 'java.lang.IllegalStateException')
  assert.equal(outboxErrorLabel(null), '기록 없음')
})

function render(url, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null,
      createElement(Route, { path: '/admin/outbox', element: createElement(AdminOutboxPage) })))))
}
const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })

test('failed tab lists events with order link, Korean reason and no retry button', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'outbox', { status: 'FAILED', orderId: '' }, 0], { ...empty, totalElements: 2, totalPages: 1, items: [
      { id: '41', eventId: 'e1', type: 'ORDER_ACCEPTED', aggregateType: 'ORDER', aggregateId: orderId, status: 'FAILED', attemptCount: 5,
        lastErrorCode: 'PROCESSING_FAILED', nextAttemptAt: null, createdAt: '2026-11-09T00:00:00Z' },
      { id: '40', eventId: 'e2', type: 'MEMBER_TEST', aggregateType: 'MEMBER', aggregateId: '7', status: 'FAILED', attemptCount: 1,
        lastErrorCode: 'UNSUPPORTED_EVENT', nextAttemptAt: null, createdAt: '2026-11-09T00:00:00Z' },
    ] })
    const html = render('/admin/outbox', client)
    assert.match(html, /aria-current="page"[^>]*>처리 중단</)
    assert.match(html, /총 2건/)
    assert.match(html, new RegExp(`href="/admin/finance/orders/${orderId}"`))
    assert.match(html, /MEMBER #7/, '주문이 아닌 대상은 링크 없이 표시')
    assert.match(html, /처리 중 오류 \(PROCESSING_FAILED\) · 시도 5회/)
    assert.doesNotMatch(html, /다음 시도/)
    assert.doesNotMatch(html, /<button[^>]*>(재처리|다시 보내기|재시도)/, '조회 화면에는 실행 버튼이 없다')
  } finally { client.clear() }
})

test('retrying tab restores the order filter and shows the next attempt time', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'outbox', { status: 'RETRYING', orderId }, 0], { ...empty, totalElements: 1, totalPages: 1, items: [
      { id: '42', eventId: 'e3', type: 'ORDER_PAID', aggregateType: 'ORDER', aggregateId: orderId, status: 'RETRYING', attemptCount: 2,
        lastErrorCode: 'LOCK_UNAVAILABLE', nextAttemptAt: '2026-11-09T00:05:00Z', createdAt: '2026-11-09T00:00:00Z' },
    ] })
    const html = render(`/admin/outbox?status=RETRYING&orderId=${orderId}`, client)
    assert.match(html, /aria-current="page"[^>]*>재시도 대기</)
    assert.match(html, new RegExp(`value="${orderId}"`))
    assert.match(html, /다음 시도/)
    assert.match(html, /잠금 대기 시간 초과/)
  } finally { client.clear() }
})
