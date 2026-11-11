import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { AdminCancellationReviewResult, AdminInquiryDetail, AdminInquiryFilter, AdminInquirySummary } from './inquiryTypes.ts'
import { isValidProductId as isValidId } from './validation.ts'

const invalid = () => Promise.reject(new Error('올바른 문의 번호가 아니에요.'))

/** 관리자 권한은 서버가 세션과 DB 역할로 판단한다. 화면은 관리자 여부를 요청값으로 보내지 않는다. */
export function getAdminInquiries(filter: AdminInquiryFilter, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (filter.status) params.set('status', filter.status)
  if (filter.type) params.set('type', filter.type)
  return apiRequest<PageResponse<AdminInquirySummary>>(`/admin/inquiries?${params}`, { signal })
}

export function getAdminInquiry(id: string, signal?: AbortSignal) {
  if (!isValidId(id)) return invalid()
  return apiRequest<AdminInquiryDetail>(`/admin/inquiries/${id}`, { signal })
}

/**
 * 취소 검토 승인(전액 반환)·거절. 공통 apiRequest가 CSRF를 붙이고, 실패해도 자동으로 다시 보내지 않는다.
 * 같은 결정을 다시 보내면 서버가 같은 결과를 돌려준다(200).
 * isCurrentAccount: 클릭 때 captureAccountScope로 잡은 계정 경계. 그 사이 계정이 바뀌면 보내지 않는다.
 */
export function decideInquiry(id: string, decision: 'approve' | 'reject', reason: string, isCurrentAccount?: () => boolean) {
  if (!isValidId(id)) return invalid()
  return apiRequest<AdminCancellationReviewResult>(`/admin/inquiries/${id}/${decision}`, {
    method: 'POST',
    json: { reason },
    isCurrentAccount,
  })
}

/** 승인·거절할 수 없게 된 문의 종료(판매자가 먼저 처리했거나 구매자가 철회). 본문 없음. */
export function closeInquiry(id: string, isCurrentAccount?: () => boolean) {
  if (!isValidId(id)) return invalid()
  return apiRequest<AdminInquiryDetail>(`/admin/inquiries/${id}/close`, { method: 'POST', isCurrentAccount })
}
