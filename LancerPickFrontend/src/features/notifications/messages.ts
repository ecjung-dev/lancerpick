import type { NotificationItem } from './types.ts'

const messages: Record<string, string> = {
  ORDER_CREATED: '새 주문 요청이 도착했습니다.',
  ORDER_ACCEPTED: '판매자가 주문을 수락했습니다. 주문 상세에서 다음 절차를 확인해 주세요.',
  ORDER_CONTRACT_CREATED: '계약 초안이 작성되었습니다. 계약 내용을 확인해 주세요.',
  ORDER_CONTRACT_SIGNATURE_REQUESTED: '상대방이 계약에 먼저 서명했습니다. 주문 상세에서 계약과 서명 상태를 확인해 주세요.',
  ORDER_CONTRACT_SIGNED: '양측 서명이 완료되어 계약이 성립했습니다. 주문 상세에서 다음 절차를 확인해 주세요.',
  ORDER_REJECTED: '판매자가 주문을 거절했습니다.',
  ORDER_PAID: '구매자가 결제를 완료했습니다. 작업을 시작해 주세요.',
  ORDER_DELIVERED: '판매자가 결과물을 납품했습니다. 확인해 주세요.',
  ORDER_REVISION_REQUESTED: '구매자가 수정을 요청했습니다.',
  ORDER_CANCELLATION_REQUESTED: '구매자가 취소를 요청했습니다.',
  ORDER_CANCELLATION_REJECTED: '판매자가 취소 요청을 거절했습니다.',
  ORDER_CANCELLATION_ADMIN_REJECTED: '관리자가 운영 문의를 검토해 취소 요청을 거절했습니다.',
  ORDER_CANCELLATION_WITHDRAWN: '구매자가 취소 요청을 철회했습니다.',
  ORDER_CANCELLED: '주문이 취소되었습니다.',
  ORDER_COMPLETED: '구매자가 구매를 확정했습니다.',
  ORDER_TIMER_STARTED:
    '판매자가 자동 구매 확정 타이머를 시작했습니다. 기한 안에 검수 결과를 알려 주세요.',
  ORDER_TIMER_ADMIN_STOPPED: '관리자가 자동 구매 확정 타이머를 해제했습니다.',
  ORDER_AUTO_CONFIRMED:
    '검수 응답 기한이 지나 구매가 자동으로 확정되었습니다. 보관된 대금은 수수료를 제외하고 판매자 적립금으로 지급되었습니다.',
  ORDER_AUTO_CONFIRM_SOON:
    '24시간 안에 구매가 자동으로 확정됩니다. 검수 결과가 있으면 기한 전에 알려 주세요.',
  // 결제 금액 보관(V3) 주문: 보관·지급·반환
  ORDER_FUNDS_HELD: '구매자가 결제를 완료해 대금이 주문에 보관되었습니다. 작업을 시작해 주세요.',
  ORDER_FUNDS_RELEASED:
    '구매자가 구매를 확정했습니다. 보관된 대금이 수수료를 제외하고 적립금으로 지급되었습니다.',
  ORDER_FUNDS_REFUNDED: '주문이 취소되어 보관된 대금이 구매자에게 전액 반환되었습니다.',
  // 기한 통지
  ORDER_DELIVERY_DUE_NOTIFIED: '결제가 완료되어 납기가 정해졌습니다. 주문 상세에서 납기를 확인해 주세요.',
  ORDER_REDELIVERY_DUE_NOTIFIED:
    '수정 요청으로 재납품 기한이 정해졌습니다. 주문 상세에서 기한을 확인해 주세요.',
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
