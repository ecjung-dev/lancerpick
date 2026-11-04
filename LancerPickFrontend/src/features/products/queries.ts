import { getProductDetail, getCategories, getProducts } from './api'
import { useQuery } from '@tanstack/react-query'
import { getMemberSession } from '../auth/api'
import { getOwnedProduct } from './management'
import type { ProductSearch } from './types'

// 필터·페이지까지 queryKey에 포함해야 다른 검색 결과의 캐시가 섞이지 않는다.
export function useProducts(search: ProductSearch) {
  return useQuery({
    queryKey: ['products', search],
    queryFn: ({ signal }) => getProducts(search, signal),
  })
}
export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => getCategories(signal),
    staleTime: 60_000,
  })
}

export function useProductDetail(id: string) {
  return useQuery({
    queryKey: ['products', 'detail', id],
    queryFn: ({ signal }) => getProductDetail(id, signal),
    retry: false,
    staleTime: 0,
    gcTime: 0,
  })
}

export function useSaleStatusSession() {
  return useQuery({
    queryKey: ['product-sale-session'], queryFn: ({ signal }) => getMemberSession(signal),
    retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: 'always',
  })
}

export function useSaleStatusProduct(memberId: string, id: string) {
  return useQuery({
    queryKey: ['product-sale-status', memberId, id],
    queryFn: ({ signal }) => getOwnedProduct(id, signal),
    retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: false,
  })
}
