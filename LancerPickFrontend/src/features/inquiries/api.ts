import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { Inquiry } from './types.ts'

const ID = /^[1-9][0-9]{0,18}$/

/**
 * 구매자의 관리자 취소 검토 요청. 같은 취소 요청에 이미 열린 문의가 있으면 서버가 그 문의를 200으로 돌려준다(새로 만들면 201).
 * 공통 apiRequest가 CSRF를 붙이며, 실패해도 자동으로 다시 보내지 않는다.
 */
export function requestCancellationReview(orderId: string, requestId: string, subject: string) {
  if (!ID.test(orderId) || !ID.test(requestId)) return Promise.reject(new Error('올바른 주문·요청 번호가 아닙니다.'))
  return apiRequest<Inquiry>(`/orders/${orderId}/cancellation-requests/${requestId}/review-inquiries`, {
    method: 'POST',
    json: { subject },
  })
}

/** 내 문의 목록. 주문 상세에서는 그 주문의 문의만 본다. */
export function getMyInquiries(orderId: string, signal?: AbortSignal) {
  if (!ID.test(orderId)) return Promise.reject(new Error('올바른 주문 번호가 아닙니다.'))
  const params = new URLSearchParams({ orderId, page: '0', size: '20' })
  return apiRequest<PageResponse<Inquiry>>(`/me/inquiries?${params}`, { signal })
}
