import { apiRequest } from '../../shared/api/client.ts'
import { prepareChatMessage, validChatSequence } from './validation.ts'
import type {
  ChatCsrfToken,
  ChatMessage,
  ChatMessagesOptions,
  ChatMessagesPage,
  SendChatMessageInput,
} from './types.ts'

/** 소켓 연결마다 새 마스킹 토큰을 받아 이전 세션의 CONNECT 토큰을 재사용하지 않는다. */
export function getChatCsrfToken(signal?: AbortSignal) {
  return apiRequest<ChatCsrfToken>('/auth/csrf', { signal, cache: 'no-store' })
}

function messagesPath(orderId: string) {
  if (!validChatSequence(orderId)) throw new Error('주문 번호를 확인해 주세요.')
  return `/orders/${orderId}/chat/messages`
}

export function getChatMessages(
  orderId: string,
  options: ChatMessagesOptions = {},
  signal?: AbortSignal,
) {
  const path = messagesPath(orderId)
  const { beforeSequence, afterSequence, limit = 50 } = options
  if (
    !Number.isInteger(limit) ||
    limit < 1 ||
    limit > 100 ||
    (beforeSequence !== undefined && !validChatSequence(beforeSequence)) ||
    (afterSequence !== undefined && !validChatSequence(afterSequence, true)) ||
    (beforeSequence !== undefined && afterSequence !== undefined)
  ) {
    throw new Error('대화 조회 조건을 확인해 주세요.')
  }
  const params = new URLSearchParams({ limit: String(limit) })
  if (beforeSequence !== undefined) params.set('beforeSequence', beforeSequence)
  if (afterSequence !== undefined) params.set('afterSequence', afterSequence)
  return apiRequest<ChatMessagesPage>(`${path}?${params}`, {
    signal,
    cache: 'no-store',
  })
}

export function sendChatMessage(orderId: string, input: SendChatMessageInput) {
  return apiRequest<ChatMessage>(messagesPath(orderId), {
    method: 'POST',
    json: prepareChatMessage(input),
  })
}
