import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import type { AdminProductDetail, AdminProductFilter, AdminProductSummary, ModerationLog, VisibilityInput } from './types.ts'
import { isValidProductId } from './validation.ts'

/** 관리자 권한은 서버가 세션으로 판단한다. 화면은 관리자 여부를 요청값으로 보내지 않는다. */
export function getAdminProducts(filter: AdminProductFilter, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (filter.keyword) params.set('keyword', filter.keyword)
  if (filter.isHidden) params.set('isHidden', filter.isHidden)
  if (filter.saleStatus) params.set('saleStatus', filter.saleStatus)
  return apiRequest<PageResponse<AdminProductSummary>>(`/admin/products?${params}`, { signal })
}

export function getAdminProduct(id: string, signal?: AbortSignal) {
  if (!isValidProductId(id)) return Promise.reject(new Error('올바른 상품 번호가 아니에요.'))
  return apiRequest<AdminProductDetail>(`/admin/products/${id}`, { signal })
}

export function getModerationLogs(id: string, page: number, signal?: AbortSignal) {
  if (!isValidProductId(id)) return Promise.reject(new Error('올바른 상품 번호가 아니에요.'))
  const params = new URLSearchParams({ page: String(page), size: '20' })
  return apiRequest<PageResponse<ModerationLog>>(`/admin/products/${id}/moderation-logs?${params}`, { signal })
}

/**
 * 숨김·복구. 공통 apiRequest가 CSRF를 붙이며 실패해도 자동으로 다시 보내지 않는다.
 * expectedVersion은 화면에서 마지막으로 본 상품 버전이다(바뀌었으면 서버가 409).
 * isCurrentAccount: 클릭 때 captureAccountScope로 잡은 계정 경계. 그 사이 계정이 바뀌면 보내지 않는다.
 */
export function changeVisibility(id: string, input: VisibilityInput, isCurrentAccount?: () => boolean) {
  if (!isValidProductId(id)) return Promise.reject(new Error('올바른 상품 번호가 아니에요.'))
  return apiRequest<AdminProductDetail>(`/admin/products/${id}/visibility`, {
    method: 'PATCH',
    isCurrentAccount,
    json: { hidden: input.hidden, reason: input.reason, expectedVersion: input.expectedVersion },
  })
}
