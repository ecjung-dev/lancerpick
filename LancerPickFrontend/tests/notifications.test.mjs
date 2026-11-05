import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { notificationMessage, notificationTarget, unreadBadge } from '../src/features/notifications/messages.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-notifications',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { NotificationsPage } = await server.ssrLoadModule('/src/features/notifications/pages/NotificationsPage.tsx')
const { NotificationNavLink } = await server.ssrLoadModule('/src/features/notifications/components/NotificationNavLink.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

test('every order event type has its own Korean message and unknown types fall back', () => {
  const types = ['CREATED', 'ACCEPTED', 'REJECTED', 'PAID', 'DELIVERED', 'REVISION_REQUESTED',
    'CANCELLATION_REQUESTED', 'CANCELLATION_REJECTED', 'CANCELLATION_WITHDRAWN', 'CANCELLED', 'COMPLETED']
  const fallback = notificationMessage('ORDER_SOMETHING_NEW')
  const messages = new Set(types.map((type) => notificationMessage(`ORDER_${type}`)))
  assert.equal(messages.size, types.length)
  assert.ok(!messages.has(fallback))
  assert.equal(fallback, '거래 상태가 변경되었습니다.')
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
  const html = render(createElement(NotificationsPage), (client) => client.setQueryData(['notifications', 'list', 1], {
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
  assert.equal(html.match(/읽지 않음/g)?.length, 1)
  assert.match(html, /2페이지/)
  assert.match(html, /disabled="">다음/)
})

test('empty list, login requirement and server errors are shown differently', () => {
  const empty = render(createElement(NotificationsPage), (client) => client.setQueryData(['notifications', 'list', 0],
    { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
  assert.match(empty, /받은 알림이 없습니다/)

  const unauthorized = render(createElement(NotificationsPage), (client) => {
    client.getQueryCache().build(client, { queryKey: ['notifications', 'list', 0], queryFn: async () => undefined }).setState({
      status: 'error', fetchStatus: 'idle', error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] }) })
  })
  assert.match(unauthorized, /로그인 후 알림을 확인할 수 있습니다/)
  assert.match(unauthorized, /href="\/login\?next=%2Fnotifications"/)

  const failed = render(createElement(NotificationsPage), (client) => {
    client.getQueryCache().build(client, { queryKey: ['notifications', 'list', 0], queryFn: async () => undefined }).setState({
      status: 'error', fetchStatus: 'idle', error: new ApiError(500, { code: 'INTERNAL_ERROR', message: '잠시 후 다시 시도해 주세요.', fieldErrors: [] }) })
  })
  assert.match(failed, /role="alert"/)
  assert.match(failed, /잠시 후 다시 시도해 주세요/)
  assert.doesNotMatch(failed, /받은 알림이 없습니다/)
})

test('header link shows the unread count only when there is one', () => {
  const withCount = render(createElement(NotificationNavLink), (client) => client.setQueryData(['notifications', 'unread'], { count: 120 }), '/')
  assert.match(withCount, /href="\/notifications"/)
  assert.match(withCount, /\(99\+\)/)
  const none = render(createElement(NotificationNavLink), (client) => client.setQueryData(['notifications', 'unread'], { count: 0 }), '/')
  assert.doesNotMatch(none, /\(/)
})
