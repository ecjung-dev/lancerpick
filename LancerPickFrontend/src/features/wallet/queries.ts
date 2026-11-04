import { getWalletSession, getWallet, getWalletEntries } from './api'
import { useQuery } from '@tanstack/react-query'

// sessionKey는 서버에서 확인한 현재 회원의 캐시 구분 값이다. API에는 전달하지 않는다.
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

export function useWalletSession() {
  return useQuery({
    queryKey: ['wallet', 'session'],
    queryFn: ({ signal }) => getWalletSession(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}

export function useTopupAccess() {
  return useQuery({
    queryKey: ['wallet', 'topup-access'],
    queryFn: async ({ signal }) => {
      await getWallet(signal)
      return true
    },
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnMount: 'always',
  })
}
