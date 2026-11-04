import { getMemberSession } from '../auth/api'
import { getOrder, getOrders } from './api'
import { useQuery } from '@tanstack/react-query'

export function useOrders(
  side: 'BUYER' | 'SELLER',
  status: string,
  page: number,
) {
  return useQuery({
    queryKey: ['wallet', 'orders', side, status, page],
    queryFn: ({ signal }) => getOrders(side, page, signal, status),
    retry: false,
    gcTime: 0,
  })
}

export function useOrder(id: string) {
  return useQuery({
    queryKey: ['wallet', 'order', id],
    queryFn: ({ signal }) => getOrder(id, signal),
    retry: false,
    gcTime: 0,
  })
}

export function useOrderEntryMember() {
  return useQuery({
    queryKey: ['order-entry-session'],
    queryFn: ({ signal }) => getMemberSession(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
}
