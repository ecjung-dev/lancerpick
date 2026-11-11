import { orderFundsStatusLabels } from '../orders/status'

/**
 * 관리자 금융 화면의 상태 코드 한글 라벨. 같은 COMPLETED라도 충전·복구 작업·거래 종료의 뜻이 달라
 * 표를 하나로 합치지 않고 문맥마다 둔다. 운영자가 서버 기록·로그와 대조할 수 있게 코드는 괄호로 남기고,
 * 표에 없는 값(새로 생긴 상태 등)은 원문 코드를 그대로 보여 준다.
 */
type Labels = Readonly<Record<string, string>>

export function codeLabel(labels: Labels, code: string) {
  const label = labels[code]
  return label ? `${label} (${code})` : code
}

/** 라벨만(코드 없이). select 옵션처럼 value에 코드가 이미 있는 곳에 쓴다. */
export function plainLabel(labels: Labels, code: string) {
  return labels[code] ?? code
}

export const policyLabels: Labels = {
  LEGACY_V1: '기존 전액 결제',
  CONTRACT_V2: '분할 결제(이전 정책)',
  CONTRACT_V3: '전액 보관 계약',
}

export const paymentStatusLabels: Labels = { PAID: '결제 완료', REFUNDED: '환불 완료' }

/** 주문 화면 대금 패널과 같은 문구. 관리자 조회는 저장된 HELD·RELEASED·REFUNDED만 받는다. */
export const fundsStatusLabels: Labels = orderFundsStatusLabels

export const settlementTypeLabels: Labels = {
  NORMAL: '일반 정산',
  NO_RESPONSE_COMPENSATION: '무응답 보상 정산',
}

export const closureReasonLabels: Labels = {
  COMPLETED: '구매 확정',
  MUTUAL_CANCELLATION: '판매자 취소 승인',
  ADMIN_CANCELLATION: '관리자 취소 승인',
  REVIEW_NO_RESPONSE: '검토 응답 기한 만료',
  BALANCE_UNPAID: '잔금 미납',
}

/** 주문 화면 계약 패널(ContractPanel)과 같은 문구. */
export const contractStatusLabels: Labels = {
  DRAFT: 'PDF 생성 대기',
  GENERATING: 'PDF 생성 중',
  FAILED: 'PDF 생성 실패',
  READY: '서명 대기',
  SIGNED: '양측 서명 완료',
  SUPERSEDED: '교체된 계약',
}

/** 충전 결과 화면(WalletTopupResultPage)과 같은 문구. */
export const topupStatusLabels: Labels = {
  READY: '승인 전',
  PROCESSING: '처리 중',
  UNKNOWN: '결과 확인 필요',
  COMPLETED: '충전 완료',
  FAILED: '충전 미완료',
}

/** 충전 복구 Job 최신 실행 상태(Spring Batch BatchStatus)와 실행 기록이 없을 때의 NO_EXECUTION. */
export const jobStatusLabels: Labels = {
  STARTING: '시작 중',
  STARTED: '실행 중',
  STOPPING: '중지 중',
  STOPPED: '중지됨',
  COMPLETED: '완료',
  FAILED: '실패',
  ABANDONED: '포기됨',
  UNKNOWN: '상태 알 수 없음',
  NO_EXECUTION: '실행 기록 없음',
}
