import { apiRequest } from '../../shared/api/client'
import type { PageResponse } from '../../shared/api/types'
import { normalizeText, codePointLength } from '../../shared/lib/text'
import type { PublicReview, Review, ReviewInput } from './types'

function positiveId(id: string) {
  if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) {
    throw new Error('번호를 확인해 주세요.')
  }
  return id
}

export function prepareReview(input: ReviewInput): ReviewInput {
  if (!Number.isInteger(input.rating) || input.rating < 1 || input.rating > 5) {
    throw new Error('별점은 1~5점으로 선택해 주세요.')
  }
  const content = input.content === null ? null : normalizeText(input.content)
  if (content !== null && (codePointLength(content) < 1 || codePointLength(content) > 2000)) {
    throw new Error('후기 글은 1~2,000자로 입력하거나 비워 주세요.')
  }
  return { rating: input.rating, content }
}

export function writeReview(orderId: string, input: ReviewInput) {
  return apiRequest<Review>(`/orders/${positiveId(orderId)}/review`, {
    method: 'POST', json: prepareReview(input),
  })
}

export function getProductReviews(productId: string, page: number, signal?: AbortSignal) {
  if (!Number.isInteger(page) || page < 0 || page > 2147483647) throw new Error('페이지를 확인해 주세요.')
  return apiRequest<PageResponse<PublicReview>>(`/products/${positiveId(productId)}/reviews?page=${page}&size=20`, { signal })
}
