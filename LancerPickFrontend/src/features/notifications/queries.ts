import { useQuery, type QueryClient } from '@tanstack/react-query'
import type { PageResponse } from '../../shared/api/types'
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from './api.ts'
import type { NotificationItem } from './types.ts'

// 계정 전환 시 shared/api/accountCache.ts가 auth 외 모든 조회를 지우므로 별도 정리 목록이 필요 없다.
export const notificationKeys = {
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

/**
 * 알림 하나의 읽음 요청 옵션. 성공하면 목록을 다시 불러오지 않고 화면의 그 항목만 읽음으로 바꾸며,
 * 읽지 않은 수만 다시 조회한다. 읽지 않음 보기에서도 클릭한 행이 응답 직후 사라지지 않아,
 * mutate()에 넘긴 이동 콜백(행이 화면에 있을 때만 실행)이 그대로 동작한다. 목록은 필터·페이지 이동이나
 * 다시 들어올 때 새로 조회된다. 실패하면 결과가 불명확하므로 알림 조회 전체를 다시 불러온다.
 * 변경 요청은 자동 재시도하지 않는다.
 */
export function notificationReadOptions(client: QueryClient, id: string) {
  return {
    retry: false as const,
    mutationFn: () => markNotificationRead(id),
    onSuccess: () => {
      client.setQueriesData<PageResponse<NotificationItem>>({ queryKey: notificationKeys.lists }, (page) =>
        page && { ...page, items: page.items.map((item) => (item.id === id ? { ...item, read: true } : item)) },
      )
    },
    onSettled: (_data: unknown, error: Error | null) =>
      client.invalidateQueries({ queryKey: error ? notificationKeys.all : notificationKeys.unread }),
  }
}
