import { Link, useLocation } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useMyProducts, useMyProductsSession } from '../queries'
import styles from './MyProductsPage.module.css'

export function MyProductsPage() {
  const session = useMyProductsSession()
  const [page, setPage] = usePageSearchParam()
  const location = useLocation()
  const listPath = location.pathname + location.search
  return <section className={styles.page}>
    <Link to="/mypage">← 마이페이지</Link>
    <div className={styles.heading}><h1>내 상품 조회</h1><Link to="/mypage/products/new" state={{ fromMyProducts: listPath }}>상품 등록</Link></div>
    {session.error instanceof ApiError && session.error.status === 401 ? <LoginNotice path={listPath} />
      : session.isError ? <AsyncState title="계정을 확인하지 못했어요" error={session.error} onRetry={() => { void session.refetch() }} />
        : !session.data ? <AsyncState title="계정 확인 중" />
          : <>
            {session.isFetching && <p role="status">계정을 다시 확인하고 있어요.</p>}
            <div hidden={session.isFetching}>
              <MyProductsList key={session.data.id} memberId={session.data.id} page={page} path={listPath} checking={session.isFetching} onPage={setPage} />
            </div>
          </>}
  </section>
}

function MyProductsList({ memberId, page, path, checking, onPage }: {
  memberId: string; page: number; path: string; checking: boolean; onPage: (page: number) => void
}) {
  const products = useMyProducts(memberId, page)
  if (products.error instanceof ApiError && products.error.status === 401) return <LoginNotice path={path} />
  if (products.isError) return <AsyncState title="내 상품을 불러오지 못했어요" error={products.error} onRetry={() => { void products.refetch() }} />
  if (!products.data) return <AsyncState title="내 상품을 불러오고 있어요" />
  if (products.data.items.some(product => product.seller.id !== memberId)) return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="이전 계정의 상품을 표시하지 않습니다. 페이지를 다시 열어 주세요." />
  const outOfRange = page > 0 && page >= products.data.totalPages
  const busy = products.isFetching || checking
  return <>
    <p>총 {products.data.totalElements}개 · 판매 중지·관리자 숨김 상품도 표시해요.</p>
    {products.data.items.length === 0 ? <AsyncState
      title={outOfRange ? '이 페이지에 상품이 없어요' : '아직 등록한 상품이 없어요'}
      description={outOfRange ? '첫 페이지에서 상품을 확인해 주세요.' : '첫 상품을 등록해 보세요.'} />
      : <ul className={styles.list}>{products.data.items.map(product => <li key={product.id} className={styles.card}>
        <h2>{product.title}</h2>
        <p>{product.category.name} · {formatWon(product.priceAmount)} · {product.durationDays}일</p>
        <p className={styles.states}><span>{product.saleStatus === 'ON_SALE' ? '판매 중' : '판매 중지'}</span><span>{product.isHidden ? '관리자 숨김' : '공개'}</span></p>
        <p className={styles.date}>등록 {formatDateTime(product.createdAt)}</p>
        <div className={styles.actions}>
          <Link to={`/mypage/products/${product.id}/edit`} state={{ fromMyProducts: path }} aria-label={`${product.title} 수정`}>수정</Link>
          <Link to={`/mypage/products/${product.id}/sale-status`} state={{ fromMyProducts: path }} aria-label={`${product.title} 판매 상태 관리`}>판매 상태 관리</Link>
          {!product.isHidden && product.saleStatus === 'ON_SALE' && <Link to={`/products/${product.id}`}>공개 상세</Link>}
        </div>
      </li>)}</ul>}
    {outOfRange && <Button variant="secondary" disabled={busy} onClick={() => onPage(0)}>첫 페이지로</Button>}
    {(products.data.totalPages > 1 || page > 0) && <nav className={styles.pagination} aria-label="내 상품 페이지">
      <Button variant="secondary" disabled={page === 0 || busy} onClick={() => onPage(page - 1)}>이전</Button>
      <span>현재 요청 {page + 1}페이지 · 총 {products.data.totalPages}페이지</span>
      <Button variant="secondary" disabled={page + 1 >= products.data.totalPages || busy} onClick={() => onPage(page + 1)}>다음</Button>
    </nav>}
  </>
}

function LoginNotice({ path }: { path: string }) {
  return <><AsyncState title="로그인 후 내 상품을 확인해 주세요" /><Link to={`/login?next=${encodeURIComponent(path)}`}>로그인</Link></>
}
