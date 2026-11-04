import { prepareOrderInput } from './validation.ts'
import type { CreateOrderInput } from './types.ts'
// 이 기능의 서버 요청 함수를 구현합니다. 공통 요청 처리는 shared/api/client.ts에 둡니다.
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types'
import type { OrderDetail, OrderSummary, CancellationResult } from './types'

function orderPath(id: string) {
  if (
    !/^[0-9]+$/.test(id) ||
    BigInt(id) < 1n ||
    BigInt(id) > 9223372036854775807n
  )
    throw new Error('주문 ID를 확인해 주세요.')
  return `/orders/${id}`
}
export const getOrders = (
  side: 'BUYER' | 'SELLER',
  page: number,
  signal?: AbortSignal,
  status?: string,
) =>
  apiRequest<PageResponse<OrderSummary>>(
    `/orders?side=${side}${status ? `&status=${encodeURIComponent(status)}` : ''}&page=${page}&size=20`,
    { signal },
  )
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
    json: { reason },
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
  return apiRequest<OrderDetail>('/orders', { method: 'POST', json: prepareOrderInput(input) })
}
