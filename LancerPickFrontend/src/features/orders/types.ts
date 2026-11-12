// API 명세에 맞춘 요청·응답 타입을 구현할 때 정의합니다.
type TradePolicyVersion = 'LEGACY_V1' | 'CONTRACT_V2' | 'CONTRACT_V3'
export interface OrderSummary {
  policyVersion: TradePolicyVersion
  id: string; status: string; productTitle: string; priceAmount: number
  counterparty: { id: string; displayName: string }; createdAt: string; currentDueAt: string | null
}
export interface OrderDetail {
  policyVersion: TradePolicyVersion
  productId: string
  cancellationRequests: CancellationRequest[]
  id: string; status: string; priceAmount: number; requirements: string
  buyer: { id: string; displayName: string }; seller: { id: string; displayName: string }
  productSnapshot: { title: string; description: string; scopeDescription: string; preparationDescription: string; productVersion: number }
  termsSnapshot: string; durationDays: number; originalDueAt: string | null; revisionDueAt: string | null; currentDueAt: string | null; allowedActions: string[]
  deliveries: { id: string; sequenceNo: number; description: string; resultUrl: string; submittedAt: string }[]
  revisionRequest: { id: string; deliveryId: string; reason: string; requestedAt: string; dueAt: string } | null
  histories: OrderHistory[]
  payment: { id: string; amount: number; feeRateBps: number; status: string; paidAt: string; refundedAt: string | null } | null
  review: { id: string; orderId: string; authorDisplayName: string; rating: number; content: string | null; createdAt: string } | null
}
export interface OrderHistory {
  // 시스템 처리(자동 구매 확정)는 실행 회원이 없어 null이다.
  id: string; actorId: string | null; action: string; fromStatus: string | null
  toStatus: string; reason: string | null; createdAt: string
}
interface CancellationRequest {
  id: string; orderId: string; requestKey: string; reason: string
  status: string; requestedAt: string; resolvedAt: string | null
  resolvedBy: string | null; responseReason: string | null
}
export interface CancellationResult { cancellationRequest: CancellationRequest; order: OrderDetail }

export interface CreateOrderInput {
  productId: string
  expectedProductVersion: number
  requirements: string
  requestKey: string
}

/** V3 검수 무응답 자동 구매 확정 타이머. 서버 시각 기준 상태와 판매자 시작 가능 여부를 그대로 표시한다. */
export interface ReviewTimer {
  status:
    | 'NOT_IN_REVIEW'
    | 'CANCELLATION_PENDING'
    | 'WAITING'
    | 'STARTABLE'
    | 'ADMIN_STOPPED'
    | 'ACTIVE'
    | 'AUTO_CONFIRMED'
  basisAt: string | null
  eligibleAt: string | null
  startedAt: string | null
  expiresAt: string | null
  canStart: boolean
}

export interface OrderFunds {
  status: 'NOT_PAID' | 'HELD' | 'RELEASED' | 'REFUNDED'
  amount: number
  heldAmount: number
  refundedAmount: number
  feeAmount: number | null
  sellerPayout: number | null
  paidAt: string | null
  resolvedAt: string | null
}
