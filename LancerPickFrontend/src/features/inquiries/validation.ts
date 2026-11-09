import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { InquiryStatus } from './types.ts'

export const SUBJECT_MAX = 150

/** 문의 제목은 공백 정리 후 1~150자. 서버(SupportInquiry.subject)도 같은 규칙으로 다시 검사한다. */
export function validateSubject(value: string) {
  const subject = normalizeText(value)
  const length = codePointLength(subject)
  if (length < 1) return { subject, error: '검토를 요청하는 이유를 짧게 적어 주세요.' }
  if (length > SUBJECT_MAX) return { subject, error: `최대 ${SUBJECT_MAX}자까지 쓸 수 있어요.` }
  return { subject, error: undefined }
}

export function inquiryStatusLabel(status: InquiryStatus) {
  return status === 'CLOSED' ? '처리 완료' : status === 'IN_REVIEW' ? '검토 중' : '검토 대기'
}

/**
 * 주문 상세에서 관리자 검토 요청 칸을 보여 줄지. 서버가 다시 검사하므로 화면 표시용이다.
 * 구매자의 대기 중 취소 요청(철회 버튼이 허용됨)이고, 결제 후 진행 중인 V3 주문일 때만 보인다.
 */
export function canAskForReview(order: { policyVersion: string; status: string; allowedActions: string[] }) {
  return order.policyVersion === 'CONTRACT_V3'
    && ['IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS'].includes(order.status)
    && order.allowedActions.includes('WITHDRAW_CANCELLATION')
}
