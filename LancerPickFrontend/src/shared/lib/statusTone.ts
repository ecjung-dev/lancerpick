/**
 * 상태 배지의 색(톤). 같은 칸의 상태 값은 모두 같은 배지 모양으로 보여 주고 색만 뜻에 따라 바꾼다.
 * - ok: 처리가 성공적으로 끝남(결제 완료·구매 확정·지급 완료·서명 완료 등)
 * - neutral: 대기·진행·되돌림·기록상 종료(환불·보관 중·취소·거절·만료·철회 등). 표에 없는 코드도 여기
 * - warn: 확인·조치가 필요하거나 제한이 걸려 있음(결과 확인 필요·검토 대기·제재 적용 중 등)
 * - danger: 실패·중단·차단(충전 실패·작업 실패·처리 중단·보안 잠금 등)
 * - strong: 역할·노출 통제 강조(관리자 역할·숨김)만
 * 업무별 코드 → 톤 표는 각 기능에 두고, 모양은 shared/components/StatusBadge.
 */
export type StatusTone = 'ok' | 'neutral' | 'warn' | 'danger' | 'strong'

export type ToneMap = Readonly<Record<string, StatusTone>>

export function toneOf(map: ToneMap, code: string): StatusTone {
  return map[code] ?? 'neutral'
}
