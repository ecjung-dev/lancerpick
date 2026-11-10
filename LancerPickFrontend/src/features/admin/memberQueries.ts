import { skipToken, useQuery } from '@tanstack/react-query'
import {
  getAdminMember,
  getAdminMembers,
  getMemberSanctions,
} from './memberApi'
import {
  adminMemberPrefix,
  pendingMemberCommandKey,
  type PendingMemberCommand,
} from './memberCommands'
import { isValidProductId as validId } from './validation'
import type { AdminMemberFilter } from './memberTypes'

export const adminMemberKeys = {
  list: (filter: AdminMemberFilter, page: number) =>
    [...adminMemberPrefix, 'list', filter, page] as const,
  detail: (id: string) => [...adminMemberPrefix, 'detail', id] as const,
  history: (id: string, page: number) =>
    [...adminMemberPrefix, 'history', id, page] as const,
}
const fresh = {
  retry: false,
  gcTime: 0,
  staleTime: 0,
  refetchOnWindowFocus: true,
  refetchInterval: 30_000,
  refetchIntervalInBackground: false,
} as const

export function useAdminMembers(filter: AdminMemberFilter, page: number) {
  return useQuery({
    queryKey: adminMemberKeys.list(filter, page),
    queryFn: ({ signal }) => getAdminMembers(filter, page, signal),
    ...fresh,
  })
}
export function useAdminMember(id: string) {
  return useQuery({
    queryKey: adminMemberKeys.detail(id),
    queryFn: ({ signal }) => getAdminMember(id, signal),
    enabled: validId(id),
    ...fresh,
  })
}
export function useMemberSanctions(id: string, page: number) {
  return useQuery({
    queryKey: adminMemberKeys.history(id, page),
    queryFn: ({ signal }) => getMemberSanctions(id, page, signal),
    enabled: validId(id),
    ...fresh,
  })
}
export function usePendingMemberCommand(id: string) {
  return (
    useQuery<PendingMemberCommand | null>({
      queryKey: pendingMemberCommandKey(id),
      queryFn: skipToken,
      initialData: null,
      gcTime: Infinity,
    }).data ?? null
  )
}
