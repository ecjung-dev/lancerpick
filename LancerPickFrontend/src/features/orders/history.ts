import type { OrderDetail, OrderHistory } from './types'
import { orderStatusLabel } from './status.ts'

const actions: Record<string, string> = {
  CREATED: '주문 생성', ACCEPTED: '주문 수락', REJECTED: '주문 거절', PAID: '결제 완료',
  DELIVERED: '납품', REVISION_REQUESTED: '수정 요청',
  CANCELLATION_REQUESTED: '취소 요청', CANCELLATION_APPROVED: '취소 요청 승인',
  CANCELLATION_REJECTED: '취소 요청 거절', CANCELLATION_WITHDRAWN: '취소 요청 철회',
  CANCELLED: '주문 취소', COMPLETED: '구매 확정',
  CONTRACT_CREATED: '계약 초안 작성', CONTRACT_SIGNED: '계약 서명',
  TIMER_STARTED: '자동 구매 확정 타이머 시작', TIMER_CANCELLED: '자동 구매 확정 타이머 종료',
  TIMER_ADMIN_STOPPED: '관리자 타이머 해제',
}
export function describeHistory(history: OrderHistory, order: Pick<OrderDetail, 'buyer' | 'seller'>) {
  const actor = history.actorId === null ? '시스템'
    : history.actorId === order.buyer.id ? '구매자'
    : history.actorId === order.seller.id ? '판매자' : '기록자'
  const from = history.fromStatus === null ? null : orderStatusLabel(history.fromStatus)
  const to = orderStatusLabel(history.toStatus)
  return {
    action: history.action === 'COMPLETED' && history.actorId === null
      ? '자동 구매 확정'
      : history.action === 'DEADLINE_NOTIFIED'
        ? history.toStatus === 'REVISION_IN_PROGRESS' ? '재납품 기한 통지' : '납기 통지'
        : actions[history.action] ?? history.action,
    actor,
    transition: from === null ? `상태: ${to}` : from === to ? `상태 유지: ${to}` : `${from} → ${to}`,
    reason: history.reason,
  }
}
