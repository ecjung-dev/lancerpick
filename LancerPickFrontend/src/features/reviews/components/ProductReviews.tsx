import { useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useProductReviews } from '../queries'
import styles from './Reviews.module.css'

export function ProductReviews({ productId }: { productId: string }) {
  const [page, setPage] = usePageSearchParam()
  const [params, setParams] = useSearchParams()
  const ratingParam = params.get('rating') ?? ''
  const rating = /^[1-5]$/.test(ratingParam) ? Number(ratingParam) : undefined
  const query = useProductReviews(productId, page, rating)

  function changeRating(value: string) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      if (value === '') next.delete('rating')
      else next.set('rating', value)
      next.delete('page')
      return next
    })
  }

  return <section className={styles.reviews} aria-label="구매 후기">
    <h2>구매 후기</h2>
    <label>별점 필터{' '}
      <select value={rating ?? ''} onChange={event => changeRating(event.target.value)}>
        <option value="">전체</option>
        {[5, 4, 3, 2, 1].map(stars => <option key={stars} value={stars}>{stars}점</option>)}
      </select>
    </label>
    {query.isPending ? <AsyncState title="후기를 불러오고 있어요" />
      : query.isError ? <AsyncState title={query.error instanceof ApiError && query.error.status === 404
        ? '현재 공개되지 않은 상품이에요' : '후기를 불러오지 못했어요'}
        error={query.error} onRetry={() => { void query.refetch() }} />
      : <>
        <p>전체 후기 {query.data.summary.reviewCount}건
          {query.data.summary.averageRating !== null && ` · 평균 ${query.data.summary.averageRating.toFixed(1)} / 5`}
        </p>
        {rating !== undefined && <p>{rating}점 후기 {query.data.totalElements}건</p>}
        {query.data.items.length === 0 ? <p>{query.data.summary.reviewCount === 0
          ? '아직 작성된 후기가 없습니다.'
          : query.data.totalElements === 0 ? '선택한 별점의 후기가 없습니다.' : '이 페이지에 후기가 없습니다.'}</p>
          : <ul>{query.data.items.map(review => <li key={review.id}>
            <strong>{review.authorDisplayName}</strong> · 별점 {review.rating} / 5
            {review.content !== null && <p className={styles.content}>{review.content}</p>}
            <small>{formatDateTime(review.createdAt)}</small>
          </li>)}</ul>}
        <nav aria-label="후기 페이지">
          <button disabled={page === 0 || query.isFetching} onClick={() => setPage(0)}>처음</button>
          <button disabled={page === 0 || query.isFetching} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page + 1}페이지</span>
          <button disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => setPage(page + 1)}>다음</button>
        </nav>
      </>}
  </section>
}
