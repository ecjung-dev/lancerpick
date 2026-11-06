import { useQuery } from '@tanstack/react-query'
import { getProductReviews } from './api'

export function useProductReviews(productId: string, page: number, rating?: number) {
  return useQuery({
    queryKey: ['products', 'reviews', productId, page, rating],
    queryFn: ({ signal }) => getProductReviews(productId, page, signal, rating),
    retry: false, gcTime: 0, staleTime: 0,
  })
}
