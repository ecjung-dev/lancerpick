import type { StatusTone, ToneMap } from '../../shared/lib/statusTone'

/** 관리자 화면 상태 코드 → 톤 표. 톤 기준은 shared/lib/statusTone.ts 주석. */
export { toneOf } from '../../shared/lib/statusTone'
export type { StatusTone }

export const paymentTones: ToneMap = { PAID: 'ok', REFUNDED: 'neutral' }

export const fundsTones: ToneMap = { NOT_PAID: 'neutral', HELD: 'neutral', RELEASED: 'ok', REFUNDED: 'neutral' }

/** 종료 사유는 이미 끝난 거래라 잔금 미납도 미해결 경고가 아니다. 구매 확정만 성공 종료로 본다. */
export const closureTones: ToneMap = { COMPLETED: 'ok' }

export const contractTones: ToneMap = { SIGNED: 'ok', FAILED: 'danger' }

export const topupTones: ToneMap = { COMPLETED: 'ok', PROCESSING: 'warn', UNKNOWN: 'warn', FAILED: 'danger' }

export const jobTones: ToneMap = { COMPLETED: 'ok', UNKNOWN: 'warn', FAILED: 'danger', STOPPED: 'danger', ABANDONED: 'danger' }

/**
 * 관리자 재처리 요청(K6) 상태. "처리됨"(SUCCEEDED)은 복구 작업 재시작처럼 접수만 뜻할 수 있어 완료(ok)로 칠하지 않는다.
 * 반영 안 됨(REJECTED)은 조건이 바뀌어 처리하지 않은 것이라 실패와 구분한다.
 */
export const actionTones: ToneMap = { UNKNOWN: 'warn', FAILED: 'danger' }

export const inquiryTones: ToneMap = { OPEN: 'warn', IN_REVIEW: 'neutral', CLOSED: 'ok' }

export const cancellationTones: ToneMap = { PENDING: 'warn' }

export const sanctionStateTones: ToneMap = { ACTIVE: 'warn' }

export const restrictionTones: ToneMap = { NONE: 'neutral', GENERAL_RESTRICTION: 'warn', SECURITY_LOCK: 'danger' }

export const outboxTones: ToneMap = { RETRYING: 'warn', FAILED: 'danger' }

export const saleStatusTones: ToneMap = { ON_SALE: 'ok', STOPPED: 'neutral' }

export const visibilityTone = (hidden: boolean): StatusTone => hidden ? 'strong' : 'neutral'
