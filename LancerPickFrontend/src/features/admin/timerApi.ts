import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { Party } from './inquiryTypes.ts'
import { isValidProductId as isValidId } from './validation.ts'

/** 관리자 타이머 해제(K5). ID는 문자열, 시각은 UTC다. */
export interface ActiveReviewTimer {
  timerId: string
  orderId: string
  cycleNo: number
  startedAt: string
  expiresAt: string
  buyer: Party
  seller: Party
}

/** ReviewTimerService가 돌려주는 해제 후 상태(ADMIN_STOPPED 등). */
export interface ReviewTimerStatus { status: string }

export function getActiveReviewTimers(page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  return apiRequest<PageResponse<ActiveReviewTimer>>(`/admin/review-timers?${params}`, { signal })
}

/**
 * 해제. 공통 apiRequest가 CSRF를 붙이고, 실패해도 자동으로 다시 보내지 않는다.
 * isCurrentAccount: 클릭 때 captureAccountScope로 잡은 계정 경계. 그 사이 계정이 바뀌면 보내지 않는다.
 */
export function stopReviewTimer(orderId: string, reason: string, isCurrentAccount?: () => boolean) {
  if (!isValidId(orderId)) return Promise.reject(new Error('올바른 주문 번호가 아니에요.'))
  return apiRequest<ReviewTimerStatus>(`/admin/orders/${orderId}/review-timer/stop`, { method: 'POST', json: { reason }, isCurrentAccount })
}

/** 타이머는 시간이 지나면 자동 확정되거나 다른 관리자가 해제할 수 있어 매번 최신으로 조회하고 보관하지 않는다. */
export const adminTimerKeys = { active: (page: number) => ['admin', 'review-timers', page] as const }

export function useActiveReviewTimers(page: number) {
  return useQuery({ queryKey: adminTimerKeys.active(page), queryFn: ({ signal }) => getActiveReviewTimers(page, signal), retry: false, staleTime: 0, gcTime: 0 })
}
