import { apiRequest } from '../../shared/api/client'
import type { PageResponse } from '../../shared/api/types'
import type { ProductSummary } from './types'

function favoritePath(productId: string) {
  if (!/^[0-9]{1,19}$/.test(productId) || BigInt(productId) <= 0n || BigInt(productId) > 9223372036854775807n) {
    throw new Error('올바른 상품 번호가 아니에요.')
  }
  return `/me/favorites/${productId}`
}

export function getFavorite(productId: string, signal?: AbortSignal) {
  return apiRequest<{ favorited: boolean }>(favoritePath(productId), { signal, cache: 'no-store' })
}

export function setFavorite(productId: string, favorited: boolean) {
  return apiRequest<void>(favoritePath(productId), { method: favorited ? 'PUT' : 'DELETE' })
}

export function getFavorites(page: number, signal?: AbortSignal) {
  if (!Number.isInteger(page) || page < 0 || page > 2147483647) {
    throw new Error('페이지를 확인해 주세요.')
  }
  return apiRequest<PageResponse<ProductSummary>>(`/me/favorites?page=${page}&size=20`, { signal, cache: 'no-store' })
}
