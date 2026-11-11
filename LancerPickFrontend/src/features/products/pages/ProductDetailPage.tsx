import { useProductDetail } from '../queries'
import { orderMinimumNotice } from '../orderMinimum'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Icon } from '../../../shared/components/Icon'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import styles from './ProductDetailPage.module.css'
import { ProductReviews } from '../../reviews/components/ProductReviews'
import { ProductFavoriteButton } from '../components/ProductFavoriteButton'

export function ProductDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const from: unknown = location.state?.from
  const fromCatalog = typeof from === 'string' && /^\/products(\?[^#]*)?$/.test(from)
  const fromSeller = typeof from === 'string' && /^\/sellers\/[0-9]{1,19}(\?[^#]*)?$/.test(from)
  const fromFavorites = typeof from === 'string' && /^\/mypage\/favorites(\?[^#]*)?$/.test(from)
  const back =
    typeof from === 'string' && (from === '/' || from.startsWith('/?') || fromSeller || fromFavorites || fromCatalog)
      ? from
      : '/'
  const product = useProductDetail(id)
  const minimumNotice = product.data ? orderMinimumNotice(product.data) : null
  const missing =
    product.error instanceof ApiError && product.error.status === 404
  return (
    <section className={styles.page}>
      <Link to={back} state={fromSeller ? location.state?.sellerState : undefined}>
        {fromSeller ? '← 판매자 상품으로' : fromFavorites ? '← 내 관심 상품으로' : '← 상품 목록으로'}
      </Link>
      {product.isPending ? (
        <AsyncState title="상품을 불러오고 있어요" />
      ) : product.isError ? (
        <AsyncState
          title={
            missing
              ? '현재 조회할 수 없는 상품이에요'
              : '상품을 불러오지 못했어요'
          }
          description={
            missing
              ? '판매가 중지되거나 공개되지 않은 상품일 수 있어요.'
              : undefined
          }
          error={missing ? undefined : product.error}
          onRetry={
            missing
              ? undefined
              : () => {
                  void product.refetch()
                }
          }
        />
      ) : (
        <>
          <header className={styles.heading}>
            <p>{product.data.category.name}</p>
            <h1>{product.data.title}</h1>
            <p>
              판매자 · <Link
                to={`/sellers/${product.data.seller.id}`}
                state={fromSeller ? location.state?.sellerState : {
                  from: location.pathname + location.search,
                  productState: location.state,
                }}
              >{product.data.seller.displayName} 소개 보기</Link>
            </p>
          </header>
          <div className={styles.grid}>
            <div className={styles.content}>
              <Detail title="서비스 소개" text={product.data.description} />
              <Detail title="작업 범위" text={product.data.scopeDescription} />
              <Detail
                title="구매 전 준비사항"
                text={product.data.preparationDescription}
              />
            </div>
            <aside className={styles.summary} aria-label="가격 및 작업 기간">
              <p>고정 가격</p>
              <strong>{formatWon(product.data.priceAmount)}</strong>
              <dl>
                <dt>작업 기간</dt>
                <dd>{product.data.durationDays}일</dd>
                <dt>판매 상태</dt>
                <dd>판매 중</dd>
              </dl>
              {minimumNotice && <p>{minimumNotice}</p>}
              <div className={styles.summaryActions}>
              {!minimumNotice && (
                <Link className={styles.orderAction} to={`/products/${product.data.id}/order`}>
                  주문 요청 작성하기 <Icon name="arrow" size={17} />
                </Link>
              )}
              <ProductFavoriteButton productId={product.data.id} />
              </div>
            </aside>
          </div>
          <footer className={styles.dates}>
            등록 {formatDateTime(product.data.createdAt)} · 수정{' '}
            {formatDateTime(product.data.updatedAt)}
          </footer>
          <ProductReviews key={product.data.id} productId={product.data.id} />
        </>
      )}
    </section>
  )
}

function Detail({ title, text }: { title: string; text: string }) {
  return (
    <section className={styles.block}>
      <h2>{title}</h2>
      <p>{text}</p>
    </section>
  )
}
