export const orderStatuses = ['REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED'] as const

export const orderStatusNames = ['수락 대기', '결제 대기', '진행 중', '납품 확인 대기', '수정 진행 중', '구매 확정', '거절됨', '취소됨'] as const

export function orderStatusLabel(status: string) {
  const index = orderStatuses.findIndex(value => value === status)
  return index < 0 ? status : orderStatusNames[index]
}

const cancellationStatusNames: Record<string, string> = {
  PENDING: '처리 대기', APPROVED: '승인됨', REJECTED: '거절됨', WITHDRAWN: '철회됨',
}

export function cancellationStatusLabel(status: string) {
  return cancellationStatusNames[status] ?? status
}
