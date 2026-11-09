import { skipToken, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getMemberSession } from '../auth/api'
import { getChatMessages, getChatThreads } from './api'
import { pendingChatKey, type PendingChatSend } from './pending'

/** Data-only account cache. Invalidations must never resend a command or discard its UUID. */
export function usePendingChatSend(memberId: string, orderId: string) {
  return (
    useQuery<PendingChatSend | null>({
      queryKey: pendingChatKey(memberId, orderId),
      queryFn: skipToken,
      initialData: null,
      gcTime: Infinity,
      staleTime: Infinity,
    }).data ?? null
  )
}

// 로그인/계정 전환 시 공통 accountCache가 auth 외 조회를 취소·제거한다.
export const chatKeys = {
  threads: ['order-chat', 'threads'] as const,
  member: ['order-chat', 'member'] as const,
  messages: (orderId: string, memberId: string) =>
    ['order-chat', 'messages', orderId, memberId] as const,
}

export function useChatThreads(
  memberId: string,
  q: string,
  page: number,
  enabled: boolean,
) {
  return useQuery({
    queryKey: [...chatKeys.threads, memberId, q, page],
    queryFn: ({ signal }) => getChatThreads(q, page, signal),
    enabled,
    retry: false,
    gcTime: 0,
    staleTime: 0,
    refetchInterval: enabled ? 30_000 : false,
    refetchIntervalInBackground: false,
  })
}

export function useChatMember() {
  return useQuery({
    queryKey: chatKeys.member,
    queryFn: ({ signal }) => getMemberSession(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
    refetchOnMount: 'always',
  })
}

export function useChatMessages(orderId: string, memberId: string) {
  return useInfiniteQuery({
    queryKey: chatKeys.messages(orderId, memberId),
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) =>
      getChatMessages(orderId, { beforeSequence: pageParam }, signal),
    getNextPageParam: (page) =>
      page.hasMore ? (page.nextCursor ?? undefined) : undefined,
    retry: false,
    gcTime: 0,
  })
}
