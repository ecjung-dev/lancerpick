import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { Settlement, SettlementSummary } from './types.ts'

/** 회원 ID는 보내지 않는다. 서버가 현재 로그인한 판매자를 확인한다. */
export function getSettlements(page: number, size = 20, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<PageResponse<Settlement>>(`/me/settlements?${params}`, { signal })
}

export function getSettlementSummary(signal?: AbortSignal) {
  return apiRequest<SettlementSummary>('/me/settlements/summary', { signal })
}
