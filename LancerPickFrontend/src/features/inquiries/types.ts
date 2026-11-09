/** 운영 문의(K4). ID는 큰 숫자 정밀도 손실을 막기 위해 문자열, 시각은 UTC ISO 8601이다. */
export type InquiryType = 'CANCELLATION_REVIEW' | 'GENERAL'
export type InquiryStatus = 'OPEN' | 'IN_REVIEW' | 'CLOSED'

export interface Inquiry {
  id: string
  type: InquiryType
  status: InquiryStatus
  subject: string
  orderId: string | null
  cancellationRequestId: string | null
  createdAt: string
  resolvedAt: string | null
}
