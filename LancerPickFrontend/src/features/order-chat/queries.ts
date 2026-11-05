import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getMemberSession } from '../auth/api'
import { getChatMessages } from './api'

// 로그인/계정 전환 시 공통 accountCache가 auth 외 조회를 취소·제거한다.
export const chatKeys = {
  member: ['order-chat', 'member'] as const,
  messages: (orderId: string, memberId: string) =>
    ['order-chat', 'messages', orderId, memberId] as const,
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
