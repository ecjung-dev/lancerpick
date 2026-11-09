import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getMyInquiries, requestCancellationReview } from '../src/features/inquiries/api.ts'
import { canAskForReview, validateSubject } from '../src/features/inquiries/validation.ts'
import { closeInquiry, decideInquiry, getAdminInquiries, getAdminInquiry } from '../src/features/admin/inquiryApi.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'
import { getActiveReviewTimers, stopReviewTimer } from '../src/features/admin/timerApi.ts'
import { changeVisibility } from '../src/features/admin/api.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-inquiry',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminInquiryListPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminInquiryListPage.tsx')
const { AdminInquiryDetailPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminInquiryDetailPage.tsx')
const { CancellationReviewRequest } = await server.ssrLoadModule('/src/features/inquiries/components/CancellationReviewRequest.tsx')
const { readInquiryFilter } = await server.ssrLoadModule('/src/features/admin/inquiryQueries.ts')
const { AdminReviewTimerPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminReviewTimerPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const csrf = () => json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })

const orderId = '9007199254740993'
const requestId = '41'
const inquiryId = '7'

// ---------- API ----------

test('buyer review request posts only the subject with CSRF and is never retried', async () => {
  let writes = 0
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return csrf()
    writes++
    assert.equal(url, `/api/orders/${orderId}/cancellation-requests/${requestId}/review-inquiries`)
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf')
    assert.deepEqual(JSON.parse(options.body), { subject: '판매자 무응답' })
    throw new TypeError('network failed')
  }
  await assert.rejects(requestCancellationReview(orderId, requestId, '판매자 무응답'), (e) => e.code === 'NETWORK_ERROR')
  assert.equal(writes, 1, '통신 오류가 나도 자동으로 다시 보내지 않는다')
})

test('admin and review commands are not sent when the account changed after the click', async () => {
  const commands = [
    (scope) => requestCancellationReview(orderId, requestId, '판매자 무응답', scope),
    (scope) => decideInquiry(inquiryId, 'approve', '사유', scope),
    (scope) => decideInquiry(inquiryId, 'reject', '사유', scope),
    (scope) => closeInquiry(inquiryId, scope),
    (scope) => stopReviewTimer(orderId, '사유', scope),
    (scope) => changeVisibility('5', { hidden: true, reason: '사유', expectedVersion: 0 }, scope),
  ]
  for (const command of commands) {
    // 1) 클릭 직후 이미 계정이 바뀐 경우: CSRF도 요청하지 않는다.
    let calls = 0
    globalThis.fetch = async () => { calls++; return csrf() }
    await assert.rejects(command(() => false), (e) => e.name === 'AbortError')
    assert.equal(calls, 0)

    // 2) CSRF 토큰을 받는 사이에 로그아웃·다른 계정 로그인이 끝난 경우: 명령은 보내지 않는다.
    clearCsrfToken()
    let current = true
    const sent = []
    globalThis.fetch = async (url) => {
      sent.push(url)
      if (url === '/api/auth/csrf') { current = false; return csrf() }
      return json({})
    }
    await assert.rejects(command(() => current), (e) => e.name === 'AbortError')
    assert.deepEqual(sent, ['/api/auth/csrf'])
    clearCsrfToken()
  }
})

test('invalid IDs never reach the network', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({}) }
  for (const bad of ['', '0', '-1', 'abc', '1.5', '01']) {
    await assert.rejects(requestCancellationReview(bad, requestId, '제목'))
    await assert.rejects(requestCancellationReview(orderId, bad, '제목'))
    await assert.rejects(getMyInquiries(bad))
  }
  for (const bad of ['', '0', 'abc', '9223372036854775808']) {
    await assert.rejects(getAdminInquiry(bad))
    await assert.rejects(decideInquiry(bad, 'approve', '사유'))
    await assert.rejects(closeInquiry(bad))
  }
  assert.equal(calls, 0)
})

test('read requests send only documented query parameters', async () => {
  const urls = []
  globalThis.fetch = async (url, options) => {
    urls.push(url)
    assert.equal(options.body, undefined)
    return json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  }
  await getMyInquiries(orderId)
  await getAdminInquiries({ status: 'OPEN', type: 'CANCELLATION_REVIEW' }, 2)
  await getAdminInquiries({ status: '', type: '' }, 0)
  assert.deepEqual(urls, [
    `/api/me/inquiries?orderId=${orderId}&page=0&size=20`,
    '/api/admin/inquiries?page=2&size=20&status=OPEN&type=CANCELLATION_REVIEW',
    '/api/admin/inquiries?page=0&size=20',
  ])
})

test('admin decisions and close are single POSTs with CSRF', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return csrf()
    sent.push({ url, method: options.method, body: options.body, token: options.headers.get('X-CSRF-TOKEN') })
    return json({ code: 'REQUEST_ALREADY_RESOLVED', message: '이미 처리', fieldErrors: [], traceId: 't' }, 409)
  }
  await assert.rejects(decideInquiry(inquiryId, 'approve', '무응답 확인'), (e) => e.code === 'REQUEST_ALREADY_RESOLVED')
  await assert.rejects(decideInquiry(inquiryId, 'reject', '작업 확인'), (e) => e.status === 409)
  await assert.rejects(closeInquiry(inquiryId), (e) => e.status === 409)
  assert.deepEqual(sent, [
    { url: `/api/admin/inquiries/${inquiryId}/approve`, method: 'POST', body: JSON.stringify({ reason: '무응답 확인' }), token: 'csrf' },
    { url: `/api/admin/inquiries/${inquiryId}/reject`, method: 'POST', body: JSON.stringify({ reason: '작업 확인' }), token: 'csrf' },
    { url: `/api/admin/inquiries/${inquiryId}/close`, method: 'POST', body: undefined, token: 'csrf' },
  ])
})

// ---------- 입력 규칙 ----------

test('subject follows the server rule: trimmed, 1 to 150 code points', () => {
  assert.equal(validateSubject('   ').error !== undefined, true)
  // 서버 TextNormalizer와 같이 앞뒤 공백만 지우고 가운데 공백은 그대로 둔다.
  assert.deepEqual(validateSubject('  판매자가  응답이 없어요\n'), { subject: '판매자가  응답이 없어요', error: undefined })
  assert.equal(validateSubject('가'.repeat(150)).error, undefined)
  assert.notEqual(validateSubject('가'.repeat(151)).error, undefined)
  assert.equal(validateSubject('😀'.repeat(150)).error, undefined, '이모지는 한 글자로 센다')
})

test('review request is offered only to the buyer of a paid, unfinished V3 order with a pending request', () => {
  const base = { policyVersion: 'CONTRACT_V3', status: 'AWAITING_CONFIRMATION', allowedActions: ['WITHDRAW_CANCELLATION'] }
  assert.equal(canAskForReview(base), true)
  assert.equal(canAskForReview({ ...base, status: 'IN_PROGRESS' }), true)
  assert.equal(canAskForReview({ ...base, policyVersion: 'LEGACY_V1' }), false)
  assert.equal(canAskForReview({ ...base, status: 'CANCELLED' }), false)
  assert.equal(canAskForReview({ ...base, allowedActions: ['APPROVE_CANCELLATION', 'REJECT_CANCELLATION'] }), false, '판매자 화면')
})

test('admin list filters from the URL accept only documented values', () => {
  assert.deepEqual(readInquiryFilter(new URLSearchParams('status=OPEN&type=GENERAL')), { status: 'OPEN', type: 'GENERAL' })
  assert.deepEqual(readInquiryFilter(new URLSearchParams('status=open&type=x')), { status: '', type: '' })
})

// ---------- 화면 ----------

function render(url, path, element, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element })))))
}
const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })

const order = {
  id: orderId, policyVersion: 'CONTRACT_V3', status: 'AWAITING_CONFIRMATION', allowedActions: ['WITHDRAW_CANCELLATION'],
  cancellationRequests: [{ id: requestId, status: 'PENDING', reason: '응답 없음', requestedAt: '2026-11-08T00:00:00Z', resolvedAt: null }],
}
const inquiry = {
  id: inquiryId, type: 'CANCELLATION_REVIEW', status: 'OPEN', subject: '<b>판매자</b> 무응답', orderId,
  cancellationRequestId: requestId, createdAt: '2026-11-09T00:00:00Z', resolvedAt: null,
}
const page = (items) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 })

test('buyer sees the review form only while no inquiry is open for the pending request', () => {
  const client = newClient()
  try {
    client.setQueryData(['wallet', 'order-inquiries', orderId], page([]))
    let html = render('/', '/', createElement(CancellationReviewRequest, { order, disabled: false }), client)
    assert.match(html, /관리자 검토 요청/)
    assert.match(html, /<input/)

    client.setQueryData(['wallet', 'order-inquiries', orderId], page([inquiry]))
    html = render('/', '/', createElement(CancellationReviewRequest, { order, disabled: false }), client)
    assert.match(html, /검토 대기/)
    assert.match(html, /&lt;b&gt;판매자&lt;\/b&gt; 무응답/, '제목은 HTML로 해석하지 않는다')
    assert.doesNotMatch(html, /<input/, '열린 문의가 있으면 다시 요청하는 칸을 숨긴다')

    html = render('/', '/', createElement(CancellationReviewRequest, { order: { ...order, policyVersion: 'LEGACY_V1' }, disabled: false }), client)
    assert.equal(html, '', 'V3가 아닌 주문에는 표시하지 않는다')
  } finally { client.clear() }
})

const detail = {
  id: inquiryId, type: 'CANCELLATION_REVIEW', status: 'OPEN', subject: '판매자 무응답',
  requester: { id: '5', displayName: '구매자' }, assignedAdminId: null,
  createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z', resolvedAt: null,
  order: { id: orderId, status: 'AWAITING_CONFIRMATION', policyVersion: 'CONTRACT_V3', priceAmount: 600001, paidAt: '2026-11-08T00:00:00Z',
    buyer: { id: '5', displayName: '구매자' }, seller: { id: '6', displayName: '판매자' } },
  cancellationRequest: { id: requestId, status: 'PENDING', reason: '<script>x</script>', requestedAt: '2026-11-08T01:00:00Z',
    resolvedAt: null, resolutionSource: null, responseReason: null },
  allowedActions: ['APPROVE', 'REJECT'],
}

test('admin detail shows the cancellation context and keeps approval behind a confirmation', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'inquiry', inquiryId], detail)
    const html = render(`/admin/inquiries/${inquiryId}`, '/admin/inquiries/:id', createElement(AdminInquiryDetailPage), client)
    assert.match(html, /600,001원/)
    assert.match(html, /&lt;script&gt;x&lt;\/script&gt;/)
    assert.match(html, /<button type="button" disabled="">승인 및 전액 반환<\/button>/, '확인 전에는 승인 버튼을 막는다')
    assert.match(html, />거절</)
    assert.doesNotMatch(html, /문의 닫기/)
  } finally { client.clear() }
})

test('admin detail offers only closing when the request was already resolved', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'inquiry', inquiryId], {
      ...detail, allowedActions: ['CLOSE'],
      cancellationRequest: { ...detail.cancellationRequest, status: 'REJECTED', resolvedAt: '2026-11-09T01:00:00Z', responseReason: '작업 중' },
    })
    const html = render(`/admin/inquiries/${inquiryId}`, '/admin/inquiries/:id', createElement(AdminInquiryDetailPage), client)
    assert.match(html, /거절됨/)
    assert.match(html, /문의 닫기/)
    assert.doesNotMatch(html, /승인 및 전액 반환/)
  } finally { client.clear() }
})

test('admin list restores filters from the URL and links to the detail', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'inquiries', { status: 'OPEN', type: '' }, 0], page([{
      id: inquiryId, type: 'CANCELLATION_REVIEW', status: 'OPEN', subject: '판매자 무응답', orderId, cancellationRequestId: requestId,
      requester: { id: '5', displayName: '구매자' }, createdAt: '2026-11-09T00:00:00Z', resolvedAt: null,
    }]))
    const html = render('/admin/inquiries?status=OPEN', '/admin/inquiries', createElement(AdminInquiryListPage), client)
    assert.match(html, /value="OPEN" selected=""/)
    assert.match(html, new RegExp(`href="/admin/inquiries/${inquiryId}"`))
    assert.match(html, /주문 #9007199254740993/)
    assert.match(html, /총 1건/)
  } finally { client.clear() }
})

// ---------- K5 타이머 해제 ----------

test('timer stop is a single POST with CSRF and the reason only; invalid order IDs never reach the network', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return csrf()
    sent.push({ url, method: options?.method, body: options?.body, token: options?.headers?.get?.('X-CSRF-TOKEN') })
    if (url.startsWith('/api/admin/review-timers')) return json({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 })
    return json({ code: 'AUTO_CONFIRM_DUE', message: '만료 우선', fieldErrors: [], traceId: 't' }, 409)
  }
  await getActiveReviewTimers(1)
  await assert.rejects(stopReviewTimer(orderId, '운영 확인'), (e) => e.code === 'AUTO_CONFIRM_DUE')
  for (const bad of ['', '0', 'abc', '9223372036854775808']) await assert.rejects(stopReviewTimer(bad, '사유'))
  assert.deepEqual(sent, [
    { url: '/api/admin/review-timers?page=1&size=20', method: 'GET', body: undefined, token: null },
    { url: `/api/admin/orders/${orderId}/review-timer/stop`, method: 'POST', body: JSON.stringify({ reason: '운영 확인' }), token: 'csrf' },
  ])
})

test('timer page lists active timers with parties and expiry, and opens no stop form until chosen', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'review-timers', 0], page([{
      timerId: '3', orderId, cycleNo: 2, startedAt: '2026-11-08T00:00:00Z', expiresAt: '2026-11-15T00:00:00Z',
      buyer: { id: '5', displayName: '<i>구매자</i>' }, seller: { id: '6', displayName: null },
    }]))
    const html = render('/admin/review-timers', '/admin/review-timers', createElement(AdminReviewTimerPage), client)
    assert.match(html, /진행 중 1건/)
    assert.match(html, /주문 #9007199254740993/)
    assert.match(html, /&lt;i&gt;구매자&lt;\/i&gt;/)
    assert.match(html, /판매자 회원 #6/)
    assert.match(html, /2회차/)
    assert.match(html, />해제하기</)
    assert.doesNotMatch(html, /<textarea/, '해제할 주문을 고르기 전에는 사유 입력 칸을 보이지 않는다')
  } finally { client.clear() }
})
