import { useQuery, type QueryClient, type UseMutationOptions } from '@tanstack/react-query'
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from './api.ts'

// 계정 전환 시 shared/api/accountCache.ts가 auth 외 모든 조회를 지우므로 별도 정리 목록이 필요 없다.
export const notificationKeys = {
  all: ['notifications'] as const,
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
 * 알림 읽음 요청 옵션. 같은 탭 이동은 읽음 성공 직후 mutation 옵션의 onSuccess에서 한다.
 * mutate()에 넘기는 호출별 콜백은 재조회(onSettled)가 끝난 뒤에, 그리고 행이 아직 화면에 있을 때만
 * 실행되므로, 읽지 않음 보기처럼 재조회 결과에서 행이 빠지면 이동이 생략될 수 있다.
 * 변경 요청은 자동 재시도하지 않고, 결과와 무관하게 알림 조회 전체를 다시 불러온다.
 */
export function notificationReadOptions(
  client: QueryClient,
  id: string,
  target: string | null,
  navigate: (to: string) => unknown,
): UseMutationOptions<void, Error, { navigate: boolean }> {
  return {
    retry: false,
    mutationFn: () => markNotificationRead(id),
    onSuccess: (_data, variables) => {
      if (variables.navigate && target) void navigate(target)
    },
    onSettled: () => client.invalidateQueries({ queryKey: notificationKeys.all }),
  }
}
