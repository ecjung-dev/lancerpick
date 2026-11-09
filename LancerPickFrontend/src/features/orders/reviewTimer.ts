import { formatDateTime } from '../../shared/lib/format.ts'
import type { ReviewTimer } from './types.ts'

/**
 * 자동 구매 확정 타이머 안내 문구. 시각은 서버가 내려준 값만 표시하고 남은 시간을 화면에서 계산해 상태를 바꾸지 않는다.
 * 검수 대기가 아니면 null을 돌려 패널을 숨긴다.
 */
export function reviewTimerView(timer: ReviewTimer, isSeller: boolean) {
  const at = (value: string | null) => (value ? formatDateTime(value) : '')
  switch (timer.status) {
    case 'NOT_IN_REVIEW':
      return null
    case 'CANCELLATION_PENDING':
      return {
        title: '취소 요청 처리 대기',
        detail: '취소 요청이 처리될 때까지 자동 구매 확정 타이머를 시작하거나 진행하지 않습니다.',
      }
    case 'WAITING':
      return {
        title: '검수 응답 대기',
        detail: isSeller
          ? `${at(timer.eligibleAt)} 이후에도 구매자 응답이 없으면 7일 자동 구매 확정 타이머를 시작할 수 있습니다.`
          : `${at(timer.eligibleAt)}까지 검수 결과(구매 확정·수정 요청)를 알려 주세요. 이후 판매자가 자동 구매 확정 타이머를 시작할 수 있습니다.`,
      }
    case 'STARTABLE':
      // 판매자인데 canStart가 false면 서버가 시작을 막은 상태다(구매자 계정 보호 조치 중).
      if (isSeller && !timer.canStart)
        return {
          title: '자동 구매 확정 타이머 시작 보류',
          detail: '구매자 계정 확인이 끝날 때까지 지금은 타이머를 시작할 수 없습니다.',
        }
      return {
        title: '자동 구매 확정 타이머 시작 가능',
        detail: isSeller
          ? '시작하면 7일 뒤 구매자 응답이 없을 때 자동으로 구매가 확정됩니다.'
          : '판매자가 7일 자동 구매 확정 타이머를 시작할 수 있습니다. 검수 결과를 알려 주세요.',
      }
    case 'ADMIN_STOPPED':
      return {
        title: '관리자가 타이머를 해제함',
        detail: '새 납품이나 취소 요청 처리 이후에 다시 시작할 수 있습니다.',
      }
    case 'ACTIVE':
      return {
        title: '자동 구매 확정 타이머 진행 중',
        detail: `${at(timer.expiresAt)}까지 구매자 응답(구매 확정·수정 요청·취소 요청)이 없으면 자동으로 구매가 확정됩니다.`,
      }
    case 'AUTO_CONFIRMED':
      return {
        title: '자동 구매 확정 완료',
        detail: `검수 응답 기한(${at(timer.expiresAt)})이 지나 구매가 자동으로 확정되었습니다.`,
      }
  }
}

/**
 * 검수 대기 중에는 시간 경과(48시간·자동 확정)나 상대방 행동(판매자의 타이머 시작, 취소 요청 처리, 재납품)으로 상태가 바뀔 수
 * 있으므로 1분마다 다시 확인한다. 검수 대기가 아니거나 자동 확정이 끝났으면, 또는 조회 실패 후에는 멈춘다.
 */
export const REVIEW_TIMER_POLL_MS = 60_000

export function reviewTimerRefetchInterval(timer: ReviewTimer | undefined, failed: boolean) {
  if (failed || !timer) return false
  return timer.status === 'NOT_IN_REVIEW' || timer.status === 'AUTO_CONFIRMED'
    ? false
    : REVIEW_TIMER_POLL_MS
}
