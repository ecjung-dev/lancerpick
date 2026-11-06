import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createChatRealtime } from '../src/features/order-chat/realtime.ts'
import { ApiError } from '../src/shared/api/client.ts'

const key = '581f0401-3684-4992-a541-137179be9f70'
const message = (sequence, clientMessageId = key) => ({
  id: sequence,
  orderId: '11',
  sequence,
  senderId: '7',
  clientMessageId,
  content: '메시지',
  createdAt: '2026-11-05T00:00:00Z',
})
const page = (
  items = [],
  lastSequence = '0',
  hasMore = false,
  nextCursor = null,
) => ({
  items,
  lastSequence,
  hasMore,
  nextCursor,
  direction: 'AFTER',
  canSend: true,
})
const tick = () => new Promise((resolve) => setImmediate(resolve))

function fixture(
  overrides = {},
  useDefaultTimers = false,
  createRealtime = createChatRealtime,
) {
  const clients = [],
    timers = [],
    states = [],
    deliveries = [],
    cursors = [],
    requests = []
  let identity = '7'
  const dependencies = {
    createClient() {
      const subscriptions = new Map()
      const client = {
        subscriptions,
        published: [],
        deactivated: false,
        configure(config) {
          Object.assign(this, config)
        },
        activate() {
          this.onConnect()
        },
        async deactivate() {
          this.deactivated = true
        },
        subscribe(destination, callback) {
          subscriptions.set(destination, callback)
        },
        publish(frame) {
          this.published.push(frame)
        },
        receive(item) {
          subscriptions.get('/user/queue/chat')({ body: JSON.stringify(item) })
        },
      }
      clients.push(client)
      return client
    },
    async session(signal) {
      requests.push(signal)
      return { id: identity }
    },
    async csrf() {
      return { headerName: 'X-CSRF-TOKEN', token: `fresh-${clients.length}` }
    },
    async history(orderId, options) {
      cursors.push(options.afterSequence)
      return page([], options.afterSequence)
    },
    socketUrl: () => 'ws://localhost:5173/ws',
    delay(callback, ms) {
      const timer = { callback, ms, cancelled: false }
      timers.push(timer)
      return timer
    },
    cancel(timer) {
      if (timer) timer.cancelled = true
    },
    random: () => 0,
    ...overrides,
  }
  if (useDefaultTimers) {
    delete dependencies.delay
    delete dependencies.cancel
  }
  const transport = createRealtime(
    {
      orderId: '11',
      memberId: '7',
      lastSequence: '0',
      onState: (state) => states.push(state),
      onMessages: (messages, recovered) =>
        deliveries.push({ messages, recovered }),
    },
    dependencies,
  )
  return {
    transport,
    clients,
    timers,
    states,
    deliveries,
    cursors,
    requests,
    identity: (id) => {
      identity = id
    },
  }
}

test('default browser timers preserve the global receiver during recovery, send and reconnect', async () => {
  const originalSetTimeout = globalThis.setTimeout
  const originalClearTimeout = globalThis.clearTimeout
  const timers = []
  globalThis.setTimeout = function (callback, ms) {
    assert.equal(
      this,
      globalThis,
      'setTimeout requires the browser global receiver',
    )
    const timer = { callback, ms, cancelled: false }
    timers.push(timer)
    return timer
  }
  globalThis.clearTimeout = function (timer) {
    assert.equal(
      this,
      globalThis,
      'clearTimeout requires the browser global receiver',
    )
    if (timer) timer.cancelled = true
  }
  // 브라우저의 this 검사를 하는 타이머가 설치된 상태에서 기본 의존성을 읽는다.
  const { createChatRealtime: withBrowserTimers } = await import(
    '../src/features/order-chat/realtime.ts?browser-timer-receiver'
  )
  const f = fixture({}, true, withBrowserTimers)
  try {
    f.transport.start()
    await tick()
    assert.equal(f.transport.isConnected(), true)
    timers.find((timer) => timer.ms === 15000 && !timer.cancelled).callback()
    await tick()
    const sent = f.transport.send({ clientMessageId: key, content: '메시지' })
    f.clients[0].receive(message('1'))
    await sent
    f.clients[0].onWebSocketClose({ code: 1006 })
    timers.find((timer) => timer.ms === 1000 && !timer.cancelled).callback()
    await tick()
    assert.equal(f.clients.length, 2)
    f.transport.stop()
    assert.equal(
      timers.every((timer) => timer.cancelled || timer.ms === 1000),
      true,
    )
  } finally {
    try {
      f.transport.stop()
    } finally {
      globalThis.setTimeout = originalSetTimeout
      globalThis.clearTimeout = originalClearTimeout
    }
  }
})

test('CONNECT uses fresh masked CSRF and expected account; only private destinations are subscribed', async () => {
  const f = fixture()
  f.transport.start()
  await tick()
  const client = f.clients[0]
  assert.equal(client.brokerURL, 'ws://localhost:5173/ws')
  assert.deepEqual(client.connectHeaders, {
    'X-CSRF-TOKEN': 'fresh-0',
    'X-Chat-Member-Id': '7',
  })
  assert.equal(client.reconnectDelay, 0)
  assert.deepEqual(
    [...client.subscriptions.keys()],
    ['/user/queue/chat', '/user/queue/chat-errors'],
  )
  assert.deepEqual(f.states, ['connecting', 'connected'])
  f.transport.stop()
})

test('future live sequence cannot skip missing history; paginated recovery advances only DB cursor', async () => {
  let calls = 0
  const cursors = []
  const f = fixture({
    history: async (_, options) => {
      cursors.push(options.afterSequence)
      if (calls++ === 0) return page()
      if (options.afterSequence === '0')
        return page([message('1')], '3', true, '1')
      return page([message('2'), message('3')], '3')
    },
  })
  f.transport.start()
  await tick()
  f.clients[0].receive(message('3'))
  await tick()
  assert.deepEqual(cursors, ['0', '0', '1'])
  assert.deepEqual(
    f.deliveries.flatMap((value) =>
      value.messages.map((item) => item.sequence),
    ),
    ['3', '1', '2', '3'],
  )
  await f.transport.recover()
  assert.equal(cursors.at(-1), '3')
  f.transport.stop()
})

test('socket write completes only on stored acknowledgement; other order/member cannot acknowledge', async () => {
  const f = fixture()
  f.transport.start()
  await tick()
  let completed = false
  const result = f.transport
    .send({ clientMessageId: key, content: '메시지' })
    .then((value) => {
      completed = true
      return value
    })
  const client = f.clients[0]
  client.receive({ ...message('1'), orderId: '12' })
  client.receive({ ...message('1'), senderId: '8' })
  await tick()
  assert.equal(completed, false)
  client.receive(message('1'))
  assert.equal((await result).sequence, '1')
  assert.equal(client.published.length, 1)
  assert.deepEqual(JSON.parse(client.published[0].body), {
    clientMessageId: key,
    content: '메시지',
  })
  f.transport.stop()
})

test('uncertain write timeout and reconnect never resubmit a mutation', async () => {
  const f = fixture()
  f.transport.start()
  await tick()
  const result = assert.rejects(
    f.transport.send({ clientMessageId: key, content: '메시지' }),
    /다시 확인/,
  )
  f.timers.find((timer) => timer.ms === 8000).callback()
  await result
  f.clients[0].onWebSocketClose({ code: 1006 })
  f.timers.find((timer) => timer.ms === 1000 && !timer.cancelled).callback()
  await tick()
  assert.equal(f.clients.length, 2)
  assert.equal(f.clients[0].published.length, 1)
  assert.equal(f.clients[1].published.length, 0)
  assert.equal(f.clients[1].connectHeaders['X-CSRF-TOKEN'], 'fresh-1')
  f.transport.stop()
})

test('missed final event is restored by periodic lookup, without a later live frame', async () => {
  let calls = 0
  const f = fixture({
    history: async () => (calls++ ? page([message('1')], '1') : page()),
  })
  f.transport.start()
  await tick()
  f.timers.find((timer) => timer.ms === 15000 && !timer.cancelled).callback()
  await tick()
  assert.equal(f.deliveries.at(-1).messages[0].sequence, '1')
  f.transport.stop()
})

test('account mismatch, session expiry and CSRF rejection stop reconnect and hide private history', async () => {
  for (const kind of ['changed', 'closed', 'csrf', 'http']) {
    const f = fixture()
    f.transport.start()
    await tick()
    if (kind === 'changed') {
      f.identity('8')
      await f.transport.recover()
    }
    if (kind === 'closed') f.clients[0].onWebSocketClose({ code: 4401 })
    if (kind === 'csrf')
      f.clients[0].onStompError({ body: '{"code":"CSRF_INVALID"}' })
    if (kind === 'http') {
      f.identity('8')
      await f.transport.recover()
    }
    assert.equal(f.states.at(-1), 'auth-required')
    assert.equal(f.transport.isConnected(), false)
    assert.equal(f.clients[0].deactivated, true)
    assert.equal(
      f.timers.filter((timer) => timer.ms < 15000 && !timer.cancelled).length,
      0,
    )
    f.transport.stop()
  }
  const denied = fixture({
    session: async () => {
      throw new ApiError(401, {
        code: 'UNAUTHORIZED',
        message: '로그인',
        fieldErrors: [],
      })
    },
  })
  denied.transport.start()
  await tick()
  assert.equal(denied.states.at(-1), 'auth-required')
  assert.equal(denied.clients.length, 0)
  denied.transport.stop()
})

test('unmount aborts requests, cancels timers, closes socket and ignores late events', async () => {
  const f = fixture()
  f.transport.start()
  await tick()
  const result = assert.rejects(
    f.transport.send({ clientMessageId: key, content: '메시지' }),
  )
  f.transport.stop()
  await result
  const count = f.deliveries.length
  f.clients[0].receive(message('1'))
  f.clients[0].onWebSocketClose({ code: 1006 })
  await tick()
  assert.equal(f.deliveries.length, count)
  assert.equal(
    f.requests.every((signal) => signal.aborted),
    true,
  )
  assert.equal(
    f.timers.every((timer) => timer.cancelled),
    true,
  )
})

test('private business failure is definitive, malformed frames do not enter cache', async () => {
  const f = fixture()
  f.transport.start()
  await tick()
  const result = assert.rejects(
    f.transport.send({ clientMessageId: key, content: '메시지' }),
    (error) => error instanceof ApiError && error.code === 'CHAT_CLOSED',
  )
  f.clients[0].subscriptions.get('/user/queue/chat-errors')({
    body: JSON.stringify({
      orderId: '11',
      clientMessageId: key,
      code: 'CHAT_CLOSED',
      message: '종료된 주문',
    }),
  })
  await result
  const count = f.deliveries.length
  f.clients[0].receive({ ...message('1'), content: 'x'.repeat(2001) })
  f.clients[0].receive({ ...message('1'), id: 9007199254740992 })
  assert.equal(f.deliveries.length, count)
  f.transport.stop()
})
