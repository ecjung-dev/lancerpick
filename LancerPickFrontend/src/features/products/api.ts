// 이 기능의 서버 요청 함수를 구현합니다. 공통 요청 처리는 shared/api/client.ts에 둡니다.
import { apiRequest } from '../../shared/api/client'
import type { PageResponse } from '../../shared/api/types'
import type { Category, ProductSearch, ProductSummary } from './types'

export function getCategories(signal?: AbortSignal) {
  return apiRequest<Category[]>('/categories', { signal })
}

export function getProducts(search: ProductSearch, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(search.page), size: String(search.size) })
  if (search.keyword) params.set('keyword', search.keyword)
  if (search.categoryId) params.set('categoryId', search.categoryId)
  return apiRequest<PageResponse<ProductSummary>>(`/products?${params}`, { signal })
}
