import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useProductReviews } from '../queries'
import styles from './Reviews.module.css'

export function ProductReviews({ productId }: { productId: string }) {
  const [page, setPage] = usePageSearchParam()
  const query = useProductReviews(productId, page)
  return <section className={styles.reviews} aria-label="구매 후기">
    <h2>구매 후기</h2>
    {query.isPending ? <AsyncState title="후기를 불러오고 있어요" />
      : query.isError ? <AsyncState title={query.error instanceof ApiError && query.error.status === 404
        ? '현재 공개되지 않은 상품이에요' : '후기를 불러오지 못했어요'}
        error={query.error} onRetry={() => { void query.refetch() }} />
      : <>
        <p>전체 {query.data.totalElements}개</p>
        {query.data.items.length === 0 ? <p>{page > 0 ? '이 페이지에 후기가 없습니다.' : '아직 작성된 후기가 없습니다.'}</p>
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
