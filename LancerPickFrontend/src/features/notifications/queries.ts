import { useQuery } from '@tanstack/react-query'
import { getNotifications, getUnreadNotificationCount } from './api.ts'

// 계정 전환 시 shared/api/accountCache.ts가 auth 외 모든 조회를 지우므로 별도 정리 목록이 필요 없다.
export const notificationKeys = {
  all: ['notifications'] as const,
  list: (page: number) => ['notifications', 'list', page] as const,
  unread: ['notifications', 'unread'] as const,
}

export function useNotifications(page: number) {
  return useQuery({
    queryKey: notificationKeys.list(page),
    queryFn: ({ signal }) => getNotifications(page, signal),
    retry: false,
    gcTime: 0,
  })
}

/** 로그인한 회원 메뉴에서만 사용한다. 실시간 전송 전까지 30초마다 다시 확인한다. */
export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: ({ signal }) => getUnreadNotificationCount(signal),
    retry: false,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  })
}
