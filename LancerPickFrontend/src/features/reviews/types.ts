import type { PageResponse } from '../../shared/api/types'

interface PublicReview {
  id: string
  authorDisplayName: string
  rating: number
  content: string | null
  createdAt: string
}

export interface Review extends PublicReview { orderId: string }
export interface ReviewInput { rating: number; content: string | null }

export interface ProductReviewsResponse extends PageResponse<PublicReview> {
  summary: { averageRating: number | null; reviewCount: number }
}
