import type { TopupConfirmation, WalletTopup } from './types'

export const CHECKOUT_STORAGE_KEY = 'lancerpick.wallet.checkout.v1'
const MAX_AGE = 30 * 60 * 1000
const confirmations = new Map<string, Promise<void>>()

export interface PendingCheckout {
  id: string
  orderId: string
  amount: number
  nonce: string
  createdAt: number
}

export function isTopupId(value: string) {
  return /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n
}

export function parseTopupAmount(value: string): number | null {
  if (!/^[1-9]\d{0,6}$/.test(value)) return null
  const amount = Number(value)
  return amount <= 1000000 ? amount : null
}

export function getCheckoutStorage(): Storage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.sessionStorage } catch { return undefined }
}

export function readPendingCheckout(storage = getCheckoutStorage(), now = Date.now()): PendingCheckout | null {
  try {
    const value: unknown = JSON.parse(storage?.getItem(CHECKOUT_STORAGE_KEY) ?? 'null')
    if (!value || typeof value !== 'object' || !('id' in value) || typeof value.id !== 'string' || !isTopupId(value.id)
      || !('orderId' in value) || typeof value.orderId !== 'string' || !/^[A-Za-z0-9_=-]{6,64}$/.test(value.orderId)
      || !('amount' in value) || typeof value.amount !== 'number' || parseTopupAmount(String(value.amount)) === null
      || !('nonce' in value) || typeof value.nonce !== 'string' || !/^[0-9a-f-]{36}$/.test(value.nonce)
      || !('createdAt' in value) || typeof value.createdAt !== 'number' || !Number.isFinite(value.createdAt)
      || value.createdAt > now || now - value.createdAt > MAX_AGE) return null
    return { id: value.id, orderId: value.orderId, amount: value.amount, nonce: value.nonce, createdAt: value.createdAt }
  } catch { return null }
}

export function savePendingCheckout(value: PendingCheckout, storage = getCheckoutStorage()) {
  if (!storage) throw new Error('결제 복귀 정보를 보관하려면 브라우저의 세션 저장소가 필요합니다.')
  storage.setItem(CHECKOUT_STORAGE_KEY, JSON.stringify({
    id: value.id, orderId: value.orderId, amount: value.amount, nonce: value.nonce, createdAt: value.createdAt,
  }))
}

export function forgetPendingCheckout(id: string, storage = getCheckoutStorage()) {
  if (readPendingCheckout(storage)?.id === id) storage?.removeItem(CHECKOUT_STORAGE_KEY)
}

export function readTopupCallback(params: URLSearchParams, pending: PendingCheckout | null, id: string): TopupConfirmation | null {
  if (!pending || pending.id !== id || !isTopupId(id)) return null
  for (const key of ['nonce', 'paymentKey', 'orderId', 'amount']) if (params.getAll(key).length !== 1) return null
  const key = params.get('paymentKey') ?? ''
  const amount = parseTopupAmount(params.get('amount') ?? '')
  if (params.get('nonce') !== pending.nonce || params.get('orderId') !== pending.orderId
    || amount !== pending.amount || !/^[\x21-\x7E]{1,200}$/.test(key)
    || (params.has('paymentType') && params.get('paymentType') !== 'NORMAL')) return null
  return { paymentKey: key, orderId: pending.orderId, amount }
}

export function matchesPendingTopup(topup: WalletTopup, pending: PendingCheckout) {
  return topup.id === pending.id && topup.tossOrderId === pending.orderId && topup.amount === pending.amount
    && topup.currency === 'KRW' && topup.isTest
}

/** StrictMode/재렌더링은 같은 승인 POST를 다시 실행하지 않는다. 실패도 자동 재시도하지 않는다. */
export function confirmCheckoutOnce(id: string, operation: () => Promise<unknown>): Promise<void> {
  const previous = confirmations.get(id)
  if (previous) return previous
  const result = Promise.resolve().then(operation).then(() => undefined)
  confirmations.set(id, result)
  return result
}

/** 로그아웃·계정 변경 시 캐시와 함께 제거한다. 토큰·paymentKey는 세션 저장소에 보관하지 않는다. */
export function clearCheckoutState(storage = getCheckoutStorage()) {
  confirmations.clear()
  try { storage?.removeItem(CHECKOUT_STORAGE_KEY) } catch { /* 저장소 차단 시 남은 서버 상태는 본인 API로 확인 */ }
}
