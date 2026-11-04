import { useQuery } from '@tanstack/react-query'
import { getProductReviews } from './api'

export function useProductReviews(productId: string, page: number) {
  return useQuery({
    queryKey: ['products', 'reviews', productId, page],
    queryFn: ({ signal }) => getProductReviews(productId, page, signal),
    retry: false, gcTime: 0, staleTime: 0,
  })
}
