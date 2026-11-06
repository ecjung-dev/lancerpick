import type { FieldError } from '../../shared/api/client.ts'

export interface ChatMessage {
  id: string
  orderId: string
  sequence: string
  senderId: string
  clientMessageId: string
  content: string
  createdAt: string
}

export interface ChatMessagesPage {
  items: ChatMessage[]
  direction: 'BEFORE' | 'AFTER'
  nextCursor: string | null
  hasMore: boolean
  lastSequence: string
  canSend: boolean
}

export interface SendChatMessageInput {
  clientMessageId: string
  content: string
}

export interface ChatMessagesOptions {
  beforeSequence?: string
  afterSequence?: string
  limit?: number
}

export type ChatConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'offline'
  | 'auth-required'

export interface ChatCsrfToken {
  headerName: string
  token: string
}

/** REST 오류와 같은 code·message·fieldErrors·traceId에 HTTP 상태 대신 status를 싣는다. */
export interface ChatSendError {
  orderId: string | null
  clientMessageId: string | null
  status: number
  code: string
  message: string
  fieldErrors: FieldError[]
  traceId: string
}
