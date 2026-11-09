import { prepareOrderInput, type CreateOrderInput } from './create.ts'

const key = (memberId: string, productId: string) => `lancerpick-order-attempt:${memberId}:${productId}`

// 결과가 불확실한 요청은 새로고침 후에도 같은 본문·키로만 재확인한다.
export function readOrderAttempt(storage: Storage, memberId: string, productId: string): CreateOrderInput | null {
  const raw = storage.getItem(key(memberId, productId))
  if (!raw) return null
  const value = JSON.parse(raw)
  if (typeof value.productId !== 'string' || typeof value.requirements !== 'string'
    || typeof value.requestKey !== 'string' || value.productId !== productId) throw new Error('저장된 주문 요청을 확인할 수 없어요. 주문 내역을 먼저 확인해 주세요.')
  return prepareOrderInput(value)
}

export function saveOrderAttempt(storage: Storage, memberId: string, input: CreateOrderInput) {
  storage.setItem(key(memberId, input.productId), JSON.stringify(prepareOrderInput(input)))
}

export function clearOrderAttempt(storage: Storage, memberId: string, productId: string) {
  storage.removeItem(key(memberId, productId))
}

/**
 * 서버가 주문을 만들지 않았다고 확정한 응답인지. 저장한 요청을 정리하고 상품을 다시 확인하도록 안내한다.
 * 403은 권한·CSRF 오류일 수도 있어 이용 제한(MEMBER_RESTRICTED)만 확정 거절로 본다.
 */
export function isDefinitiveOrderRejection(status: number, code: string) {
  return [400, 404, 409].includes(status) || (status === 403 && code === 'MEMBER_RESTRICTED')
}
