import { useQuery } from '@tanstack/react-query'
import { getContract } from './api'
import { getMemberSession } from '../auth/api'

export function useContractMember() {
  return useQuery({
    queryKey: ['wallet', 'contract-member'],
    queryFn: ({ signal }) => getMemberSession(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}
export function useContract(id: string, memberId?: string, version?: number) {
  return useQuery({
    queryKey: ['wallet', 'contract', memberId, id, version ?? 'current'],
    queryFn: ({ signal }) => getContract(id, version, signal),
    enabled: !!memberId,
    retry: false,
    gcTime: 0,
    refetchInterval: (query) =>
      !query.state.error && query.state.data?.status === 'GENERATING'
        ? 5000
        : false,
  })
}
