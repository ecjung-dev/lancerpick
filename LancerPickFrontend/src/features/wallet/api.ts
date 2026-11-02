import { apiRequest } from '../../shared/api/client'
import type { PageResponse } from '../../shared/api/types'
import type { WalletBalance, WalletEntry } from './types'

// 본인 조회: 회원 ID는 보내지 않는다. 서버에서 인증 정보로 결정한다.
export function getWallet(signal?: AbortSignal) {
  return apiRequest<WalletBalance>('/me/wallet', { signal })
}

export function getWalletEntries(page: number, size = 20, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiRequest<PageResponse<WalletEntry>>(`/me/wallet/entries?${params}`, { signal })
}
