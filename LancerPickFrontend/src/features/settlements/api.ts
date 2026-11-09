import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { Settlement, SettlementSummary } from './types.ts'
import type { SettlementPeriod } from './filters.ts'

function periodParams(period: SettlementPeriod) {
  const params = new URLSearchParams()
  if (period.from) params.set('from', period.from)
  if (period.to) params.set('to', period.to)
  return params
}

/** 회원 ID는 보내지 않는다. 서버가 현재 로그인한 판매자를 확인한다. */
export function getSettlements(page: number, size = 20, signal?: AbortSignal,
  period: SettlementPeriod = { from: '', to: '' }, sort: 'latest' | 'oldest' = 'latest') {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  for (const [key, value] of periodParams(period)) params.set(key, value)
  if (sort === 'oldest') params.set('sort', sort)
  return apiRequest<PageResponse<Settlement>>(`/me/settlements?${params}`, { signal })
}

export function getSettlementSummary(signal?: AbortSignal, period: SettlementPeriod = { from: '', to: '' }) {
  const params = periodParams(period)
  return apiRequest<SettlementSummary>(`/me/settlements/summary${params.size ? `?${params}` : ''}`, { signal })
}
