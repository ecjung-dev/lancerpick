import type { ReactNode } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { useCurrentMember } from '../../auth/queries'
import styles from './AccountLayout.module.css'

export function AccountLayout({ children }: { children: ReactNode }) {
  const member = useCurrentMember()
  const { pathname } = useLocation()
  const [params] = useSearchParams()
  const currentMember = member.isSuccess ? member.data : undefined
  const seller = pathname.startsWith('/mypage/products') || pathname === '/mypage/seller-profile'
    || pathname === '/mypage/settlements' || (pathname === '/orders' && params.get('side') === 'SELLER')
  const canSell = currentMember?.hasSellerProfile === true
  const generalLinks = [
    ['/mypage/profile', '내 정보'], ['/mypage/wallet', '적립금 · 이용 내역'],
    ['/orders?side=BUYER', '구매 내역'], ['/mypage/favorites', '내 관심 상품'],
  ]
  const sellerLinks = [
    ['/mypage/seller-profile', '판매자 프로필'], ['/mypage/products/new', '상품 등록'],
    ['/mypage/products', '내 상품 조회'], ['/orders?side=SELLER', '판매 내역'],
    ['/mypage/settlements', '판매 수익'],
  ]
  function selected(to: string) {
    if (to.startsWith('/orders')) return pathname === '/orders' && seller === to.endsWith('SELLER')
    if (to === '/mypage/wallet') return pathname === '/mypage' || pathname.startsWith('/mypage/wallet')
    return pathname === to
  }
  return <section className={styles.page}>
    <div className={styles.heading}>
      <div><Link to="/">홈</Link><p>MY ACCOUNT</p><strong>마이페이지</strong></div>
      <span>나의 거래를 한눈에, 관리도 간편하게.</span>
    </div>
    <div className={styles.columns}>
      <aside className={styles.sidebar} aria-label="마이페이지 메뉴">
        <div className={styles.member}>
          <span aria-hidden="true" className={styles.avatar}>{currentMember?.displayName.slice(0, 1) ?? 'L'}</span>
          <strong>{currentMember?.displayName ?? '나의 공간'}</strong>
          <span>적립금과 거래 내역을 확인할 수 있어요.</span>
        </div>
        <nav className={styles.switcher} aria-label="마이페이지 구분">
          <Link to="/mypage" aria-current={!seller ? 'page' : undefined}>일반</Link>
          <Link to="/mypage/seller-profile" aria-current={seller ? 'page' : undefined}>판매자</Link>
        </nav>
        {seller && !canSell && <p className={styles.notice} role="status">
          {member.isPending ? '판매자 정보를 확인하고 있어요.'
            : member.isError ? '회원 정보를 확인하지 못했어요. 페이지를 새로고침해 주세요.'
              : !member.data ? '로그인 후 판매자 프로필을 등록할 수 있어요.'
                : '판매자 프로필을 등록하면 판매 기능을 사용할 수 있어요.'}
        </p>}
        <nav className={styles.menu} aria-label={seller ? '판매자 활동' : '일반 활동'}>
          {(seller ? sellerLinks : generalLinks).map(([to, label], index) =>
            seller && index > 0 && !canSell
              ? <span key={to} aria-disabled="true" className={styles.disabled}>{label}<small>프로필 등록 후</small></span>
              : <Link key={to} to={to} aria-current={selected(to) ? 'page' : undefined}>{label}</Link>)}
        </nav>
      </aside>
      <div className={styles.content}>{children}</div>
    </div>
  </section>
}
