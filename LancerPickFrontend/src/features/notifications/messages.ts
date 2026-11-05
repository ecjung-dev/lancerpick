import type { NotificationItem } from './types.ts'

const messages: Record<string, string> = {
  ORDER_CREATED: '새 주문 요청이 도착했습니다.',
  ORDER_ACCEPTED: '판매자가 주문을 수락했습니다. 주문 상세에서 다음 절차를 확인해 주세요.',
  ORDER_CONTRACT_CREATED: '계약 초안이 작성되었습니다. 계약 내용을 확인해 주세요.',
  ORDER_CONTRACT_SIGNED: '상대방이 계약에 서명했습니다. 계약 상태를 확인해 주세요.',
  ORDER_REJECTED: '판매자가 주문을 거절했습니다.',
  ORDER_PAID: '구매자가 결제를 완료했습니다. 작업을 시작해 주세요.',
  ORDER_DELIVERED: '판매자가 결과물을 납품했습니다. 확인해 주세요.',
  ORDER_REVISION_REQUESTED: '구매자가 수정을 요청했습니다.',
  ORDER_CANCELLATION_REQUESTED: '구매자가 취소를 요청했습니다.',
  ORDER_CANCELLATION_REJECTED: '판매자가 취소 요청을 거절했습니다.',
  ORDER_CANCELLATION_WITHDRAWN: '구매자가 취소 요청을 철회했습니다.',
  ORDER_CANCELLED: '주문이 취소되었습니다.',
  ORDER_COMPLETED: '구매자가 구매를 확정했습니다.',
}

/** 서버가 새 유형을 먼저 배포해도 화면이 깨지지 않도록 일반 문구로 대신한다. */
export function notificationMessage(type: string) {
  return messages[type] ?? '거래 상태가 변경되었습니다.'
}

/** 주문 알림만 주문 상세로 이동한다. ID 형식이 올바르지 않으면 링크를 만들지 않는다. */
export function notificationTarget(item: Pick<NotificationItem, 'orderId'>) {
  return item.orderId && /^[1-9][0-9]{0,18}$/.test(item.orderId) ? `/orders/${item.orderId}` : null
}

/**
 * 새 탭·새 창 열기(보조 버튼·수정 키)는 브라우저 동작을 그대로 두고, 같은 탭 이동만 읽음 처리 결과를 기다린다.
 * 그래야 읽음 처리가 실패했을 때 목록 화면에서 안내할 수 있다.
 */
export function waitsForReadBeforeNavigation(event: {
  button: number
  metaKey: boolean
  ctrlKey: boolean
  shiftKey: boolean
  altKey: boolean
}) {
  return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey
}

/** 헤더 배지는 99를 넘으면 99+로 줄인다. 0 이하·비정상 값은 표시하지 않는다. */
export function unreadBadge(count: number | undefined) {
  if (count === undefined || !Number.isInteger(count) || count <= 0) return null
  return count > 99 ? '99+' : String(count)
}
