import { useQuery } from '@tanstack/react-query'
import { getAdminProduct, getAdminProducts, getModerationLogs } from './api'
import type { AdminProductFilter } from './types'

/**
 * 관리자 조회 캐시는 ['admin'] 아래에 둔다. 로그인·로그아웃 때 resetAccountState()가
 * ['auth'] 외 캐시를 모두 지우므로 다른 계정에 이전 관리자 화면 내용이 남지 않는다.
 * 숨김 상태는 다른 관리자가 바꿀 수 있어 매번 최신으로 조회하고 보관하지 않는다.
 */
export const adminKeys = {
  list: (filter: AdminProductFilter, page: number) => ['admin', 'products', filter, page] as const,
  detail: (id: string) => ['admin', 'product', id] as const,
  logs: (id: string, page: number) => ['admin', 'logs', id, page] as const,
}

const fresh = { retry: false, staleTime: 0, gcTime: 0 } as const

export function useAdminProducts(filter: AdminProductFilter, page: number) {
  return useQuery({ queryKey: adminKeys.list(filter, page), queryFn: ({ signal }) => getAdminProducts(filter, page, signal), ...fresh })
}

export function useAdminProduct(id: string) {
  return useQuery({ queryKey: adminKeys.detail(id), queryFn: ({ signal }) => getAdminProduct(id, signal), ...fresh })
}

export function useModerationLogs(id: string, page: number) {
  return useQuery({ queryKey: adminKeys.logs(id, page), queryFn: ({ signal }) => getModerationLogs(id, page, signal), ...fresh })
}
