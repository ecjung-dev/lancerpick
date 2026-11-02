import { useQuery } from '@tanstack/react-query'
import { getWallet, getWalletEntries } from './api'

// sessionKey는 인증 연동 후 제공할 캐시 구분 값이다. API에는 전달하지 않는다.
// 로그아웃·계정 변경 시 ['wallet'] 캐시도 제거해야 한다.
export function useWallet(sessionKey: string) {
  return useQuery({
    queryKey: ['wallet', sessionKey, 'balance'],
    queryFn: ({ signal }) => getWallet(signal),
    enabled: Boolean(sessionKey),
    staleTime: 0,
  })
}

export function useWalletEntries(sessionKey: string, page: number, size = 20) {
  return useQuery({
    queryKey: ['wallet', sessionKey, 'entries', { page, size }],
    queryFn: ({ signal }) => getWalletEntries(page, size, signal),
    enabled: Boolean(sessionKey),
    staleTime: 0,
  })
}
