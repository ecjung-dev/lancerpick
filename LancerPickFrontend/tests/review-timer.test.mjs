import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getReviewTimer, startReviewTimer } from '../src/features/orders/api.ts'
import {
  reviewTimerView,
  reviewTimerRefetchInterval,
  REVIEW_TIMER_POLL_MS,
} from '../src/features/orders/reviewTimer.ts'
import { describeHistory } from '../src/features/orders/history.ts'
import { notificationMessage } from '../src/features/notifications/messages.ts'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})
const server = await createServer({
  configFile: false,
  cacheDir: 'node_modules/.vite-test-review-timer',
  server: { middlewareMode: true, ws: false, watch: null },
  appType: 'custom',
})
const { ReviewTimerPanel } = await server.ssrLoadModule(
  '/src/features/orders/components/ReviewTimerPanel.tsx',
)
after(async () => {
  await server.close()
})

const timer = (status, extra = {}) => ({
  status,
  basisAt: '2026-11-01T00:00:00Z',
  eligibleAt: '2026-11-03T00:00:00Z',
  startedAt: null,
  expiresAt: null,
  canStart: false,
  ...extra,
})

test('timer lookup keeps large IDs, bypasses cache; start sends POST without body', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/orders/9007199254740993/review-timer')
    assert.equal(init.cache, 'no-store')
    assert.equal(init.signal, controller.signal)
    return Response.json(timer('WAITING'))
  }
  assert.equal((await getReviewTimer('9007199254740993', controller.signal)).status, 'WAITING')
  globalThis.fetch = async (url, init) => {
    if (url === '/api/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 't' })
    assert.equal(url, '/api/orders/11/review-timer')
    assert.equal(init.method, 'POST')
    assert.equal(init.body, undefined)
    return Response.json(timer('ACTIVE'))
  }
  assert.equal((await startReviewTimer('11')).status, 'ACTIVE')
  assert.throws(() => getReviewTimer('../1'))
})

test('timer text hides outside review and explains each state to the right party', () => {
  assert.equal(reviewTimerView(timer('NOT_IN_REVIEW'), true), null)
  assert.match(reviewTimerView(timer('WAITING'), false).detail, /검수 결과/)
  assert.match(reviewTimerView(timer('WAITING'), true).detail, /시작할 수 있습니다/)
  assert.match(reviewTimerView(timer('CANCELLATION_PENDING'), true).title, /취소 요청/)
  assert.match(reviewTimerView(timer('STARTABLE', { canStart: true }), true).title, /시작 가능/)
  // 서버가 시작을 막은 판매자(구매자 보안 잠금)는 시작 가능으로 안내하지 않는다.
  assert.match(reviewTimerView(timer('STARTABLE'), true).title, /시작 보류/)
  assert.match(reviewTimerView(timer('STARTABLE'), false).title, /시작 가능/)
  const active = reviewTimerView(
    timer('ACTIVE', { startedAt: '2026-11-03T01:00:00Z', expiresAt: '2026-11-10T01:00:00Z' }),
    false,
  )
  assert.match(active.title, /진행 중/)
  assert.match(active.detail, /자동으로 구매가 확정/)
  assert.match(reviewTimerView(timer('AUTO_CONFIRMED', { expiresAt: '2026-11-10T01:00:00Z' }), false).title, /자동 구매 확정 완료/)
})

function render(data) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['wallet', 'order-review-timer', '11'], data)
  try {
    return renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(ReviewTimerPanel, { orderId: '11', sellerId: '7' }),
      ),
    )
  } finally {
    client.clear()
  }
}

test('start button follows only the server canStart flag', () => {
  assert.match(render(timer('STARTABLE', { canStart: true })), /타이머 시작<\/button>/)
  assert.doesNotMatch(render(timer('STARTABLE', { canStart: false })), /<button/)
  assert.equal(render(timer('NOT_IN_REVIEW')), '')
})

test('system completion is shown as automatic confirmation, not as a member action', () => {
  const order = { buyer: { id: '1' }, seller: { id: '2' } }
  const item = describeHistory(
    {
      id: '9', actorId: null, action: 'COMPLETED', fromStatus: 'AWAITING_CONFIRMATION',
      toStatus: 'COMPLETED', reason: null, createdAt: '2026-11-10T01:00:00Z',
    },
    order,
  )
  assert.equal(item.actor, '시스템')
  assert.equal(item.action, '자동 구매 확정')
  assert.equal(
    describeHistory({ id: '8', actorId: '2', action: 'TIMER_STARTED', fromStatus: 'AWAITING_CONFIRMATION',
      toStatus: 'AWAITING_CONFIRMATION', reason: null, createdAt: '2026-11-03T01:00:00Z' }, order).action,
    '자동 구매 확정 타이머 시작',
  )
  assert.match(notificationMessage('ORDER_AUTO_CONFIRMED'), /자동으로 확정/)
  assert.match(notificationMessage('ORDER_TIMER_STARTED'), /타이머를 시작/)
})

test('every in-review state is polled (time or the other party can change it); finished states and failures stop', () => {
  for (const status of ['WAITING', 'STARTABLE', 'ACTIVE', 'CANCELLATION_PENDING', 'ADMIN_STOPPED'])
    assert.equal(reviewTimerRefetchInterval(timer(status), false), REVIEW_TIMER_POLL_MS)
  for (const status of ['NOT_IN_REVIEW', 'AUTO_CONFIRMED'])
    assert.equal(reviewTimerRefetchInterval(timer(status), false), false)
  assert.equal(reviewTimerRefetchInterval(timer('ACTIVE'), true), false)
  assert.equal(reviewTimerRefetchInterval(undefined, false), false)
})

test('V3 funds, deadline and pre-notice notifications have their own wording', () => {
  const fallback = notificationMessage('ORDER_SOMETHING_NEW')
  for (const type of [
    'ORDER_FUNDS_HELD', 'ORDER_FUNDS_RELEASED', 'ORDER_FUNDS_REFUNDED',
    'ORDER_DELIVERY_DUE_NOTIFIED', 'ORDER_REDELIVERY_DUE_NOTIFIED', 'ORDER_AUTO_CONFIRM_SOON',
  ]) assert.notEqual(notificationMessage(type), fallback, type)
  assert.match(notificationMessage('ORDER_FUNDS_HELD'), /보관/)
  assert.match(notificationMessage('ORDER_FUNDS_RELEASED'), /적립금으로 지급/)
  assert.match(notificationMessage('ORDER_FUNDS_REFUNDED'), /전액 반환/)
  assert.match(notificationMessage('ORDER_AUTO_CONFIRM_SOON'), /24시간 안에/)
  assert.match(notificationMessage('ORDER_AUTO_CONFIRMED'), /판매자 적립금으로 지급/)
  // 잔금 미지급 종료 알림은 V3 범위가 아니다.
  assert.equal(notificationMessage('ORDER_TERMINATED'), fallback)
})

test('deadline notices are shown as system records by kind', () => {
  const order = { buyer: { id: '1' }, seller: { id: '2' } }
  const notice = (toStatus) => describeHistory({
    id: '7', actorId: null, action: 'DEADLINE_NOTIFIED', fromStatus: toStatus,
    toStatus, reason: null, createdAt: '2026-11-03T01:00:00Z',
  }, order)
  assert.deepEqual(notice('IN_PROGRESS'), {
    action: '납기 통지', actor: '시스템', transition: '상태 유지: 진행 중', reason: null,
  })
  assert.equal(notice('REVISION_IN_PROGRESS').action, '재납품 기한 통지')
})
