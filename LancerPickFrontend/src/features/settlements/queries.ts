import { useQuery } from '@tanstack/react-query'
import { getSettlements, getSettlementSummary } from './api'
import type { SettlementPeriod } from './filters'

export const settlementKeys = {
  list: (page: number, period: SettlementPeriod, sort: 'latest' | 'oldest') => ['wallet', 'settlements', page, period.from, period.to, sort] as const,
  summary: (period: SettlementPeriod) => ['wallet', 'settlement-summary', period.from, period.to] as const,
}

// wallet 하위 키로 두어 로그아웃·계정 전환 시 개인 캐시 정리에 참여한다.
export function useSettlements(page: number, period: SettlementPeriod, sort: 'latest' | 'oldest') {
  return useQuery({
    queryKey: settlementKeys.list(page, period, sort),
    queryFn: ({ signal }) => getSettlements(page, 20, signal, period, sort),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}

export function useSettlementSummary(period: SettlementPeriod) {
  return useQuery({
    queryKey: settlementKeys.summary(period),
    queryFn: ({ signal }) => getSettlementSummary(signal, period),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}
