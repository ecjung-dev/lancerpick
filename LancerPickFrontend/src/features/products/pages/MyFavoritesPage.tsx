import { loginPath } from '../../auth/session'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatWon } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { getFavorites } from '../favoritesApi'
import { useMyProductsSession } from '../queries'
import styles from './MyProductsPage.module.css'

export function MyFavoritesPage() {
  const session = useMyProductsSession()
  const location = useLocation()
  const path = location.pathname + location.search
  return <section className={styles.page}>
    <Link to="/mypage">← 마이페이지</Link>
    <h1>내 관심 상품</h1>
    {isUnauthorized(session.error) ? <LoginNotice path={path} />
      : session.isError ? <AsyncState title="계정을 확인하지 못했어요" error={session.error} onRetry={() => { void session.refetch() }} />
        : !session.data || session.isFetching ? <AsyncState title="계정을 확인하고 있어요" />
          : <FavoritesList key={session.data.id} memberId={session.data.id} path={path} />}
  </section>
}

function FavoritesList({ memberId, path }: { memberId: string; path: string }) {
  const [page, setPage] = usePageSearchParam()
  const favorites = useQuery({
    queryKey: ['favorites', memberId, 'list', page],
    queryFn: ({ signal }) => getFavorites(page, signal),
    retry: false,
    gcTime: 0,
  })
  if (isUnauthorized(favorites.error)) return <LoginNotice path={path} />
  if (favorites.isError) return <AsyncState title="관심 상품을 불러오지 못했어요" error={favorites.error} onRetry={() => { void favorites.refetch() }} />
  if (!favorites.data) return <AsyncState title="관심 상품을 불러오고 있어요" />
  const { items, totalElements, totalPages } = favorites.data
  return <>
    <p>총 {totalElements}개 · 현재 공개·판매 중인 상품을 표시해요.</p>
    {items.length > 0 ? <ul className={styles.list}>{items.map(product => <li className={styles.card} key={product.id}>
      <h2><Link to={`/products/${product.id}`} state={{ from: path }}>{product.title}</Link></h2>
      <p>{product.category.name} · {product.seller.displayName} · {formatWon(product.priceAmount)} · {product.durationDays}일</p>
    </li>)}</ul> : page === 0
      ? <AsyncState title="표시할 관심 상품이 없어요" />
      : <Button variant="secondary" onClick={() => setPage(0)}>첫 페이지로</Button>}
    {(totalPages > 1 || page > 0) && <nav className={styles.pagination} aria-label="관심 상품 페이지">
      <Button variant="secondary" disabled={page === 0 || favorites.isFetching} onClick={() => setPage(page - 1)}>이전</Button>
      <span>{page + 1}페이지</span>
      <Button variant="secondary" disabled={page + 1 >= totalPages || favorites.isFetching} onClick={() => setPage(page + 1)}>다음</Button>
    </nav>}
  </>
}

function LoginNotice({ path }: { path: string }) {
  return <Link to={loginPath(path)}>로그인하고 관심 상품 보기</Link>
}
