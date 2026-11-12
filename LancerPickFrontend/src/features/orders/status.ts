import type { ToneMap } from '../../shared/lib/statusTone'
import type { OrderFunds } from './types'

export const orderStatuses = ['REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED'] as const

export const orderStatusNames = ['수락 대기', '결제 대기', '진행 중', '납품 확인 대기', '수정 진행 중', '구매 확정', '거절됨', '취소됨'] as const

export function orderStatusLabel(status: string) {
  if (status === 'AWAITING_CONTRACT') return '계약 서명 대기'
  if (status === 'AWAITING_BALANCE') return '잔금 결제 대기'
  if (status === 'TERMINATED') return '계약 종료'
  const index = orderStatuses.findIndex(value => value === status)
  return index < 0 ? status : orderStatusNames[index]
}

/** 주문 상태 배지 톤. 구매 확정만 성공 종료(ok)이고 거절·취소·계약 종료는 기록상 종료라 neutral. */
export const orderStatusTones: ToneMap = { COMPLETED: 'ok' }

const cancellationStatusNames: Record<string, string> = {
  PENDING: '처리 대기', APPROVED: '승인됨', REJECTED: '거절됨', WITHDRAWN: '철회됨',
}

export function cancellationStatusLabel(status: string) {
  return cancellationStatusNames[status] ?? status
}

/** 주문 대금(order_funds) 상태 문구. 주문 화면 대금 패널과 관리자 금융 상세가 함께 쓴다. */
export const orderFundsStatusLabels: Record<OrderFunds['status'], string> = {
  NOT_PAID: '전액 결제 대기',
  HELD: '주문 대금 보관 중',
  RELEASED: '판매자 지급 완료',
  REFUNDED: '구매자 전액 반환 완료',
}
