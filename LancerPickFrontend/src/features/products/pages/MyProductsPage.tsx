import { loginPath } from '../../auth/session'
import { Link, useLocation } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Icon } from '../../../shared/components/Icon'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useMyProducts, useMyProductsSession } from '../queries'
import styles from './MyProductsPage.module.css'
import { Pager } from '../../../shared/components/Pager'
import { PageHeading } from '../../../shared/components/PageHeading'

export function MyProductsPage() {
  const session = useMyProductsSession()
  const [page, setPage] = usePageSearchParam()
  const location = useLocation()
  const listPath = location.pathname + location.search
  return <section className={styles.page}>
    <PageHeading eyebrow="MY SERVICES" title="내 상품 조회"
      actions={<Link className={styles.create} to="/mypage/products/new" state={{ fromMyProducts: listPath }}><span aria-hidden="true">＋</span>상품 등록</Link>}>
      <p>등록한 서비스를 확인하고 판매 상태를 관리할 수 있어요.</p>
    </PageHeading>
    {isUnauthorized(session.error) ? <LoginNotice path={listPath} />
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
  if (isUnauthorized(products.error)) return <LoginNotice path={path} />
  if (products.isError) return <AsyncState title="내 상품을 불러오지 못했어요" error={products.error} onRetry={() => { void products.refetch() }} />
  if (!products.data) return <AsyncState title="내 상품을 불러오고 있어요" />
  if (products.data.items.some(product => product.seller.id !== memberId)) return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="이전 계정의 상품을 표시하지 않아요. 페이지를 다시 열어 주세요." />
  const outOfRange = page > 0 && page >= products.data.totalPages
  const busy = products.isFetching || checking
  return <>
    <div className={styles.listHeading}><p>전체 상품 <strong>{products.data.totalElements.toLocaleString('ko-KR')}</strong>개</p><span>등록일 최신순</span></div>
    <p className={styles.notice}><Icon name="document" size={16} />판매 중지·관리자 숨김 상품도 포함한 내 상품 목록이에요.</p>
    {products.data.items.length === 0 ? <AsyncState
      title={outOfRange ? '이 페이지에 상품이 없어요' : '아직 등록한 상품이 없어요'}
      description={outOfRange ? '첫 페이지에서 상품을 확인해 주세요.' : '첫 상품을 등록해 보세요.'} />
      : <ul className={styles.list}>{products.data.items.map(product => <li key={product.id} className={styles.card}>
        <div className={styles.cardTop}><span className={styles.category}>{product.category.name}</span><div className={styles.states}><span className={product.saleStatus === 'ON_SALE' ? styles.onSale : ''}>{product.saleStatus === 'ON_SALE' ? '판매 중' : '판매 중지'}</span>{product.isHidden && <span className={styles.hidden}>관리자 숨김</span>}</div></div>
        <h2>{product.title}</h2>
        <div className={styles.terms}><span>{product.durationDays}일 작업</span><strong>{formatWon(product.priceAmount)}</strong></div>
        <div className={styles.metadata}><span>상품 #{product.id}</span><span className={styles.visibility}>{product.isHidden ? '공개 목록에서 숨김' : '공개'}</span></div>
        <p className={styles.date}>등록일 <time dateTime={product.createdAt}>{formatDateTime(product.createdAt)}</time></p>
        <div className={styles.actions}>
          <Link className={styles.edit} to={`/mypage/products/${product.id}/edit`} state={{ fromMyProducts: path }} aria-label={`${product.title} 수정`}>상품 수정</Link>
          <Link to={`/mypage/products/${product.id}/sale-status`} state={{ fromMyProducts: path }} aria-label={`${product.title} 판매 상태 관리`}>판매 상태 관리</Link>
          {!product.isHidden && product.saleStatus === 'ON_SALE' && <Link className={styles.public} to={`/products/${product.id}`} aria-label={`${product.title} 공개 상세`}>공개 상세 <Icon name="arrow" size={14} /></Link>}
        </div>
      </li>)}</ul>}
    <Pager page={page} totalPages={products.data.totalPages} busy={busy} onPage={onPage} label="내 상품 페이지" />
  </>
}

function LoginNotice({ path }: { path: string }) {
  return <><AsyncState title="로그인 후 내 상품을 확인해 주세요" /><Link to={loginPath(path)}>로그인</Link></>
}
