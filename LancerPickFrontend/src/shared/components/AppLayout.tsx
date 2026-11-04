import { useState } from 'react'
import { Link, Outlet, useNavigate, useSearchParams } from 'react-router'
import { Icon } from './Icon'
import { Input } from './Input'
import { MemberNav } from '../../features/auth/components/MemberNav'
import { normalizeText } from '../lib/text'
import styles from './AppLayout.module.css'

export function AppLayout() {
  const [params] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  return (
    <div className={styles.layout}>
      <a href="#main-content" className={styles.skip}>본문으로 이동</a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand} aria-label="랜서픽 홈"><span className={styles.brandMark} aria-hidden="true" />랜서픽</Link>
          <nav className={styles.discovery} aria-label="서비스 탐색">
            <HeaderSearch key={keyword} initialKeyword={keyword} />
          </nav>
          <nav className={styles.nav} aria-label="회원 메뉴">
            <MemberNav signupClassName={styles.signup} buttonClassName={styles.navButton} />
          </nav>
        </div>
      </header>
      <main className={styles.main} id="main-content">
        <Outlet />
      </main>
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div><strong>랜서픽</strong><p>좋은 시작이, 좋은 결과로 이어지도록.</p></div>
          <div><p>학습용 팀 프로젝트</p><p>테스트 적립금만 사용하며 실제 현금 거래·출금은 없습니다.</p></div>
          <small>© 2026 LancerPick Team</small>
        </div>
      </footer>
    </div>
  )
}

function HeaderSearch({ initialKeyword }: { initialKeyword: string }) {
  const [input, setInput] = useState(initialKeyword)
  const navigate = useNavigate()
  return <form className={styles.search} role="search" onSubmit={(event) => {
    event.preventDefault()
    const next = new URLSearchParams()
    const keyword = normalizeText(input)
    if (keyword) next.set('keyword', keyword)
    navigate(`/?${next.toString()}#services`)
  }}>
    <label className={styles.srOnly} htmlFor="service-search">서비스 검색</label>
    <Input id="service-search" type="search" placeholder="어떤 서비스를 찾으세요?" value={input} onChange={(event) => setInput(event.target.value)} />
    <button type="submit" aria-label="검색"><Icon name="search" size={19} /></button>
  </form>
}
