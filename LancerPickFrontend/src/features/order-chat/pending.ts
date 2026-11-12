import type { QueryClient } from '@tanstack/react-query'
import { captureAccountScope, memberQueryKey } from '../auth/session.ts'
import type { Member } from '../auth/types.ts'
import { normalizeText } from '../../shared/lib/text.ts'
import type { SendChatMessageInput } from './types.ts'

const pendingChatPrefix = ['order-chat', 'pending-send'] as const
export const pendingChatKey = (memberId: string, orderId: string) =>
  [...pendingChatPrefix, memberId, orderId] as const

export interface PendingChatSend {
  input: SendChatMessageInput
  phase: 'sending' | 'uncertain'
  revision: number
}

export interface ChatSendAttempt extends PendingChatSend {
  isCurrentAccount: () => boolean
}

function currentMember(client: QueryClient, memberId: string) {
  return client.getQueryData<Member | null>(memberQueryKey)?.id === memberId
}

/** Unconfirmed commands outlive the room component; ordinary unsent text still belongs to it. */
export function beginChatSend(
  client: QueryClient,
  memberId: string,
  orderId: string,
  content: string,
): ChatSendAttempt | undefined {
  if (!currentMember(client, memberId)) return
  const key = pendingChatKey(memberId, orderId)
  const previous = client.getQueryData<PendingChatSend | null>(key)
  if (previous?.phase === 'sending') return
  // Do not expire unresolved UUIDs by GC. Logout/account switching clears this personal cache.
  client.setQueryDefaults(pendingChatPrefix, { gcTime: Infinity })
  const pending: PendingChatSend = {
    input: previous?.input ?? {
      clientMessageId: crypto.randomUUID(),
      content: normalizeText(content),
    },
    phase: 'sending',
    revision: (previous?.revision ?? 0) + 1,
  }
  client.setQueryData(key, pending)
  return { ...pending, isCurrentAccount: captureAccountScope(client) }
}

/** Late failures cannot overwrite a newer retry; confirmed success settles the same UUID. */
export function settleChatSend(
  client: QueryClient,
  memberId: string,
  orderId: string,
  attempt: ChatSendAttempt,
  outcome: 'confirmed' | 'rejected' | 'uncertain',
) {
  if (!attempt.isCurrentAccount()) return
  const key = pendingChatKey(memberId, orderId)
  const current = client.getQueryData<PendingChatSend | null>(key)
  if (
    !current ||
    current.input.clientMessageId !== attempt.input.clientMessageId
  )
    return
  if (outcome !== 'confirmed' && current.revision !== attempt.revision) return
  // A rejection of a retry (e.g. rate limiting) says nothing about the first unknown result.
  const unresolved =
    outcome === 'uncertain' || (outcome === 'rejected' && attempt.revision > 1)
  client.setQueryData(
    key,
    unresolved ? { ...current, phase: 'uncertain' } : null,
  )
  return unresolved ? 'uncertain' : 'cleared'
}

/** A matching message found in authenticated history is also a positive acknowledgment. */
export function confirmChatSendFromHistory(
  client: QueryClient,
  memberId: string,
  orderId: string,
  clientMessageId: string,
) {
  if (!currentMember(client, memberId)) return
  const key = pendingChatKey(memberId, orderId)
  const current = client.getQueryData<PendingChatSend | null>(key)
  if (current?.input.clientMessageId === clientMessageId)
    client.setQueryData(key, null)
}
