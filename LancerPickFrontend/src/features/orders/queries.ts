import { getMemberSession } from '../auth/api'
import { getOrder, getOrders, getOrderFunds, getReviewTimer } from './api'
import { reviewTimerRefetchInterval } from './reviewTimer'
import type { OrderListFilters } from './listFilters'
import { useQuery } from '@tanstack/react-query'

function orderListKey(filters: OrderListFilters) {
  const { side, status, page, q, from, to, sort } = filters
  return ['wallet', 'orders', side, status, page, q, from, to, sort] as const
}

export function useOrders(filters: OrderListFilters) {
  return useQuery({
    queryKey: orderListKey(filters),
    queryFn: ({ signal }) => getOrders(filters, signal),
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

export function useOrderFunds(id: string) {
  return useQuery({
    queryKey: ['wallet', 'order-funds', id],
    queryFn: ({ signal }) => getOrderFunds(id, signal),
    retry: false,
    gcTime: 0,
  })
}

// 주문 변경 후 ['wallet'] 무효화로 함께 다시 조회되도록 wallet 하위 키를 쓴다.
export function useReviewTimer(id: string) {
  return useQuery({
    queryKey: ['wallet', 'order-review-timer', id],
    queryFn: ({ signal }) => getReviewTimer(id, signal),
    retry: false,
    gcTime: 0,
    refetchInterval: (query) =>
      reviewTimerRefetchInterval(query.state.data, !!query.state.error),
    refetchIntervalInBackground: false,
  })
}
