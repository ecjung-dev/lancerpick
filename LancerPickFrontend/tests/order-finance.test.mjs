import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { getOrder, getOrders, payOrder, cancelOrder, confirmOrder, acceptOrder, rejectOrder, deliverOrder, requestOrderRevision } from '../src/features/orders/api.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'
import { createCancellationRequest, resolveCancellationRequest } from '../src/features/orders/api.ts'
import { describeHistory } from '../src/features/orders/history.ts'
import { orderDeadlineView } from '../src/features/orders/deadlines.ts'
import { koreanToday, orderListSearch, readOrderListFilters, recentMonths } from '../src/features/orders/listFilters.ts'
const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

test('order list sends selected conditions and omits empty ones', async () => {
  const urls = []
  globalThis.fetch = async (url) => { urls.push(url); return json({ items: [], totalPages: 0 }) }
  const none = { status: '', q: '', from: '', to: '', sort: 'latest' }
  await getOrders({ ...none, side: 'SELLER', status: 'AWAITING_PAYMENT', page: 2 })
  await getOrders({ ...none, side: 'BUYER', page: 0 })
  await getOrders({ side: 'BUYER', status: '', q: '로고 50%&x', from: '2026-10-01', to: '2026-10-31', sort: 'oldest', page: 1 })
  assert.deepEqual(urls, [
    '/api/orders?side=SELLER&status=AWAITING_PAYMENT&page=2&size=20',
    '/api/orders?side=BUYER&page=0&size=20',
    '/api/orders?side=BUYER&q=%EB%A1%9C%EA%B3%A0+50%25%26x&from=2026-10-01&to=2026-10-31&sort=oldest&page=1&size=20',
  ])
})

test('order list URL filters drop invalid values and recent months use Korean dates', () => {
  const read = (search) => readOrderListFilters(new URLSearchParams(search))
  assert.deepEqual(read('side=SELLER&q=%20%EB%A1%9C%EA%B3%A0%20&from=2026-10-01&to=2026-10-31&sort=oldest&page=3'),
    { side: 'SELLER', status: '', q: '로고', from: '2026-10-01', to: '2026-10-31', sort: 'oldest', page: 3 })
  assert.deepEqual(read(`q=${'😀'.repeat(100)}`).q, '😀'.repeat(100))
  assert.equal(read(`q=${'😀'.repeat(101)}`).q, '')
  assert.deepEqual([read('from=2026-02-29').from, read('from=2028-02-29').from, read('to=2026-13-01').to], ['', '2028-02-29', ''])
  assert.deepEqual([read('from=2026-11-02&to=2026-11-01').from, read('from=2026-11-02&to=2026-11-01').to], ['', ''])
  assert.equal(read('sort=LATEST').sort, 'latest')
  assert.deepEqual(orderListSearch({ side: 'BUYER', status: '', q: '', from: '', to: '', sort: 'latest', page: 0 }), { side: 'BUYER' })
  // 2026-10-31T15:30Z는 한국 날짜 11/1
  assert.equal(koreanToday(Date.parse('2026-10-31T15:30:00Z')), '2026-11-01')
  assert.deepEqual(recentMonths(1, Date.parse('2026-11-09T00:00:00Z')), { from: '2026-10-09', to: '2026-11-09' })
  assert.deepEqual(recentMonths(3, Date.parse('2026-05-31T00:00:00Z')), { from: '2026-02-28', to: '2026-05-31' })
  assert.deepEqual(recentMonths(1, Date.parse('2026-01-15T00:00:00Z')), { from: '2025-12-15', to: '2026-01-15' })
})

test('active overdue and delivered late use distinct due dates and strict boundary', () => {
  const due = '2026-11-03T00:00:00Z'
  const base = { status: 'IN_PROGRESS', currentDueAt: due, originalDueAt: due, revisionDueAt: '2026-11-05T00:00:00Z', deliveries: [
    { id: '1', sequenceNo: 1, submittedAt: due }, { id: '2', sequenceNo: 2, submittedAt: '2026-11-05T00:00:01Z' },
  ] }
  assert.equal(orderDeadlineView(base, Date.parse(due)).currentLate, false)
  assert.deepEqual(orderDeadlineView(base, Date.parse(due) + 1).deliveries.map(item => item.late), [false, true])
  assert.equal(orderDeadlineView({ ...base, status: 'AWAITING_CONFIRMATION' }, Date.parse(due) + 1).currentLate, false)
  assert.equal(orderDeadlineView({ ...base, status: 'COMPLETED' }, Date.parse(due) + 1).currentLate, false)
  assert.equal(orderDeadlineView({ ...base, status: 'REVISION_IN_PROGRESS', currentDueAt: base.revisionDueAt }, Date.parse(due) + 1).currentLate, false)
})

test('delivery lateness preserves microseconds and timezone offsets', () => {
  const due = '2026-11-03T00:00:00.123456Z'
  const base = { status: 'AWAITING_CONFIRMATION', currentDueAt: due, originalDueAt: due, revisionDueAt: null, deliveries: [
    { id: 'same', sequenceNo: 1, submittedAt: due },
    { id: 'late', sequenceNo: 1, submittedAt: '2026-11-03T09:00:00.123457+09:00' },
    { id: 'early', sequenceNo: 1, submittedAt: '2026-11-03T00:00:00.123455Z' },
  ] }
  assert.deepEqual(orderDeadlineView(base, Date.parse(due)).deliveries.map(item => item.late), [false, true, false])
  assert.equal(orderDeadlineView({ ...base, status: 'IN_PROGRESS' }, Date.parse(due)).currentLate, false)
  assert.equal(orderDeadlineView({ ...base, status: 'IN_PROGRESS' }, Date.parse(due) + 1).currentLate, true)
})

test('order history distinguishes state changes from events that leave state unchanged', () => {
  const order = { buyer: { id: '9007199254740993' }, seller: { id: '2' } }
  const event = { id: '1', actorId: '9007199254740993', action: 'CANCELLATION_REQUESTED', fromStatus: 'IN_PROGRESS', toStatus: 'IN_PROGRESS', reason: '취소 사유', createdAt: '2026-11-03T00:00:00Z' }
  assert.deepEqual(describeHistory(event, order), { action: '취소 요청', actor: '구매자', transition: '상태 유지: 진행 중', reason: '취소 사유' })
  assert.equal(describeHistory({ ...event, actorId: '2', action: 'CANCELLED', toStatus: 'CANCELLED' }, order).transition, '진행 중 → 취소됨')
  assert.equal(describeHistory({ ...event, action: 'CREATED', fromStatus: null, toStatus: 'REQUESTED', reason: null }, order).transition, '상태: 수락 대기')
  // 운영 문의를 검토한 관리자의 거절(K4): 당사자가 아닌 실행자는 관리자로 표시한다.
  assert.deepEqual(describeHistory({ ...event, actorId: '77', action: 'CANCELLATION_REJECTED', reason: '판매자 작업 진행 확인' }, order),
    { action: '취소 요청 거절', actor: '관리자', transition: '상태 유지: 진행 중', reason: '판매자 작업 진행 확인' })
  assert.equal(describeHistory({ ...event, actorId: null, action: 'COMPLETED', toStatus: 'COMPLETED' }, order).actor, '시스템')
})

test('cancellation commands preserve UUID and IDs and omit optional empty rejection body', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.method, 'POST')
    calls.push([url, options.body && JSON.parse(options.body)])
    return json({ cancellationRequest: { id: '9007199254740994' }, order: { id: '1' } })
  }
  const key = '1d15b8aa-4ad6-4e01-b13c-8598df53b42d'
  await createCancellationRequest('1', '사유', key)
  for (const action of ['approve', 'reject', 'withdraw']) await resolveCancellationRequest('1', '9007199254740994', action)
  await resolveCancellationRequest('1', '9007199254740994', 'reject', '거절 사유')
  assert.deepEqual(calls, [
    ['/api/orders/1/cancellation-requests', { reason: '사유', requestKey: key }],
    ...['approve', 'reject', 'withdraw'].map(action => [`/api/orders/1/cancellation-requests/9007199254740994/${action}`, undefined]),
    ['/api/orders/1/cancellation-requests/9007199254740994/reject', { responseReason: '거절 사유' }],
  ])
})

test('uncertain cancellation is not replayed automatically and explicit retry keeps supplied key', async () => {
  const bodies = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    bodies.push(options.body)
    throw new Error('connection lost')
  }
  const key = '1d15b8aa-4ad6-4e01-b13c-8598df53b42d'
  await assert.rejects(createCancellationRequest('1', '사유', key))
  assert.equal(bodies.length, 1)
  await assert.rejects(createCancellationRequest('1', '사유', key))
  assert.equal(bodies.length, 2)
  assert.equal(bodies[0], bodies[1])
  assert.throws(() => resolveCancellationRequest('1', '../me', 'approve'))
  assert.equal(bodies.length, 2)
})

test('delivery rounds and revision reference use the documented bodies with CSRF', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.method, 'POST')
    calls.push([url, JSON.parse(options.body)])
    return json({ id: '9007199254740993', status: 'AWAITING_CONFIRMATION' })
  }
  await deliverOrder('9007199254740993', '최초', 'https://example.com/1', 1)
  await requestOrderRevision('9007199254740993', '9007199254740994', '수정 사유')
  await deliverOrder('9007199254740993', '수정', 'https://example.com/2', 2)
  assert.deepEqual(calls, [
    ['/api/orders/9007199254740993/deliveries', { description: '최초', resultUrl: 'https://example.com/1', expectedSequenceNo: 1 }],
    ['/api/orders/9007199254740993/revision-request', { deliveryId: '9007199254740994', reason: '수정 사유' }],
    ['/api/orders/9007199254740993/deliveries', { description: '수정', resultUrl: 'https://example.com/2', expectedSequenceNo: 2 }],
  ])
})

test('delivery conflict does not advance round or retry and revision rejects invalid reference', async () => {
  const rounds = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    rounds.push(JSON.parse(options.body).expectedSequenceNo)
    return json({ code: 'DELIVERY_ALREADY_SUBMITTED', message: '이미 납품했어요.', fieldErrors: [], traceId: 'test' }, 409)
  }
  await assert.rejects(deliverOrder('1', '설명', 'https://example.com', 1), error => error.code === 'DELIVERY_ALREADY_SUBMITTED')
  assert.deepEqual(rounds, [1])
  for (const id of ['0', '../me', '9223372036854775808']) assert.throws(() => requestOrderRevision('1', id, '수정'))
  assert.deepEqual(rounds, [1])
})

test('seller decisions send CSRF and reason without client identity and preserve large IDs', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    calls.push([url, options.body])
    return json({ id: '9007199254740993', status: 'REJECTED' })
  }
  await acceptOrder('9007199254740993')
  await rejectOrder('9007199254740993', '일정상 어렵습니다.')
  assert.deepEqual(calls, [['/api/orders/9007199254740993/accept', undefined], ['/api/orders/9007199254740993/reject', JSON.stringify({ reason: '일정상 어렵습니다.' })]])
})

test('seller decisions propagate errors without replay and reject invalid IDs before network', async () => {
  for (const status of [400, 401, 403, 404, 409, 500]) {
    clearCsrfToken()
    let writes = 0
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
      writes++
      return json({ code: 'DECISION_FAILED', message: 'failed', fieldErrors: [] }, status)
    }
    await assert.rejects(acceptOrder('1'), error => error.status === status)
    await assert.rejects(rejectOrder('1', '사유'), error => error.status === status)
    assert.equal(writes, 2)
  }
  let calls = 0
  globalThis.fetch = async () => { calls++; throw new Error('network') }
  for (const id of ['0', '-1', '../me', '9223372036854775808']) {
    assert.throws(() => acceptOrder(id))
    assert.throws(() => rejectOrder(id, '사유'))
  }
  assert.equal(calls, 0)
})
test('order financial commands use CSRF and send no client amount or member id', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    calls.push([url, options.body])
    return json({ id: '9007199254740993', status: 'COMPLETED' })
  }
  await payOrder('9007199254740993'); await cancelOrder('9007199254740993', 'reason'); await confirmOrder('9007199254740993')
  assert.deepEqual(calls, [['/api/orders/9007199254740993/payment', '{"agreedToTerms":true}'], ['/api/orders/9007199254740993/cancel', '{"reason":"reason"}'], ['/api/orders/9007199254740993/confirm', undefined]])
})
test('invalid identifiers never reach network and failed payment is not replayed', async () => {
  let writes = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    writes++; return json({ code: 'INSUFFICIENT_BALANCE', message: 'insufficient', fieldErrors: [], traceId: 'test' }, 409)
  }
  for (const id of ['0', '-1', '../me', '9223372036854775808']) assert.throws(() => getOrder(id))
  assert.equal(writes, 0)
  await assert.rejects(payOrder('1'), error => error.code === 'INSUFFICIENT_BALANCE')
  assert.equal(writes, 1)
})


test('direct cancellation rejects invalid reasons before CSRF or mutation requests', () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({}) }
  for (const reason of ['', ' \r\n\u0085\u3000 ', '가'.repeat(1001), '😀'.repeat(1001)]) {
    assert.throws(() => cancelOrder('1', reason), /취소 사유/)
  }
  assert.equal(calls, 0)
})

test('direct cancellation normalizes reason and accepts 1000 Unicode code points', async () => {
  const bodies = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(url, '/api/orders/1/cancel')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    bodies.push(JSON.parse(options.body))
    return json({ id: '1', status: 'CANCELLED' })
  }
  await cancelOrder('1', '\u0085😀\r\n취소\u3000')
  await cancelOrder('1', '😀'.repeat(1000))
  assert.deepEqual(bodies, [{ reason: '😀\n취소' }, { reason: '😀'.repeat(1000) }])
})

test('order command failures treat network loss and 5xx as unclear instead of asking to retry', async () => {
  const { ApiError } = await import('../src/shared/api/client.ts')
  const { orderCommandFailureMessage, UNCLEAR_ORDER_RESULT } = await import('../src/features/orders/failure.ts')
  const api = (status, code, message = '요청을 처리하지 못했어요. 다시 시도해 주세요.') => new ApiError(status, { code, message, fieldErrors: [] })
  // 저장 후 응답만 유실된 경우(게이트웨이 502·통신 단절)는 실패로 단정하지 않는다.
  assert.equal(orderCommandFailureMessage(api(502, 'HTTP_ERROR')), UNCLEAR_ORDER_RESULT)
  assert.equal(orderCommandFailureMessage(api(500, 'INTERNAL_ERROR', '서버 오류')), UNCLEAR_ORDER_RESULT)
  assert.equal(orderCommandFailureMessage(api(0, 'NETWORK_ERROR')), UNCLEAR_ORDER_RESULT)
  assert.equal(orderCommandFailureMessage(new TypeError('boom')), UNCLEAR_ORDER_RESULT)
  assert.doesNotMatch(UNCLEAR_ORDER_RESULT, /다시 시도해 주세요/)
  // 서버가 처리하지 않았다고 답한 4xx는 서버 메시지와 코드를 그대로 보여준다.
  assert.equal(orderCommandFailureMessage(api(409, 'ORDER_STATE_CONFLICT', '수락·거절 가능한 주문 상태가 아니에요.')), '수락·거절 가능한 주문 상태가 아니에요. (ORDER_STATE_CONFLICT)')
  assert.equal(orderCommandFailureMessage(api(403, 'CSRF_INVALID', '요청 인증 정보를 갱신해 주세요.')), '요청 인증 정보를 갱신해 주세요. (CSRF_INVALID)')
})
