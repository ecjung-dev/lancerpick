import type { Client, IMessage } from '@stomp/stompjs'
import { ApiError } from '../../shared/api/client.ts'
import { getMemberSession } from '../auth/api.ts'
import { getChatCsrfToken, getChatMessages } from './api.ts'
import { prepareChatMessage, validChatSequence } from './validation.ts'
import type {
  ChatConnectionState,
  ChatMessage,
  ChatMessagesPage,
  ChatSendError,
  SendChatMessageInput,
} from './types.ts'

interface ChatRealtimeOptions {
  orderId: string
  memberId: string
  lastSequence: string
  onState: (state: ChatConnectionState) => void
  onMessages: (messages: ChatMessage[], recovered?: ChatMessagesPage) => void
}

// 테스트에서 통신·시간만 교체한다. 실제 전송은 자동 재시도하지 않는다.
interface ChatRealtimeDependencies {
  createClient: () => Client | Promise<Client>
  session: typeof getMemberSession
  csrf: typeof getChatCsrfToken
  history: typeof getChatMessages
  socketUrl: () => string
  delay: typeof setTimeout
  cancel: typeof clearTimeout
  random: () => number
}

const defaults: ChatRealtimeDependencies = {
  createClient: async () => new (await import('@stomp/stompjs')).Client(),
  session: getMemberSession,
  csrf: getChatCsrfToken,
  history: getChatMessages,
  socketUrl: () =>
    `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`,
  // 의존성 객체의 메서드로 호출해도 브라우저 타이머의 this는 전역 객체를 유지한다.
  delay: globalThis.setTimeout.bind(globalThis),
  cancel: globalThis.clearTimeout.bind(globalThis),
  random: Math.random,
}

/** 서버가 보낸 오류 상태를 쓴다. 상태가 없는 이전 형식 프레임은 기존처럼 저장 실패만 503, 나머지는 409로 본다. */
export function chatErrorStatus(error: Pick<ChatSendError, 'status' | 'code'>) {
  const { status } = error as { status?: unknown }
  if (typeof status === 'number' && Number.isInteger(status) && status >= 400 && status <= 599)
    return status
  return error.code === 'CHAT_SEND_FAILED' ? 503 : 409
}

function readMessage(body: string): ChatMessage | undefined {
  try {
    const value = JSON.parse(body) as ChatMessage
    if (
      value &&
      [value.id, value.orderId, value.senderId, value.sequence].every(
        (item) => typeof item === 'string',
      ) &&
      validChatSequence(value.id) &&
      validChatSequence(value.orderId) &&
      validChatSequence(value.senderId) &&
      validChatSequence(value.sequence) &&
      typeof value.createdAt === 'string' &&
      Number.isFinite(Date.parse(value.createdAt))
    ) {
      prepareChatMessage(value)
      return value
    }
  } catch {
    /* 형식이 잘못된 프레임은 화면과 캐시에 넣지 않는다. */
  }
}

/** 소켓은 빠른 전달, DB 순번 조회는 누락 복구를 맡는다. 구독 완료를 보장하지 않는 simple broker도 복구한다. */
export function createChatRealtime(
  options: ChatRealtimeOptions,
  overrides: Partial<ChatRealtimeDependencies> = {},
) {
  const dependencies = { ...defaults, ...overrides }
  const abort = new AbortController()
  let stopped = false
  let blocked = false
  let connected = false
  let attempts = 0
  let cursor = options.lastSequence
  let client: Client | undefined
  let retryTimer: ReturnType<typeof setTimeout> | undefined
  let recoveryTimer: ReturnType<typeof setTimeout> | undefined
  let recovering: Promise<void> | undefined
  const pending = new Map<
    string,
    {
      resolve: (message: ChatMessage) => void
      reject: (error: Error) => void
      timer: ReturnType<typeof setTimeout>
    }
  >()

  function rejectPending() {
    for (const item of pending.values()) {
      dependencies.cancel(item.timer)
      item.reject(new Error('전송 결과를 다시 확인해 주세요.'))
    }
    pending.clear()
  }

  function block() {
    if (stopped || blocked) return
    blocked = true
    connected = false
    dependencies.cancel(retryTimer)
    dependencies.cancel(recoveryTimer)
    rejectPending()
    options.onState('auth-required')
    void client?.deactivate({ force: true })
  }

  async function checkMember() {
    if ((await dependencies.session(abort.signal)).id !== options.memberId) {
      block()
      throw new Error('로그인 계정을 다시 확인해 주세요.')
    }
  }

  function accept(messages: ChatMessage[], recovered?: ChatMessagesPage) {
    if (stopped || blocked) return
    const ownOrder = messages.filter(
      (message) => message.orderId === options.orderId,
    )
    options.onMessages(ownOrder, recovered)
    for (const message of ownOrder) {
      if (message.senderId !== options.memberId) continue
      const item = pending.get(message.clientMessageId)
      if (!item) continue
      dependencies.cancel(item.timer)
      pending.delete(message.clientMessageId)
      item.resolve(message)
    }
  }

  function recover(): Promise<void> {
    if (stopped || blocked) return Promise.resolve()
    if (recovering) return recovering
    recovering = (async () => {
      await checkMember()
      // 실시간 프레임 순번으로 cursor를 건너뛰지 않는다. DB에서 연속 조회한 위치만 전진한다.
      for (;;) {
        const page = await dependencies.history(
          options.orderId,
          { afterSequence: cursor, limit: 100 },
          abort.signal,
        )
        if (stopped || blocked) return
        await checkMember()
        if (stopped || blocked) return
        accept(page.items, page)
        if (!page.hasMore) {
          if (BigInt(page.lastSequence) > BigInt(cursor))
            cursor = page.lastSequence
          return
        }
        if (!page.nextCursor || BigInt(page.nextCursor) <= BigInt(cursor))
          throw new Error('대화 조회 위치를 확인해 주세요.')
        cursor = page.nextCursor
      }
    })()
      .catch((error: unknown) => {
        if (error instanceof ApiError && [401, 403, 404].includes(error.status))
          block()
        // 통신 실패는 마지막 성공 cursor를 유지하여 다음 조회에서 이어 받는다.
      })
      .finally(() => {
        recovering = undefined
      })
    return recovering
  }

  function scheduleRecovery() {
    dependencies.cancel(recoveryTimer)
    if (stopped || blocked) return
    recoveryTimer = dependencies.delay(() => {
      void recover().finally(scheduleRecovery)
    }, 15000)
  }

  function disconnected(code?: number) {
    if (stopped || blocked) return
    connected = false
    rejectPending()
    if (code === 4401 || code === 4403) {
      block()
      return
    }
    if (code === 4429 || attempts >= 8) {
      options.onState('offline')
      return
    }
    options.onState('reconnecting')
    dependencies.cancel(retryTimer)
    const wait =
      Math.min(30000, 1000 * 2 ** attempts++) +
      Math.floor(dependencies.random() * 500)
    retryTimer = dependencies.delay(() => {
      void connect()
    }, wait)
  }

  function receive(frame: IMessage) {
    const message = readMessage(frame.body)
    if (message) {
      accept([message])
      void recover()
    }
  }

  function receiveError(frame: IMessage) {
    try {
      const error = JSON.parse(frame.body) as ChatSendError
      if (error.orderId !== options.orderId || !error.clientMessageId) return
      const item = pending.get(error.clientMessageId)
      if (
        !item ||
        typeof error.code !== 'string' ||
        typeof error.message !== 'string'
      )
        return
      dependencies.cancel(item.timer)
      pending.delete(error.clientMessageId)
      item.reject(
        new ApiError(chatErrorStatus(error), {
          code: error.code,
          message: error.message,
          fieldErrors: Array.isArray(error.fieldErrors) ? error.fieldErrors : [],
          traceId: typeof error.traceId === 'string' ? error.traceId : undefined,
        }),
      )
    } catch {
      /* 서버 원문이나 불완전한 오류는 표시하지 않는다. 타임아웃 후 DB 조회로 확인한다. */
    }
  }

  async function connect() {
    if (stopped || blocked) return
    const previous = client
    client = undefined
    connected = false
    await previous?.deactivate({ force: true })
    try {
      await checkMember()
      const csrf = await dependencies.csrf(abort.signal)
      if (stopped || blocked) return
      const next = await dependencies.createClient()
      if (stopped || blocked) return
      client = next
      next.configure({
        brokerURL: dependencies.socketUrl(),
        reconnectDelay: 0,
        connectionTimeout: 10000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        debug: () => {},
        connectHeaders: {
          [csrf.headerName]: csrf.token,
          'X-Chat-Member-Id': options.memberId,
        },
        onConnect: () => {
          if (stopped || blocked || client !== next) return
          connected = true
          attempts = 0
          next.subscribe('/user/queue/chat', receive)
          next.subscribe('/user/queue/chat-errors', receiveError)
          options.onState('connected')
          void recover()
        },
        onWebSocketClose: (event) => {
          if (client === next) disconnected(event.code)
        },
        // CONNECT 인증/CSRF 및 기본 거부 오류는 재접속 폭주 대신 재확인을 요구한다.
        onStompError: () => block(),
      })
      next.activate()
    } catch (error) {
      if (error instanceof ApiError && [401, 403].includes(error.status))
        block()
      else disconnected()
    }
  }

  return {
    start() {
      options.onState('connecting')
      void connect()
      scheduleRecovery()
    },
    recover,
    isConnected: () => connected && !stopped && !blocked,
    send(input: SendChatMessageInput): Promise<ChatMessage> {
      const body = prepareChatMessage(input)
      const activeClient = client
      if (!connected || stopped || blocked || !activeClient)
        return Promise.reject(new Error('실시간 연결을 확인해 주세요.'))
      if (pending.has(body.clientMessageId))
        return Promise.reject(new Error('이미 전송 중이에요.'))
      return new Promise((resolve, reject) => {
        const timer = dependencies.delay(() => {
          pending.delete(body.clientMessageId)
          reject(new Error('전송 결과를 다시 확인해 주세요.'))
          void recover()
        }, 8000)
        pending.set(body.clientMessageId, { resolve, reject, timer })
        try {
          activeClient.publish({
            destination: `/app/orders/${options.orderId}/chat/messages`,
            body: JSON.stringify(body),
            headers: { 'content-type': 'application/json' },
          })
        } catch {
          dependencies.cancel(timer)
          pending.delete(body.clientMessageId)
          reject(new Error('전송 결과를 다시 확인해 주세요.'))
        }
      })
    },
    stop() {
      stopped = true
      connected = false
      abort.abort()
      dependencies.cancel(retryTimer)
      dependencies.cancel(recoveryTimer)
      rejectPending()
      void client?.deactivate({ force: true })
    },
  }
}
