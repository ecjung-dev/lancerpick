import { useQuery } from '@tanstack/react-query'
import { getCurrentMember } from './api'
import { memberQueryKey } from './session'

/** 현재 로그인 회원. 로그인 안 했으면 data가 null이다. 서버 세션이 기준이므로 매번 최신으로 확인한다. */
export function useCurrentMember() {
  return useQuery({
    queryKey: memberQueryKey,
    queryFn: ({ signal }) => getCurrentMember(signal),
    staleTime: 0,
    retry: false,
  })
}
