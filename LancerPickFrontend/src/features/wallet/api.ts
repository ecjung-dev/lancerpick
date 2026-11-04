import { getMemberSession } from '../auth/api.ts'
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types'
import type { WalletBalance, WalletEntry, WalletTopup, TopupConfirmation } from './types'

/** 화면의 캐시 구분에만 사용한다. 지갑 요청에는 회원 ID를 보내지 않는다. */
export function getWalletSession(signal?: AbortSignal) {
  return getMemberSession(signal)
}

// 본인 조회: 회원 ID는 보내지 않는다. 서버에서 인증 정보로 결정한다.
export function getWallet(signal?: AbortSignal) {
  return apiRequest<WalletBalance>('/me/wallet', { signal })
}

export function getWalletEntries(page: number, size = 20, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<PageResponse<WalletEntry>>(`/me/wallet/entries?${params}`, { signal })
}

export function createWalletTopup(amount: number) {
  return apiRequest<WalletTopup>('/me/wallet/topups', { method: 'POST', json: { amount } })
}

export function getWalletTopup(id: string, signal?: AbortSignal) {
  return apiRequest<WalletTopup>(`/me/wallet/topups/${encodeURIComponent(id)}`, { signal })
}

export function confirmWalletTopup(id: string, confirmation: TopupConfirmation) {
  return apiRequest<WalletTopup>(`/me/wallet/topups/${encodeURIComponent(id)}/confirm`, { method: 'POST', json: confirmation })
}

export function reconcileWalletTopup(id: string) {
  return apiRequest<WalletTopup>(`/me/wallet/topups/${encodeURIComponent(id)}/reconcile`, { method: 'POST' })
}
