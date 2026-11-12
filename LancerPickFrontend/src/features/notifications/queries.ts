import { useQuery, type QueryClient } from '@tanstack/react-query'
import { getNotifications, getUnreadNotificationCount, markAllNotificationsRead, markNotificationRead } from './api.ts'

// 계정 전환 시 shared/api/accountCache.ts가 auth 외 모든 조회를 지우므로 별도 정리 목록이 필요 없다.
const notificationKeys = {
  all: ['notifications'] as const,
  lists: ['notifications', 'list'] as const,
  list: (page: number, unreadOnly: boolean) =>
    ['notifications', 'list', unreadOnly ? 'unread' : 'all', page] as const,
  unread: ['notifications', 'unread'] as const,
}

export function useNotifications(page: number, unreadOnly: boolean) {
  return useQuery({
    queryKey: notificationKeys.list(page, unreadOnly),
    queryFn: ({ signal }) => getNotifications(page, unreadOnly, signal),
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

/** 이동과 독립적으로 읽음 처리한다. 클릭 당시 계정만 전송·후속 재조회를 허용한다. */
export function notificationReadOptions(client: QueryClient, id?: string) {
  return {
    retry: false as const,
    mutationFn: (isCurrentAccount: () => boolean) => id === undefined
      ? markAllNotificationsRead(isCurrentAccount)
      : markNotificationRead(id, isCurrentAccount),
    onSettled: (_data: unknown, _error: Error | null, isCurrentAccount: () => boolean) => {
      if (isCurrentAccount()) return client.invalidateQueries({ queryKey: notificationKeys.all })
    },
  }
}
