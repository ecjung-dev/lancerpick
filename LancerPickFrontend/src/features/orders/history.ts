import type { OrderDetail, OrderHistory } from './types'
import { orderStatusLabel } from './status.ts'

const actions: Record<string, string> = {
  CREATED: '주문 생성', ACCEPTED: '주문 수락', REJECTED: '주문 거절', PAID: '결제 완료',
  DELIVERED: '납품', REVISION_REQUESTED: '수정 요청',
  CANCELLATION_REQUESTED: '취소 요청', CANCELLATION_APPROVED: '취소 요청 승인',
  CANCELLATION_REJECTED: '취소 요청 거절', CANCELLATION_WITHDRAWN: '취소 요청 철회',
  CANCELLED: '주문 취소', COMPLETED: '구매 확정',
}
export function describeHistory(history: OrderHistory, order: Pick<OrderDetail, 'buyer' | 'seller'>) {
  const actor = history.actorId === order.buyer.id ? '구매자'
    : history.actorId === order.seller.id ? '판매자' : '기록자'
  const from = history.fromStatus === null ? null : orderStatusLabel(history.fromStatus)
  const to = orderStatusLabel(history.toStatus)
  return {
    action: actions[history.action] ?? history.action,
    actor,
    transition: from === null ? `상태: ${to}` : from === to ? `상태 유지: ${to}` : `${from} → ${to}`,
    reason: history.reason,
  }
}
