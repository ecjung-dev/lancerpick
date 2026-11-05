import type { ChatMessage, ChatMessagesPage } from './types.ts'

export function mergeChatMessages(pages: ChatMessagesPage[]): ChatMessage[] {
  const messages = new Map<string, ChatMessage>()
  for (const page of pages)
    for (const message of page.items) messages.set(message.id, message)
  return [...messages.values()].sort((left, right) => {
    const a = BigInt(left.sequence)
    const b = BigInt(right.sequence)
    return a < b ? -1 : a > b ? 1 : 0
  })
}
