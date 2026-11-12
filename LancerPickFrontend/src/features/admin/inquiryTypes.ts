import type { InquiryStatus, InquiryType } from '../inquiries/types.ts'

/** 관리자 운영 문의(K4) 응답. ID는 문자열, 시각은 UTC다. */
export interface Party { id: string; displayName: string | null }

export interface AdminInquirySummary {
  id: string
  type: InquiryType
  status: InquiryStatus
  subject: string
  orderId: string | null
  cancellationRequestId: string | null
  requester: Party
  createdAt: string
  resolvedAt: string | null
}

type AdminInquiryAction = 'APPROVE' | 'REJECT' | 'CLOSE'

export interface AdminInquiryDetail {
  id: string
  type: InquiryType
  status: InquiryStatus
  subject: string
  requester: Party
  assignedAdminId: string | null
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
  order: {
    id: string; status: string; policyVersion: string; priceAmount: number; paidAt: string | null
    buyer: Party; seller: Party
  } | null
  cancellationRequest: {
    id: string; status: string; reason: string; requestedAt: string; resolvedAt: string | null
    resolutionSource: string | null; responseReason: string | null
  } | null
  allowedActions: AdminInquiryAction[]
}

export interface AdminCancellationReviewResult {
  inquiryId: string
  orderId: string
  cancellationRequestId: string
  decision: 'APPROVED' | 'REJECTED'
  orderStatus: string
  inquiryStatus: string
  resolvedAt: string
}

/** 목록 필터. 빈 문자열은 "조건 없음"이다. */
export interface AdminInquiryFilter {
  status: '' | InquiryStatus
  type: '' | InquiryType
}
