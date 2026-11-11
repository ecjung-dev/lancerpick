/**
 * 관리자 화면 상태 배지의 색(톤). 같은 칸의 상태 값은 모두 같은 배지 모양으로 보여 주고 색만 뜻에 따라 바꾼다.
 * - ok: 처리가 성공적으로 끝남(결제 완료·충전 완료·지급 완료·서명 완료 등)
 * - neutral: 대기·진행·되돌림·기록상 종료(환불·보관 중·만료·철회·판매 중지 등). 표에 없는 코드도 여기
 * - warn: 관리자 확인·조치가 필요하거나 제한이 걸려 있음(결과 확인 필요·검토 대기·제재 적용 중 등)
 * - danger: 실패·중단·차단(충전 실패·작업 실패·처리 중단·보안 잠금 등)
 * - strong: 역할·노출 통제 강조(관리자 역할·숨김)만
 * 색만으로 구분하지 않도록 배지 문구는 항상 함께 보인다.
 */
export type StatusTone = 'ok' | 'neutral' | 'warn' | 'danger' | 'strong'

type ToneMap = Readonly<Record<string, StatusTone>>

export function toneOf(map: ToneMap, code: string): StatusTone {
  return map[code] ?? 'neutral'
}

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
