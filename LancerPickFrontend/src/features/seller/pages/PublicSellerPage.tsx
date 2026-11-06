import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { ProductCard } from '../../products/components/ProductCard'
import { useProducts } from '../../products/queries'
import { usePublicSellerProfile } from '../queries'
import { isSafePortfolioUrl } from '../validation'
import styles from './PublicSellerPage.module.css'

export function PublicSellerPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const from: unknown = location.state?.from
  const productPath = typeof from === 'string' && /^\/products\/[0-9]{1,19}(\?[^#]*)?$/.test(from)
    ? from
    : null
  const profile = usePublicSellerProfile(id)
  const missing = profile.error instanceof ApiError && profile.error.status === 404

  return (
    <section className={styles.page}>
      <Link to={productPath ?? '/'} state={productPath ? location.state?.productState : undefined}>
        {productPath ? '← 상품 상세로' : '← 상품 목록으로'}
      </Link>
      {profile.isPending ? <AsyncState title="판매자 소개를 불러오고 있어요." />
        : profile.isError ? (
          <AsyncState
            title={missing ? '판매자 소개를 찾을 수 없어요.' : '판매자 소개를 불러오지 못했어요.'}
            error={missing ? undefined : profile.error}
            onRetry={missing ? undefined : () => void profile.refetch()}
          />
        ) : (
          <>
            <header className={styles.profile}>
              <h1>{profile.data.displayName}</h1>
              <dl>
                <dt>전문 분야</dt>
                <dd>{profile.data.specialty}</dd>
                <dt>소개</dt>
                <dd>{profile.data.introduction}</dd>
              </dl>
              {profile.data.portfolioUrl && isSafePortfolioUrl(profile.data.portfolioUrl) && (
                <a href={profile.data.portfolioUrl} target="_blank" rel="noopener noreferrer">
                  포트폴리오 보기 (새 탭)
                </a>
              )}
            </header>
            <SellerProducts key={profile.data.id} sellerId={profile.data.id} />
          </>
        )}
    </section>
  )
}

function SellerProducts({ sellerId }: { sellerId: string }) {
  const [page, setPage] = usePageSearchParam()
  const location = useLocation()
  const navigate = useNavigate()
  const products = useProducts({ keyword: '', categoryId: '', sellerId, page, size: 8 })

  return (
    <section aria-labelledby="seller-products-title" aria-busy={products.isFetching}>
      <h2 id="seller-products-title">판매 중인 상품</h2>
      {products.isPending ? <AsyncState title="상품을 불러오고 있어요." />
        : products.isError ? (
          <AsyncState title="상품을 불러오지 못했어요." error={products.error} onRetry={() => void products.refetch()} />
        ) : (
          <>
            {page > 0 && page >= products.data.totalPages ? (
              <>
                <AsyncState title="요청한 페이지에 상품이 없어요." />
                <Button variant="secondary" onClick={() => setPage(0)}>첫 페이지로</Button>
              </>
            ) : products.data.items.length === 0 ? (
              <AsyncState title="현재 판매 중인 상품이 없어요." />
            ) : (
              <div className={styles.products}>
                {products.data.items.map((product) => (
                  <ProductCard key={product.id} product={product} onSelect={() => {
                    navigate(`/products/${product.id}`, {
                      state: { from: location.pathname + location.search, sellerState: location.state },
                    })
                  }} />
                ))}
              </div>
            )}
            {products.data.totalPages > 1 && page < products.data.totalPages && (
              <nav className={styles.pagination} aria-label="판매자 상품 페이지">
                <Button variant="secondary" disabled={page === 0 || products.isFetching} onClick={() => setPage(page - 1)}>이전</Button>
                <span>{page + 1} / {products.data.totalPages}</span>
                <Button variant="secondary" disabled={page + 1 >= products.data.totalPages || products.isFetching} onClick={() => setPage(page + 1)}>다음</Button>
              </nav>
            )}
          </>
        )}
    </section>
  )
}
