import { useQuery } from '@tanstack/react-query'
import { getCategories, getProducts } from './api'
import type { ProductSearch } from './types'

// 필터·페이지까지 queryKey에 포함해야 다른 검색 결과의 캐시가 섞이지 않는다.
export function useProducts(search: ProductSearch) {
  return useQuery({ queryKey: ['products', search], queryFn: ({ signal }) => getProducts(search, signal) })
}
export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: ({ signal }) => getCategories(signal), staleTime: 60_000 })
}
