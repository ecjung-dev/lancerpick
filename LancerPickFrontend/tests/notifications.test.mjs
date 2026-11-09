import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { MutationObserver, QueryClient, QueryClientProvider, QueryObserver } from '@tanstack/react-query'
import { createServer } from 'vite'
import { notificationMessage, notificationTarget, unreadBadge, waitsForReadBeforeNavigation } from '../src/features/notifications/messages.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-notifications',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { NotificationsPage, NotificationReadError } = await server.ssrLoadModule('/src/features/notifications/pages/NotificationsPage.tsx')
const { NotificationNavLink } = await server.ssrLoadModule('/src/features/notifications/components/NotificationNavLink.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
const { getNotifications } = await server.ssrLoadModule('/src/features/notifications/api.ts')
const { notificationReadOptions } = await server.ssrLoadModule('/src/features/notifications/queries.ts')
after(async () => { await server.close() })

test('every order event type has its own Korean message and unknown types fall back', () => {
  const types = ['CREATED', 'ACCEPTED', 'REJECTED', 'PAID', 'DELIVERED', 'REVISION_REQUESTED',
    'CANCELLATION_REQUESTED', 'CANCELLATION_REJECTED', 'CANCELLATION_WITHDRAWN', 'CANCELLED', 'COMPLETED']
  const fallback = notificationMessage('ORDER_SOMETHING_NEW')
  const messages = new Set(types.map((type) => notificationMessage(`ORDER_${type}`)))
  assert.equal(messages.size, types.length)
  assert.ok(!messages.has(fallback))
  assert.equal(fallback, '거래 상태가 변경되었습니다.')
  // 관리자 거절(K4)은 판매자 거절과 다른 문구다.
  const adminRejected = notificationMessage('ORDER_CANCELLATION_ADMIN_REJECTED')
  assert.notEqual(adminRejected, fallback)
  assert.notEqual(adminRejected, notificationMessage('ORDER_CANCELLATION_REJECTED'))
  assert.match(adminRejected, /관리자/)
})

test('first signature asks the counterpart to check signing while final signature announces formation', () => {
  assert.match(notificationMessage('ORDER_CONTRACT_SIGNATURE_REQUESTED'), /먼저 서명/)
  assert.match(notificationMessage('ORDER_CONTRACT_SIGNATURE_REQUESTED'), /서명 상태를 확인/)
  assert.doesNotMatch(notificationMessage('ORDER_CONTRACT_SIGNATURE_REQUESTED'), /계약이 성립/)
  assert.match(notificationMessage('ORDER_CONTRACT_SIGNED'), /양측 서명이 완료되어 계약이 성립/)
})

test('only valid order IDs become links, keeping large IDs as strings', () => {
  assert.equal(notificationTarget({ orderId: '9007199254740993' }), '/orders/9007199254740993')
  for (const orderId of [null, '', '0', '-1', '1.5', 'abc', '01', '12345678901234567890'])
    assert.equal(notificationTarget({ orderId }), null)
})

test('unread badge hides zero or invalid counts and caps at 99+', () => {
  assert.equal(unreadBadge(undefined), null)
  assert.equal(unreadBadge(0), null)
  assert.equal(unreadBadge(-1), null)
  assert.equal(unreadBadge(1.5), null)
  assert.equal(unreadBadge(7), '7')
  assert.equal(unreadBadge(99), '99')
  assert.equal(unreadBadge(100), '99+')
})

function render(element, setup, url = '/notifications') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  setup(client)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
      createElement(QueryClientProvider, { client }, element)))
  } finally { client.clear() }
}

test('notification list distinguishes unread items and links to the order detail', () => {
  const html = render(createElement(NotificationsPage), (client) => client.setQueryData(['notifications', 'list', 'all', 1], {
    items: [
      { id: '2', type: 'ORDER_DELIVERED', orderId: '9007199254740993', productTitle: '로고 디자인', read: false, createdAt: '2026-11-05T01:00:00Z' },
      { id: '1', type: 'ORDER_ACCEPTED', orderId: '7', productTitle: null, read: true, createdAt: '2026-11-04T01:00:00Z' },
    ],
    page: 1, size: 20, totalElements: 22, totalPages: 2,
  }), '/notifications?page=1')
  assert.match(html, /판매자가 결과물을 납품했습니다/)
  assert.match(html, /로고 디자인/)
  assert.match(html, /href="\/orders\/9007199254740993"/)
  assert.match(html, /href="\/orders\/7"/)
  assert.equal(html.match(/<span[^>]*>읽지 않음<\/span>/g)?.length, 1)
  assert.match(html, /2페이지/)
  assert.match(html, /disabled="">다음/)
})

test('empty list, login requirement and server errors are shown differently', () => {
  const empty = render(createElement(NotificationsPage), (client) => client.setQueryData(['notifications', 'list', 'all', 0],
    { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
  assert.match(empty, /받은 알림이 없습니다/)

  const unauthorized = render(createElement(NotificationsPage), (client) => {
    client.getQueryCache().build(client, { queryKey: ['notifications', 'list', 'all', 0], queryFn: async () => undefined }).setState({
      status: 'error', fetchStatus: 'idle', error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] }) })
  })
  assert.match(unauthorized, /로그인 후 알림을 확인할 수 있습니다/)
  assert.match(unauthorized, /href="\/login\?next=%2Fnotifications"/)

  const failed = render(createElement(NotificationsPage), (client) => {
    client.getQueryCache().build(client, { queryKey: ['notifications', 'list', 'all', 0], queryFn: async () => undefined }).setState({
      status: 'error', fetchStatus: 'idle', error: new ApiError(500, { code: 'INTERNAL_ERROR', message: '잠시 후 다시 시도해 주세요.', fieldErrors: [] }) })
  })
  assert.match(failed, /role="alert"/)
  assert.match(failed, /잠시 후 다시 시도해 주세요/)
  assert.doesNotMatch(failed, /받은 알림이 없습니다/)
})

test('unread filter comes from the URL, keeps its own cache entry and empty message', () => {
  const unread = render(createElement(NotificationsPage), (client) => {
    client.setQueryData(['notifications', 'list', 'all', 0],
      { items: [{ id: '1', type: 'ORDER_ACCEPTED', orderId: '7', productTitle: null, read: true, createdAt: '2026-11-04T01:00:00Z' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1 })
    client.setQueryData(['notifications', 'list', 'unread', 0], { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  }, '/notifications?filter=unread')
  assert.match(unread, /aria-pressed="true">읽지 않음/)
  assert.match(unread, /aria-pressed="false">전체/)
  assert.match(unread, /읽지 않은 알림이 없습니다/)
  assert.doesNotMatch(unread, /href="\/orders\/7"/)

  // 알 수 없는 필터 값은 전체 목록으로 본다.
  const unknown = render(createElement(NotificationsPage), (client) => client.setQueryData(['notifications', 'list', 'all', 0],
    { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }), '/notifications?filter=READ')
  assert.match(unknown, /aria-pressed="true">전체/)
  assert.match(unknown, /받은 알림이 없습니다/)
})

test('list request adds unreadOnly only for the unread filter', async () => {
  const original = globalThis.fetch
  const urls = []
  globalThis.fetch = async (url) => {
    urls.push(url)
    return new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    await getNotifications(2, false)
    await getNotifications(0, true)
  } finally { globalThis.fetch = original }
  assert.deepEqual(urls, ['/api/notifications?page=2&size=20', '/api/notifications?page=0&size=20&unreadOnly=true'])
})

// 읽지 않음 보기에서 알림을 눌러 읽음 처리할 때, 목록이 다시 조회돼 행이 사라지면 행의 이동 콜백이 생략된다.
async function readFromUnreadView(status) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const original = globalThis.fetch
  const calls = { list: 0, count: 0 }
  globalThis.fetch = async (url, options) => {
    if (options?.method === 'POST') return new Response(status === 204 ? null : JSON.stringify({ code: 'INTERNAL_ERROR', message: '오류', fieldErrors: [] }),
      { status, headers: status === 204 ? {} : { 'Content-Type': 'application/json' } })
    calls.count++
    return new Response(JSON.stringify({ count: 0 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  const key = ['notifications', 'list', 'unread', 0]
  const item = { id: '108', type: 'ORDER_DELIVERED', orderId: '30', productTitle: null, read: false, createdAt: '2026-11-05T01:00:00Z' }
  const list = new QueryObserver(client, { queryKey: key,
    queryFn: async () => { calls.list++; return { items: calls.list === 1 ? [item] : [], page: 0, size: 20, totalElements: calls.list === 1 ? 1 : 0, totalPages: 1 } } })
  const stopList = list.subscribe(() => {})
  const counter = new QueryObserver(client, { queryKey: ['notifications', 'unread'],
    queryFn: async () => { calls.count++; return { count: 1 } } })
  const stopCounter = counter.subscribe(() => {})
  try {
    await client.getQueryCache().find({ queryKey: key }).promise
    // 행: 화면에 남아 있는 동안만 호출별 이동 콜백이 실행된다(기존 동작).
    const row = new MutationObserver(client, notificationReadOptions(client, '108'))
    const stopRow = row.subscribe(() => {})
    const moves = []
    await row.mutate(undefined, { onSuccess: () => moves.push('/orders/30') }).catch(() => {})
    stopRow()
    return { moves, calls, items: client.getQueryData(key).items }
  } finally {
    stopList(); stopCounter(); globalThis.fetch = original; client.clear()
  }
}

test('reading from the unread view keeps the row, marks it read and still opens the order', async () => {
  const done = await readFromUnreadView(204)
  assert.deepEqual(done.moves, ['/orders/30'])
  assert.equal(done.calls.list, 1)
  assert.ok(done.calls.count >= 2)
  assert.deepEqual(done.items.map((item) => [item.id, item.read]), [['108', true]])

  // 실패하면 이동하지 않고, 결과가 불명확하므로 목록도 다시 조회한다.
  const failed = await readFromUnreadView(500)
  assert.deepEqual(failed.moves, [])
  assert.equal(failed.calls.list, 2)
})

test('only plain left clicks wait for the read request before navigating', () => {
  const plain = { button: 0, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false }
  assert.equal(waitsForReadBeforeNavigation(plain), true)
  assert.equal(waitsForReadBeforeNavigation({ ...plain, button: 1 }), false)
  for (const key of ['metaKey', 'ctrlKey', 'shiftKey', 'altKey'])
    assert.equal(waitsForReadBeforeNavigation({ ...plain, [key]: true }), false)
})

test('read failure notice is an alert and offers the order without marking it read', () => {
  const withOrder = render(createElement(NotificationReadError, { target: '/orders/7' }), () => {})
  assert.match(withOrder, /role="alert"/)
  assert.match(withOrder, /읽음 처리하지 못했어요/)
  assert.match(withOrder, /href="\/orders\/7"/)
  const withoutOrder = render(createElement(NotificationReadError, { target: null }), () => {})
  assert.match(withoutOrder, /읽음 처리하지 못했어요/)
  assert.doesNotMatch(withoutOrder, /href=/)
})

test('header link shows the unread count only when there is one', () => {
  const withCount = render(createElement(NotificationNavLink), (client) => client.setQueryData(['notifications', 'unread'], { count: 120 }), '/')
  assert.match(withCount, /href="\/notifications"/)
  assert.match(withCount, /\(99\+\)/)
  const none = render(createElement(NotificationNavLink), (client) => client.setQueryData(['notifications', 'unread'], { count: 0 }), '/')
  assert.doesNotMatch(none, /\(/)
})
