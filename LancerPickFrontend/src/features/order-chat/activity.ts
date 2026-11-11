import type { ChatThread } from './types.ts'

/** Session-only hint, never a server unread count. */
export interface ChatActivity { startedAt: string; seen: Record<string, string> }
export const activityKey = (memberId: string) => ['order-chat', 'activity', memberId] as const
export function hasNewChat(thread: ChatThread, memberId: string, activity: ChatActivity) {
  return thread.lastSenderId !== null && thread.lastSenderId !== memberId &&
    thread.lastMessageAt !== null &&
    Date.parse(thread.lastMessageAt) > Date.parse(activity.seen[thread.orderId] ?? activity.startedAt)
}
