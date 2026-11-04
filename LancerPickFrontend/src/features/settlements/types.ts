/** ID는 문자열, 금액은 정수 원 단위, 시각은 UTC ISO 8601 응답을 사용한다. */
export interface Settlement {
  id: string
  orderId: string
  grossAmount: number
  feeRateBps: number
  feeAmount: number
  sellerAmount: number
  createdAt: string
}

export interface SettlementSummary {
  completedCount: number
  grossAmount: number
  feeAmount: number
  sellerAmount: number
}
