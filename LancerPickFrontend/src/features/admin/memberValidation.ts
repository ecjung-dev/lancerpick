import { normalizeText } from '../../shared/lib/text.ts'
import type { AdminMemberFilter, MemberRestriction } from './memberTypes.ts'

export const restrictionLabels: Record<MemberRestriction, string> = {
  NONE: '제한 없음',
  GENERAL_RESTRICTION: '일반 이용 제한',
  SECURITY_LOCK: '보안 잠금',
}
export const sanctionStateLabels = {
  ACTIVE: '적용 중',
  EXPIRED: '기간 만료',
  REVOKED: '관리자 해제',
  SCHEDULED: '적용 예정',
}

export function readMemberFilter(params: URLSearchParams): AdminMemberFilter {
  const restriction = params.get('restriction')
  return {
    q: normalizeText(params.get('q') ?? ''),
    restriction:
      restriction === 'NONE' ||
      restriction === 'GENERAL_RESTRICTION' ||
      restriction === 'SECURITY_LOCK'
        ? restriction
        : '',
  }
}

/** datetime-local is explicitly interpreted as Korea time, independent of the device timezone. */
export function koreaEndTime(value: string, serverTime: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))
    return { error: '종료 일시를 입력해 주세요.' }
  const time = new Date(`${value}:00+09:00`)
  // Reject normalized impossible dates (e.g. February 30) instead of silently changing the day.
  if (
    !Number.isFinite(time.getTime()) ||
    new Date(time.getTime() + 9 * 3_600_000).toISOString().slice(0, 16) !==
      value
  ) {
    return { error: '올바른 종료 일시를 입력해 주세요.' }
  }
  if (time.getTime() <= Date.parse(serverTime))
    return { error: '종료 일시는 현재 서버 시각 이후로 입력해 주세요.' }
  return { endsAt: time.toISOString() }
}
