import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import {
  decisionLabel, getContractPdf, getOrderFinance, getPayments, getRecoveryJobs, getTopups, readPaymentFilter, readTopupFilter,
} from '../src/features/admin/financeApi.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin-finance',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminFinancePage } = await server.ssrLoadModule('/src/features/admin/pages/AdminFinancePage.tsx')
const { AdminOrderFinancePage } = await server.ssrLoadModule('/src/features/admin/pages/AdminOrderFinancePage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const orderId = '9007199254740993'

// ---------- API ----------

test('read requests are GETs with only documented, non-empty query parameters', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => { sent.push([url, options.method, options.body]); return json(empty) }
  await getTopups({ memberId: '5', status: 'UNKNOWN' }, 1)
  await getTopups({ memberId: '', status: '' }, 0)
  await getPayments({ orderId, buyerId: '', sellerId: '6', status: 'REFUNDED', policyVersion: 'CONTRACT_V3' }, 0)
  await getRecoveryJobs(2)
  await getOrderFinance(orderId)
  await getContractPdf(orderId)
  assert.deepEqual(sent, [
    ['/api/admin/finance/topups?page=1&size=20&memberId=5&status=UNKNOWN', 'GET', undefined],
    ['/api/admin/finance/topups?page=0&size=20', 'GET', undefined],
    [`/api/admin/finance/payments?page=0&size=20&orderId=${orderId}&sellerId=6&status=REFUNDED&policyVersion=CONTRACT_V3`, 'GET', undefined],
    ['/api/admin/finance/recovery-jobs?page=2&size=20', 'GET', undefined],
    [`/api/admin/finance/orders/${orderId}`, 'GET', undefined],
    [`/api/admin/orders/${orderId}/contract-pdf`, 'GET', undefined],
  ])
})

test('invalid order IDs never reach the network', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({}) }
  for (const bad of ['', '0', 'abc', '-1', '9223372036854775808']) {
    await assert.rejects(getOrderFinance(bad))
    await assert.rejects(getContractPdf(bad))
  }
  assert.equal(calls, 0)
})

test('URL filters keep only documented values (exact case, positive IDs)', () => {
  assert.deepEqual(readTopupFilter(new URLSearchParams('memberId=5&status=FAILED')), { memberId: '5', status: 'FAILED' })
  assert.deepEqual(readTopupFilter(new URLSearchParams('memberId=0&status=failed')), { memberId: '', status: '' })
  assert.deepEqual(readPaymentFilter(new URLSearchParams('orderId=7&buyerId=x&sellerId=01&status=paid&policyVersion=V3')),
    { orderId: '7', buyerId: '', sellerId: '', status: '', policyVersion: '' })
  assert.deepEqual(readPaymentFilter(new URLSearchParams('status=REFUNDED&policyVersion=LEGACY_V1')),
    { orderId: '', buyerId: '', sellerId: '', status: 'REFUNDED', policyVersion: 'LEGACY_V1' })
})

test('candidate labels never read as permission and keep the original code', () => {
  assert.match(decisionLabel(true, 'NONE'), /재처리 후보 · 실행은 아직 지원하지 않아요/)
  assert.match(decisionLabel(true, null, 'RETRY_FAILED'), /실패 후 재생성/)
  assert.equal(decisionLabel(false, 'LEASE_ACTIVE'), '후보 아님: 다른 작업이 처리 중 (LEASE_ACTIVE)')
  assert.equal(decisionLabel(false, 'SOMETHING_NEW'), '후보 아님: SOMETHING_NEW')
})

// ---------- 화면 ----------

function render(url, path, element, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element })))))
}
const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })

test('order finance shows missing records as "기록 없음", not 0원, and PDF metadata without paths', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'finance', 'order', orderId], {
      orderId, buyerId: '5', sellerId: '6', policyVersion: 'CONTRACT_V3', orderStatus: 'IN_PROGRESS', orderAmount: 600001,
      payment: { id: '1', orderId, buyerId: '5', sellerId: '6', policyVersion: 'CONTRACT_V3', orderStatus: 'IN_PROGRESS', status: 'PAID',
        amount: 600001, refundedAmount: 0, paidAt: '2026-11-09T00:00:00Z', refundedAt: null },
      funds: { paymentId: '1', status: 'HELD', amount: 600001, remainingAmount: 600001, heldAt: '2026-11-09T00:00:00Z', resolvedAt: null },
      settlement: null, closure: null,
    })
    client.setQueryData(['admin', 'finance', 'pdf', orderId], {
      orderId, contractId: '3', version: 2, status: 'SIGNED', currentVersion: true, fileMetadataPresent: true,
      createdAt: '2026-11-08T00:00:00Z', updatedAt: '2026-11-08T01:00:00Z', checkedAt: '2026-11-09T02:00:00Z',
      generationCandidate: { eligible: false, kind: 'NONE', reason: 'ORDER_NOT_WRITABLE' },
    })
    const html = render(`/admin/finance/orders/${orderId}`, '/admin/finance/orders/:id', createElement(AdminOrderFinancePage), client)
    assert.match(html, /주문 #9007199254740993/)
    assert.match(html, /CONTRACT_V3 · 주문 진행 중/, '주문 상태는 한글 라벨')
    assert.match(html, /600,001원/)
    assert.equal(html.match(/기록 없음/g)?.length, 2, '정산·종료 분배가 없으면 0원 대신 기록 없음')
    assert.match(html, /HELD/)
    assert.match(html, /실제 파일 확인은 아님/)
    assert.match(html, /후보 아님: 지금 주문 상태에서는 생성 불가 \(ORDER_NOT_WRITABLE\)/)
    assert.doesNotMatch(html, /<button[^>]*>(재시도|재생성|재확인|재시작)/, '조회 화면에는 실행 버튼이 없다')
  } finally { client.clear() }
})

test('finance page defaults to payments, restores filters from the URL and links to the order view', () => {
  const client = newClient()
  try {
    const filter = { orderId: '', buyerId: '5', sellerId: '', status: 'PAID', policyVersion: '' }
    client.setQueryData(['admin', 'finance', 'payments', filter, 0], { ...empty, totalElements: 1, totalPages: 1, items: [{
      id: '1', orderId, buyerId: '5', sellerId: '6', policyVersion: 'CONTRACT_V3', orderStatus: 'IN_PROGRESS', status: 'PAID',
      amount: 600001, refundedAmount: 0, paidAt: '2026-11-09T00:00:00Z', refundedAt: null }] })
    const html = render('/admin/finance?buyerId=5&status=PAID', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(html, /aria-current="page"[^>]*>결제·환불</)
    assert.match(html, /value="5"/)
    assert.match(html, /value="PAID" selected=""/)
    assert.match(html, new RegExp(`href="/admin/finance/orders/${orderId}"`))
    assert.match(html, /총 1건/)
    assert.match(html, /주문 진행 중/)
    assert.doesNotMatch(html, /주문 IN_PROGRESS/)
  } finally { client.clear() }
})

test('topup tab shows candidate decisions as information only', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'finance', 'topups', { memberId: '', status: 'UNKNOWN' }, 0], { ...empty, totalElements: 1, totalPages: 1, items: [{
      id: '9', memberId: '5', amount: 10000, status: 'UNKNOWN', lastErrorCode: 'RESULT_REQUIRES_REVIEW', recoveryAttemptCount: 2,
      createdAt: '2026-11-09T00:00:00Z', processingStartedAt: '2026-11-09T00:01:00Z', nextRecoveryAt: null, recoveryLeaseUntil: null, completedAt: null,
      automaticRecovery: { eligible: false, reason: 'RECOVERY_DISABLED' }, manualRecheck: { eligible: true, reason: 'NONE' } }] })
    const html = render('/admin/finance?tab=topups&status=UNKNOWN', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(html, /충전 #9/)
    assert.match(html, /복구 기능 꺼짐 \(RECOVERY_DISABLED\)/)
    assert.match(html, /재처리 후보 · 실행은 아직 지원하지 않아요/)
  } finally { client.clear() }
})
