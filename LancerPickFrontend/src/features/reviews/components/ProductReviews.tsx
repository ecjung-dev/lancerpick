import { useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Icon } from '../../../shared/components/Icon'
import { formatDateTime } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useProductReviews } from '../queries'
import styles from './ProductReviews.module.css'

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
    <div className={styles.heading}><div><h2>구매 후기</h2><p>구매자가 남긴 서비스 이용 후기입니다.</p></div>
    <label>별점 필터{' '}
      <select aria-label="별점 필터" value={rating ?? ''} onChange={event => changeRating(event.target.value)}>
        <option value="">전체</option>
        {[5, 4, 3, 2, 1].map(stars => <option key={stars} value={stars}>{stars}점</option>)}
      </select>
    </label></div>
    {query.isPending ? <AsyncState title="후기를 불러오고 있어요" />
      : query.isError ? <AsyncState title={query.error instanceof ApiError && query.error.status === 404
        ? '현재 공개되지 않은 상품이에요' : '후기를 불러오지 못했어요'}
        error={query.error} onRetry={() => { void query.refetch() }} />
      : <>
        <div className={styles.summary}>
          <div className={styles.score} aria-hidden="true"><Icon name="star" size={24} /><strong>{query.data.summary.averageRating === null ? '—' : query.data.summary.averageRating.toFixed(1)}</strong><span>/ 5</span></div>
        <p>전체 후기 {query.data.summary.reviewCount}건
          {query.data.summary.averageRating !== null && ` · 평균 ${query.data.summary.averageRating.toFixed(1)} / 5`}
        </p>
        </div>
        {rating !== undefined && <p className={styles.filtered}>{rating}점 후기 {query.data.totalElements}건</p>}
        {query.data.items.length === 0 ? <div className={styles.empty}><Icon name="star" size={28} /><p>{query.data.summary.reviewCount === 0
          ? '아직 작성된 후기가 없습니다.'
          : query.data.totalElements === 0 ? '선택한 별점의 후기가 없습니다.' : '이 페이지에 후기가 없습니다.'}</p><span>{query.data.summary.reviewCount === 0 ? '거래를 완료한 구매자의 후기가 이곳에 표시됩니다.' : '다른 별점이나 페이지를 선택해 보세요.'}</span></div>
          : <ul className={styles.list}>{query.data.items.map(review => <li key={review.id}>
            <div className={styles.reviewHeading}><div><strong>{review.authorDisplayName}</strong><span className={styles.stars} role="img" aria-label={`별점 ${review.rating} / 5`}>{[1, 2, 3, 4, 5].map(star => <span key={star} className={star <= review.rating ? styles.filled : ''}><Icon name="star" size={14} /></span>)}</span></div><time dateTime={review.createdAt}>{formatDateTime(review.createdAt)}</time></div>
            {review.content !== null ? <p className={styles.content}>{review.content}</p> : <p className={styles.ratingOnly}>별점만 남긴 후기입니다.</p>}
          </li>)}</ul>}
        {(page > 0 || query.data.totalPages > 1) && <nav aria-label="후기 페이지">
          <button disabled={page === 0 || query.isFetching} onClick={() => setPage(0)}>처음</button>
          <button disabled={page === 0 || query.isFetching} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page + 1}페이지</span>
          <button disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => setPage(page + 1)}>다음</button>
        </nav>}
      </>}
  </section>
}
