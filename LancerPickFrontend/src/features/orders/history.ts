import type { OrderDetail, OrderHistory } from './types'

const actions: Record<string, string> = {
  CREATED: '주문 생성', ACCEPTED: '주문 수락', REJECTED: '주문 거절', PAID: '결제 완료',
  DELIVERED: '납품', REVISION_REQUESTED: '수정 요청',
  CANCELLATION_REQUESTED: '취소 요청', CANCELLATION_APPROVED: '취소 요청 승인',
  CANCELLATION_REJECTED: '취소 요청 거절', CANCELLATION_WITHDRAWN: '취소 요청 철회',
  CANCELLED: '주문 취소', COMPLETED: '구매 확정',
}
const statuses: Record<string, string> = {
  REQUESTED: '수락 대기', AWAITING_PAYMENT: '결제 대기', IN_PROGRESS: '진행 중',
  AWAITING_CONFIRMATION: '납품 확인 대기', REVISION_IN_PROGRESS: '수정 진행 중',
  REJECTED: '거절됨', CANCELLED: '취소됨', COMPLETED: '구매 확정',
}

export function describeHistory(history: OrderHistory, order: Pick<OrderDetail, 'buyer' | 'seller'>) {
  const actor = history.actorId === order.buyer.id ? '구매자'
    : history.actorId === order.seller.id ? '판매자' : '기록자'
  const from = history.fromStatus === null ? null : (statuses[history.fromStatus] ?? history.fromStatus)
  const to = statuses[history.toStatus] ?? history.toStatus
  return {
    action: actions[history.action] ?? history.action,
    actor,
    transition: from === null ? `상태: ${to}` : from === to ? `상태 유지: ${to}` : `${from} → ${to}`,
    reason: history.reason,
  }
}
