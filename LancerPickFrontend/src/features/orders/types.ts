// API 명세에 맞춘 요청·응답 타입을 구현할 때 정의합니다.
export interface OrderSummary {
  id: string; status: string; productTitle: string; priceAmount: number
  counterparty: { id: string; displayName: string }; createdAt: string; currentDueAt: string | null
}
export interface OrderDetail {
  cancellationRequests: CancellationRequest[]
  id: string; status: string; priceAmount: number; requirements: string
  buyer: { id: string; displayName: string }; seller: { id: string; displayName: string }
  productSnapshot: { title: string; description: string; scopeDescription: string; preparationDescription: string; productVersion: number }
  termsSnapshot: string; durationDays: number; currentDueAt: string | null; allowedActions: string[]
  deliveries: { id: string; sequenceNo: number; description: string; resultUrl: string; submittedAt: string }[]
  revisionRequest: { id: string; deliveryId: string; reason: string; requestedAt: string; dueAt: string } | null
}
export interface CancellationRequest {
  id: string; orderId: string; requestKey: string; reason: string
  status: string; requestedAt: string; resolvedAt: string | null
  resolvedBy: string | null; responseReason: string | null
}
export interface CancellationResult { cancellationRequest: CancellationRequest; order: OrderDetail }
