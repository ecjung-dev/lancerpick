import type { OrderDetail } from './types'

export type OrderDetailTab = 'progress' | 'conditions' | 'records'

export const orderDetailTabs: { id: OrderDetailTab; label: string }[] = [
  { id: 'progress', label: '거래 진행' },
  { id: 'conditions', label: '계약·조건' },
  { id: 'records', label: '거래 기록' },
]

/**
 * 화면 안내일 뿐 명령 허용 여부를 정하지 않는다. 버튼은 계속 서버 allowedActions를 따른다.
 * isSeller는 현재 회원 조회로 확인된 경우에만 true다(URL의 side로 판단하지 않는다).
 */
export function orderDetailPresentation(order: OrderDetail, isSeller = false) {
  const steps = ['요청', order.policyVersion === 'LEGACY_V1' ? '수락' : '계약', '결제', '작업', '검수', '완료']
  const stages: Record<string, number> = {
    REQUESTED: 0, AWAITING_CONTRACT: 1, AWAITING_PAYMENT: 2,
    IN_PROGRESS: 3, REVISION_IN_PROGRESS: 3, AWAITING_CONFIRMATION: 4,
    AWAITING_BALANCE: 4, COMPLETED: 5,
  }
  const messages: Record<string, [string, string]> = {
    REQUESTED: ['주문 수락을 기다리고 있어요', '판매자가 주문 조건과 요구사항을 확인한 뒤 수락합니다.'],
    AWAITING_CONTRACT: ['계약 내용을 확인해 주세요', '계약·조건 탭에서 계약서 작성과 양측 서명을 진행할 수 있습니다.'],
    AWAITING_PAYMENT: ['결제를 기다리고 있어요', '구매자가 거래 조건을 확인하고 적립금으로 결제하면 작업이 시작됩니다.'],
    IN_PROGRESS: ['작업을 진행하고 있어요', '약속한 작업 범위와 납기를 확인하고, 필요한 내용은 메시지로 이야기해 보세요.'],
    REVISION_IN_PROGRESS: ['요청한 수정을 진행하고 있어요', '수정 요청 내용과 재납품 기한을 확인해 주세요.'],
    AWAITING_CONFIRMATION: ['납품을 확인하고 있어요', '구매자는 결과물을 확인한 뒤 구매 확정 또는 허용된 수정 요청을 진행해 주세요.'],
    AWAITING_BALANCE: ['잔금 결제 대기 중이에요', '보관된 계약 조건과 현재 거래 상태를 확인해 주세요.'],
    COMPLETED: ['거래가 완료됐어요', isSeller
      ? '납품과 거래 기록을 확인할 수 있습니다. 이 주문의 판매 수익은 거래 요약의 판매 수익 링크에서 확인할 수 있습니다.'
      : '납품과 거래 기록을 확인할 수 있습니다. 결과물과 거래 대금 처리 내역은 거래 기록에서 확인해 주세요.'],
    CANCELLED: ['취소된 거래예요', '결제와 반환 여부는 거래 대금 및 거래 기록에서 확인해 주세요.'],
    REJECTED: ['거절된 주문이에요', '거래 기록에서 처리 사유를 확인할 수 있습니다.'],
    TERMINATED: ['종료된 계약이에요', '계약 조건과 처리 결과는 계약·조건 및 거래 기록에서 확인해 주세요.'],
  }
  const pendingCancellation = order.cancellationRequests.some(item => item.status === 'PENDING')
  const [title, description] = pendingCancellation
    ? ['취소 요청이 처리 대기 중이에요', '취소 요청을 확인해 주세요. 처리 대기 중에는 납품·수정·구매 확정을 진행할 수 없습니다.']
    : messages[order.status] ?? ['현재 거래 상태를 확인해 주세요', '사용 가능한 작업은 서버에서 확인한 상태에 따라 표시됩니다.']
  return { title, description, steps, stage: stages[order.status] ?? null, pendingCancellation }
}
