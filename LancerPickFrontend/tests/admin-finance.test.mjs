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
  assert.match(decisionLabel(true, 'NONE'), /재처리 후보 · 실행 직전에 서버가 다시 확인해요/)
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
    assert.match(html, /전액 보관 계약 \(CONTRACT_V3\) · 주문 진행 중/, '정책·주문 상태는 한글 라벨, 정책 코드는 괄호로 남긴다')
    assert.match(html, /600,001원/)
    assert.equal(html.match(/기록 없음/g)?.length, 2, '정산·종료 분배가 없으면 0원 대신 기록 없음')
    assert.match(html, /주문 대금 보관 중 \(HELD\)/)
    assert.match(html, /실제 파일 확인은 아님/)
    assert.match(html, /후보 아님: 지금 주문 상태에서는 생성 불가 \(ORDER_NOT_WRITABLE\)/)
    assert.doesNotMatch(html, /<button[^>]*>(재시도|재생성|재확인|재시작)/, '후보가 아니면 실행 버튼이 없다')
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
    // 열이 바뀌어도 통과하지 않도록 역할 칸(data-label) 안의 링크 목적지를 확인한다.
    // 보조기술에는 "구매자 #5"처럼 역할까지 링크 이름으로 읽히고, 화면에는 열 머리글이 역할을 대신한다.
    assert.match(html, /data-label="구매자"><a [^>]*href="\/admin\/members\/5"[^>]*><span class="[^"]*">구매자 <\/span>#5<\/a>/)
    assert.match(html, /data-label="판매자"><a [^>]*href="\/admin\/members\/6"[^>]*><span class="[^"]*">판매자 <\/span>#6<\/a>/)
    assert.match(html, /총 1건/)
    assert.match(html, /data-label="주문 상태">진행 중</)
    assert.doesNotMatch(html, /IN_PROGRESS/)
  } finally { client.clear() }
})

test('topup tab shows the decision and offers a recheck only for candidates', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'finance', 'topups', { memberId: '', status: 'UNKNOWN' }, 0], { ...empty, totalElements: 1, totalPages: 1, items: [{
      id: '9', memberId: '5', amount: 10000, status: 'UNKNOWN', lastErrorCode: 'RESULT_REQUIRES_REVIEW', recoveryAttemptCount: 2,
      createdAt: '2026-11-09T00:00:00Z', processingStartedAt: '2026-11-09T00:01:00Z', nextRecoveryAt: null, recoveryLeaseUntil: null, completedAt: null,
      automaticRecovery: { eligible: false, reason: 'RECOVERY_DISABLED' }, manualRecheck: { eligible: true, reason: 'NONE' } }] })
    const html = render('/admin/finance?tab=topups&status=UNKNOWN', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(html, /data-label="충전"><strong>#9<\/strong>/)
    assert.match(html, /복구 기능 꺼짐 \(RECOVERY_DISABLED\)/)
    assert.match(html, /재처리 후보 · 실행 직전에 서버가 다시 확인해요/)
    assert.match(html, /<summary>충전 재확인<\/summary>/)
    assert.match(html, /<button type="submit">재확인 요청<\/button>/)
  } finally { client.clear() }
})

// ---------- 필터 초기화 ----------

test('canResetFilter는 주소의 필터 키·page(잘못된 값 포함)·입력 초안을 보고, 지정하지 않은 tab은 무시한다', async () => {
  const { canResetFilter } = await import('../src/features/admin/filterReset.ts')
  const keys = ['orderId', 'status']
  assert.equal(canResetFilter(new URLSearchParams('tab=payments'), keys), false, 'tab만 있으면 지울 것이 없다')
  assert.equal(canResetFilter(new URLSearchParams('tab=payments&page=2'), keys), true, 'page만 있어도 초기화 가능')
  assert.equal(canResetFilter(new URLSearchParams('status=garbage'), keys), true, '읽기에서 버려진 잘못된 값도 주소에 남았으면 지운다')
  assert.equal(canResetFilter(new URLSearchParams(''), keys, ['', '  ']), false)
  assert.equal(canResetFilter(new URLSearchParams(''), keys, ['12']), true, '주소가 비어도 적용 전 입력이 있으면 지운다')
})

test('결제 필터 초기화 버튼은 조건이 없으면 막히고, 조건이 있으면 열린다(type=button, 탭 유지 대상 아님)', () => {
  const client = newClient()
  try {
    const none = { orderId: '', buyerId: '', sellerId: '', status: '', policyVersion: '' }
    client.setQueryData(['admin', 'finance', 'payments', none, 0], empty)
    const idle = render('/admin/finance?tab=payments', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(idle, /<button type="button"[^>]*disabled=""[^>]*>초기화<\/button>/)
    const filtered = { ...none, status: 'PAID' }
    client.setQueryData(['admin', 'finance', 'payments', filtered, 0], empty)
    const active = render('/admin/finance?tab=payments&status=PAID', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(active, /<button type="button"(?![^>]*disabled)[^>]*>초기화<\/button>/)
  } finally { client.clear() }
})

test('상태 코드 라벨은 문맥별 표를 쓰고 코드를 괄호로 남기며, 모르는 값은 원문 그대로 보여 준다', async () => {
  const labels = await server.ssrLoadModule('/src/features/admin/statusLabels.ts')
  assert.equal(labels.codeLabel(labels.topupStatusLabels, 'COMPLETED'), '충전 완료 (COMPLETED)')
  assert.equal(labels.codeLabel(labels.jobStatusLabels, 'COMPLETED'), '완료 (COMPLETED)')
  assert.equal(labels.codeLabel(labels.closureReasonLabels, 'COMPLETED'), '구매 확정 (COMPLETED)')
  assert.equal(labels.codeLabel(labels.jobStatusLabels, 'NO_EXECUTION'), '실행 기록 없음 (NO_EXECUTION)')
  assert.equal(labels.codeLabel(labels.settlementTypeLabels, 'NEW_TYPE'), 'NEW_TYPE')
  assert.equal(labels.plainLabel(labels.topupStatusLabels, 'UNKNOWN'), '결과 확인 필요')
})

test('작업 이력 초기화는 읽기에서 버려진 잘못된 action도 주소에 있으면 열린다', () => {
  const client = newClient()
  try {
    const html = render('/admin/finance?tab=logs&action=garbage', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(html, /<button type="button"(?![^>]*disabled)[^>]*>초기화<\/button>/)
  } finally { client.clear() }
})

test('복구 결과 키는 충전 건별 결과 라벨과 키를 함께 보여 준다', async () => {
  const labels = await server.ssrLoadModule('/src/features/admin/statusLabels.ts')
  assert.equal(labels.codeLabel(labels.jobOutcomeLabels, 'completed'), '충전 완료 (completed)')
  assert.equal(labels.codeLabel(labels.jobOutcomeLabels, 'stale'), '처리 조건 변경·처리권 없음 (stale)')
})
