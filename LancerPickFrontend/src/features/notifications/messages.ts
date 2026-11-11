import type { NotificationItem } from './types.ts'

const messages: Record<string, string> = {
  ORDER_CREATED: '새 주문 요청이 도착했어요.',
  ORDER_ACCEPTED: '판매자가 주문을 수락했어요. 주문 상세에서 다음 절차를 확인해 주세요.',
  ORDER_CONTRACT_CREATED: '계약 초안이 작성됐어요. 계약 내용을 확인해 주세요.',
  ORDER_CONTRACT_SIGNATURE_REQUESTED: '상대방이 계약에 먼저 서명했어요. 주문 상세에서 계약과 서명 상태를 확인해 주세요.',
  ORDER_CONTRACT_SIGNED: '양측 서명이 완료되어 계약이 성립했어요. 주문 상세에서 다음 절차를 확인해 주세요.',
  ORDER_REJECTED: '판매자가 주문을 거절했어요.',
  ORDER_PAID: '구매자가 결제를 완료했어요. 작업을 시작해 주세요.',
  ORDER_DELIVERED: '판매자가 결과물을 납품했어요. 확인해 주세요.',
  ORDER_REVISION_REQUESTED: '구매자가 수정을 요청했어요.',
  ORDER_CANCELLATION_REQUESTED: '구매자가 취소를 요청했어요.',
  ORDER_CANCELLATION_REJECTED: '판매자가 취소 요청을 거절했어요.',
  ORDER_CANCELLATION_ADMIN_REJECTED: '관리자가 운영 문의를 검토해 취소 요청을 거절했어요.',
  ORDER_CANCELLATION_WITHDRAWN: '구매자가 취소 요청을 철회했어요.',
  ORDER_CANCELLED: '주문이 취소됐어요.',
  ORDER_COMPLETED: '구매자가 구매를 확정했어요.',
  ORDER_TIMER_STARTED:
    '판매자가 자동 구매 확정 타이머를 시작했어요. 기한 안에 검수 결과를 알려 주세요.',
  ORDER_TIMER_ADMIN_STOPPED: '관리자가 자동 구매 확정 타이머를 해제했어요.',
  ORDER_AUTO_CONFIRMED:
    '검수 응답 기한이 지나 구매가 자동으로 확정됐어요. 보관된 대금은 수수료를 제외하고 판매자 적립금으로 지급됐어요.',
  ORDER_AUTO_CONFIRM_SOON:
    '24시간 안에 구매가 자동으로 확정돼요. 검수 결과가 있으면 기한 전에 알려 주세요.',
  // 결제 금액 보관(V3) 주문: 보관·지급·반환
  ORDER_FUNDS_HELD: '구매자가 결제를 완료해 대금이 주문에 보관됐어요. 작업을 시작해 주세요.',
  ORDER_FUNDS_RELEASED:
    '구매자가 구매를 확정했어요. 보관된 대금이 수수료를 제외하고 적립금으로 지급됐어요.',
  ORDER_FUNDS_REFUNDED: '주문이 취소되어 보관된 대금이 구매자에게 전액 반환됐어요.',
  // 기한 통지
  ORDER_DELIVERY_DUE_NOTIFIED: '결제가 완료되어 납기가 정해졌어요. 주문 상세에서 납기를 확인해 주세요.',
  ORDER_REDELIVERY_DUE_NOTIFIED:
    '수정 요청으로 재납품 기한이 정해졌어요. 주문 상세에서 기한을 확인해 주세요.',
  // 회원 알림(주문과 무관)
  PASSWORD_CHANGE_RECOMMENDED:
    '비밀번호를 바꾼 지 6개월이 지났어요. 계정 보호를 위해 내 정보에서 비밀번호를 바꿔 주세요.',
}

/** 서버가 새 유형을 먼저 배포해도 화면이 깨지지 않도록 일반 문구로 대신한다. */
export function notificationMessage(type: string) {
  return messages[type] ?? '거래 상태가 바뀌었어요.'
}

/** 회원 알림 중 이동할 화면이 정해진 유형. 주문과 연결되지 않는다. */
const memberTargets: Record<string, string> = {
  PASSWORD_CHANGE_RECOMMENDED: '/mypage/profile',
}

/**
 * 주문 알림은 주문 상세로, 정해진 회원 알림은 해당 화면으로 이동한다.
 * 주문 ID 형식이 올바르지 않거나 이동할 곳이 없는 유형이면 링크를 만들지 않는다.
 */
export function notificationTarget(item: Pick<NotificationItem, 'orderId'> & { type?: string }) {
  if (item.orderId) return /^[1-9][0-9]{0,18}$/.test(item.orderId) ? `/orders/${item.orderId}` : null
  return (item.type && Object.hasOwn(memberTargets, item.type) && memberTargets[item.type]) || null
}

/** 헤더 배지는 99를 넘으면 99+로 줄인다. 0 이하·비정상 값은 표시하지 않는다. */
export function unreadBadge(count: number | undefined) {
  if (count === undefined || !Number.isInteger(count) || count <= 0) return null
  return count > 99 ? '99+' : String(count)
}
