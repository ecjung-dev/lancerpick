import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import {
  getChatCsrfToken,
  getChatThreads,
  getChatMessages,
  sendChatMessage,
} from '../src/features/order-chat/api.ts'
import {
  prepareChatMessage,
  chatStatusAllowsSend,
} from '../src/features/order-chat/validation.ts'
import { mergeChatMessages } from '../src/features/order-chat/messages.ts'
import {
  captureAccountScope,
  resetAccountState,
} from '../src/features/auth/session.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const key = '581f0401-3684-4992-a541-137179be9f70'
const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  clearCsrfToken()
})
const server = await createServer({
  configFile: false,
  cacheDir: 'node_modules/.vite-test-order-chat',
  server: { middlewareMode: true, ws: false, watch: null },
  appType: 'custom',
})
const { ChatMessages, OrderChatPanel } = await server.ssrLoadModule(
  '/src/features/order-chat/components/OrderChatPanel.tsx',
)
after(async () => {
  await server.close()
})

test('conversation list sends only keyword and paging, preserving literal search characters', async () => {
  let requested
  globalThis.fetch = async (url) => {
    requested = url
    return new Response(
      JSON.stringify({
        items: [],
        page: 2,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
      { headers: { 'Content-Type': 'application/json' } },
    )
  }
  await getChatThreads('로고_%!', 2)
  const url = new URL(requested, 'http://localhost')
  assert.equal(url.pathname, '/api/me/order-chats')
  assert.equal(url.searchParams.get('q'), '로고_%!')
  assert.equal(url.searchParams.get('page'), '2')
  assert.equal(url.searchParams.get('size'), '20')
  assert.equal(url.searchParams.size, 3)
  assert.throws(() => getChatThreads('가'.repeat(101), 0))
  assert.throws(() => getChatThreads('', -1))
})

test('account switch while CSRF is pending prevents the old chat from being sent', async () => {
  const client = new QueryClient()
  client.setQueryData(['auth', 'me'], { id: '7' })
  let ready, release
  const started = new Promise((resolve) => {
    ready = resolve
  })
  const csrf = new Promise((resolve) => {
    release = resolve
  })
  let writes = 0
  let csrfRequests = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') {
      if (++csrfRequests === 1) {
        ready()
        return csrf
      }
      return new Response(
        JSON.stringify({ token: 'new', headerName: 'X-CSRF-TOKEN' }),
        { headers: { 'Content-Type': 'application/json' } },
      )
    }
    writes++
    return new Response('{}')
  }
  try {
    const pending = sendChatMessage(
      '11',
      { clientMessageId: key, content: '이전 계정의 메시지' },
      captureAccountScope(client),
    )
    const rejected = assert.rejects(
      pending,
      (error) => error.name === 'AbortError',
    )
    await started
    await resetAccountState(client, null)
    release(
      new Response(
        JSON.stringify({ token: 'old', headerName: 'X-CSRF-TOKEN' }),
        { headers: { 'Content-Type': 'application/json' } },
      ),
    )
    await rejected
    assert.equal(writes, 0)
  } finally {
    client.clear()
  }
})

const message = (id, sequence = id) => ({
  id,
  orderId: '11',
  sequence,
  senderId: '7',
  clientMessageId: key,
  content: '<script>private</script>\n두 줄',
  createdAt: '2026-11-05T00:00:00Z',
})
const page = (items = [], canSend = true) => ({
  items,
  direction: 'BEFORE',
  nextCursor: null,
  hasMore: false,
  lastSequence: items.at(-1)?.sequence ?? '0',
  canSend,
})

test('socket CONNECT fetches a fresh CSRF token with session, no-store and cancellation', async () => {
  const controller = new AbortController()
  let requests = 0
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/auth/csrf')
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    assert.equal(init.signal, controller.signal)
    return Response.json({
      headerName: 'X-CSRF-TOKEN',
      token: `fresh-${++requests}`,
    })
  }
  assert.equal((await getChatCsrfToken(controller.signal)).token, 'fresh-1')
  assert.equal((await getChatCsrfToken(controller.signal)).token, 'fresh-2')
})

test('chat lookup preserves BIGINT cursors, session, cancellation and no-store', async () => {
  const controller = new AbortController()
  const urls = []
  globalThis.fetch = async (url, init) => {
    urls.push(url)
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    assert.equal(init.signal, controller.signal)
    return Response.json(page())
  }
  await getChatMessages(
    '9007199254740993',
    { beforeSequence: '9007199254740995', limit: 20 },
    controller.signal,
  )
  await getChatMessages('11', { afterSequence: '0' }, controller.signal)
  assert.deepEqual(urls, [
    '/api/orders/9007199254740993/chat/messages?limit=20&beforeSequence=9007199254740995',
    '/api/orders/11/chat/messages?limit=50&afterSequence=0',
  ])
})

test('invalid IDs or mixed cursors never reach the network', () => {
  globalThis.fetch = async () => {
    throw new Error('must not fetch')
  }
  for (const id of ['0', '../1', '9223372036854775808'])
    assert.throws(() => getChatMessages(id))
  for (const options of [
    { beforeSequence: '0' },
    { afterSequence: '-1' },
    { beforeSequence: '1', afterSequence: '0' },
    { limit: 101 },
    { limit: 1.5 },
  ])
    assert.throws(() => getChatMessages('11', options))
})

test('send uses CSRF and only content/client key; uncertain writes are never retried automatically', async () => {
  const writes = []
  globalThis.fetch = async (url, init) => {
    if (url === '/api/auth/csrf')
      return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.headers.get('X-CSRF-TOKEN'), 'csrf')
    writes.push(JSON.parse(init.body))
    throw new TypeError('lost response')
  }
  const input = {
    clientMessageId: key,
    content: '  첫 줄\r\n두 줄 ',
    senderId: '999',
  }
  await assert.rejects(sendChatMessage('11', input))
  assert.equal(writes.length, 1)
  await assert.rejects(sendChatMessage('11', input))
  assert.deepEqual(writes, [
    { clientMessageId: key, content: '첫 줄\n두 줄' },
    { clientMessageId: key, content: '첫 줄\n두 줄' },
  ])
})

test('Unicode limits and terminal policy match the server', () => {
  assert.equal(
    [
      ...prepareChatMessage({
        clientMessageId: key,
        content: '😀'.repeat(2000),
      }).content,
    ].length,
    2000,
  )
  assert.throws(() =>
    prepareChatMessage({ clientMessageId: key, content: '😀'.repeat(2001) }),
  )
  assert.throws(() =>
    prepareChatMessage({ clientMessageId: key, content: '\u2003 \n' }),
  )
  for (const state of [
    'COMPLETED',
    'CANCELLED',
    'REJECTED',
    'TERMINATED',
    'UNKNOWN',
  ])
    assert.equal(chatStatusAllowsSend(state), false)
  assert.equal(chatStatusAllowsSend('AWAITING_CONTRACT'), true)
})

test('history pages deduplicate and sort by exact sequence beyond JS safe integers', () => {
  const first = message('1', '9007199254740993')
  const second = message('2', '9007199254740994')
  const merged = mergeChatMessages([page([second]), page([first, second])])
  assert.deepEqual(
    merged.map((item) => item.id),
    ['1', '2'],
  )
})

test('message rendering escapes HTML and shows each sender without executing content', () => {
  const html = renderToStaticMarkup(
    createElement(ChatMessages, { messages: [message('1')], memberId: '7' }),
  )
  assert.match(html, /&lt;script&gt;private&lt;\/script&gt;/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, />나</)
})

function renderPanel(status, denied = false) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['order-chat', 'member'], { id: '7' })
  const queryKey = ['order-chat', 'messages', '11', '7']
  client.setQueryData(queryKey, {
    pages: [page([message('1')], status === 'IN_PROGRESS')],
    pageParams: [undefined],
  })
  if (denied)
    client
      .getQueryCache()
      .find({ queryKey })
      .setState({ status: 'error', error: new Error('접근할 수 없습니다.') })
  try {
    return renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(
          MemoryRouter,
          null,
          createElement(OrderChatPanel, { orderId: '11', orderStatus: status }),
        ),
      ),
    )
  } finally {
    client.clear()
  }
}

test('closed order keeps history while hiding composition and failed access hides cached content', () => {
  const closed = renderPanel('CANCELLED')
  assert.match(closed, /private/)
  assert.match(closed, /조회만/)
  assert.doesNotMatch(closed, /<textarea/)
  assert.match(renderPanel('IN_PROGRESS'), /<textarea/)
  const denied = renderPanel('IN_PROGRESS', true)
  assert.doesNotMatch(denied, /private|<textarea/)
  assert.match(denied, /접근할 수 없습니다/)
})

test('account switch cancels old chat requests and removes history and member caches', async () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  client.setQueryData(['auth', 'me'], { id: '7' })
  client.setQueryData(['order-chat', 'member'], { id: '7' })
  client.setQueryData(
    ['order-chat', 'messages', '11', '7'],
    page([message('1')]),
  )
  let started
  let aborted = false
  const ready = new Promise((resolve) => {
    started = resolve
  })
  const pending = client
    .fetchQuery({
      queryKey: ['order-chat', 'in-flight'],
      queryFn: ({ signal }) =>
        new Promise((resolve, reject) => {
          signal.addEventListener('abort', () => {
            aborted = true
            reject(new Error('abort'))
          })
          started()
        }),
    })
    .catch(() => undefined)
  await ready
  await resetAccountState(client, { id: '8' })
  await pending
  assert.equal(aborted, true)
  assert.equal(
    client.getQueryCache().findAll({ queryKey: ['order-chat'] }).length,
    0,
  )
  assert.equal(client.getQueryData(['auth', 'me']).id, '8')
  client.clear()
})
