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
