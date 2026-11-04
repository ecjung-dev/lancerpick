import { useQuery } from '@tanstack/react-query'
import { getSettlements, getSettlementSummary } from './api'

// wallet 하위 키로 두어 로그아웃·계정 전환 시 개인 캐시 정리에 참여한다.
export function useSettlements(page: number) {
  return useQuery({
    queryKey: ['wallet', 'settlements', page],
    queryFn: ({ signal }) => getSettlements(page, 20, signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}

export function useSettlementSummary() {
  return useQuery({
    queryKey: ['wallet', 'settlement-summary'],
    queryFn: ({ signal }) => getSettlementSummary(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}
