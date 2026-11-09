import { prepareCancellationReason, prepareOrderInput } from './validation.ts'
import type { CreateOrderInput } from './types.ts'
import type { OrderListFilters } from './listFilters.ts'
// 이 기능의 서버 요청 함수를 구현합니다. 공통 요청 처리는 shared/api/client.ts에 둡니다.
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types'
import type {
  OrderDetail,
  OrderSummary,
  CancellationResult,
  OrderFunds,
  ReviewTimer,
} from './types'

function orderPath(id: string) {
  if (
    !/^[0-9]+$/.test(id) ||
    BigInt(id) < 1n ||
    BigInt(id) > 9223372036854775807n
  )
    throw new Error('주문 ID를 확인해 주세요.')
  return `/orders/${id}`
}
export const getOrders = (filters: OrderListFilters, signal?: AbortSignal) => {
  const params = new URLSearchParams({ side: filters.side })
  if (filters.status) params.set('status', filters.status)
  if (filters.q) params.set('q', filters.q)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.sort === 'oldest') params.set('sort', 'oldest')
  params.set('page', String(filters.page))
  params.set('size', '20')
  return apiRequest<PageResponse<OrderSummary>>(`/orders?${params}`, { signal })
}
export const getOrder = (id: string, signal?: AbortSignal) =>
  apiRequest<OrderDetail>(orderPath(id), { signal })
export const payOrder = (id: string) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/payment`, {
    method: 'POST',
    json: { agreedToTerms: true },
  })
export const cancelOrder = (id: string, reason: string) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/cancel`, {
    method: 'POST',
    json: { reason: prepareCancellationReason(reason) },
  })
export const confirmOrder = (id: string) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/confirm`, { method: 'POST' })
export const acceptOrder = (id: string) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/accept`, { method: 'POST' })
export const rejectOrder = (id: string, reason: string) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/reject`, {
    method: 'POST',
    json: { reason },
  })
export const deliverOrder = (
  id: string,
  description: string,
  resultUrl: string,
  expectedSequenceNo: 1 | 2,
) =>
  apiRequest<OrderDetail>(`${orderPath(id)}/deliveries`, {
    method: 'POST',
    json: { description, resultUrl, expectedSequenceNo },
  })
export const requestOrderRevision = (
  id: string,
  deliveryId: string,
  reason: string,
) => {
  orderPath(deliveryId)
  return apiRequest<OrderDetail>(`${orderPath(id)}/revision-request`, {
    method: 'POST',
    json: { deliveryId, reason },
  })
}
export const createCancellationRequest = (
  id: string,
  reason: string,
  requestKey: string,
) =>
  apiRequest<CancellationResult>(`${orderPath(id)}/cancellation-requests`, {
    method: 'POST',
    json: { reason, requestKey },
  })
export const resolveCancellationRequest = (
  id: string,
  requestId: string,
  action: 'approve' | 'reject' | 'withdraw',
  responseReason?: string,
) => {
  orderPath(requestId)
  return apiRequest<CancellationResult>(
    `${orderPath(id)}/cancellation-requests/${requestId}/${action}`,
    {
      method: 'POST',
      ...(action === 'reject' && responseReason
        ? { json: { responseReason } }
        : {}),
    },
  )
}

export function createOrder(input: CreateOrderInput) {
  return apiRequest<OrderDetail>('/orders', {
    method: 'POST',
    json: prepareOrderInput(input),
  })
}

export const getOrderFunds = (id: string, signal?: AbortSignal) =>
  apiRequest<OrderFunds>(`${orderPath(id)}/funds`, {
    signal,
    cache: 'no-store',
  })

export const getReviewTimer = (id: string, signal?: AbortSignal) =>
  apiRequest<ReviewTimer>(`${orderPath(id)}/review-timer`, {
    signal,
    cache: 'no-store',
  })

/** 판매자의 자동 구매 확정 타이머 시작. 본문 없이 서버가 시각·기산점을 판단한다. */
export const startReviewTimer = (id: string) =>
  apiRequest<ReviewTimer>(`${orderPath(id)}/review-timer`, { method: 'POST' })
