import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  Link,
  Outlet,
  useNavigate,
  useSearchParams,
  useLocation,
} from 'react-router'
import { Icon } from './Icon'
import { Input } from './Input'
import { MemberNav } from '../../features/auth/components/MemberNav'
import { normalizeText } from '../lib/text'
import { AccountLayout } from '../../features/account/components/AccountLayout'
import { ChatWorkspaceProvider } from '../../features/order-chat/components/ChatWorkspaceProvider'
import styles from './AppLayout.module.css'

export function AppLayout() {
  const { pathname } = useLocation()
  const accountPage =
    pathname === '/mypage' ||
    pathname.startsWith('/mypage/') ||
    pathname === '/orders'
  const [params] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  return (
    <ChatWorkspaceProvider>
      <div className={styles.layout}>
        <a href="#main-content" className={styles.skip}>
          본문으로 이동
        </a>
        <AppHeader key={keyword} initialKeyword={keyword} />
        <main className={styles.main} id="main-content" tabIndex={-1}>
          {accountPage ? (
            <AccountLayout>
              <Outlet />
            </AccountLayout>
          ) : (
            <Outlet />
          )}
        </main>
        <footer className={styles.footer}>
          <div className={styles.footerInner}>
            <div>
              <strong>랜서픽</strong>
              <p>좋은 시작이, 좋은 결과로 이어지도록.</p>
            </div>
            <div>
              <p>학습용 팀 프로젝트</p>
              <p>테스트 적립금만 사용하며 실제 현금 거래·출금은 없어요.</p>
            </div>
            <small>© 2026 LancerPick Team</small>
          </div>
        </footer>
      </div>
    </ChatWorkspaceProvider>
  )
}

function AppHeader({ initialKeyword }: { initialKeyword: string }) {
  const [input, setInput] = useState(initialKeyword)
  const [openedAt, setOpenedAt] = useState<string | null>(null)
  const location = useLocation()
  const expanded = openedAt === location.key
  const trigger = useRef<HTMLButtonElement>(null)
  const mobileForm = useRef<HTMLFormElement>(null)
  const desktopForm = useRef<HTMLFormElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (expanded) mobileForm.current?.querySelector('input')?.focus()
  }, [expanded])
  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 721px)')
    const resize = () => {
      if (!desktop.matches) return
      const focused = mobileForm.current?.contains(document.activeElement)
        || document.activeElement === trigger.current
      setOpenedAt(null)
      if (focused) desktopForm.current?.querySelector('input')?.focus()
    }
    desktop.addEventListener('change', resize)
    return () => desktop.removeEventListener('change', resize)
  }, [])

  function close() {
    setOpenedAt(null)
    trigger.current?.focus()
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    const next = new URLSearchParams()
    const keyword = normalizeText(input)
    if (keyword) next.set('keyword', keyword)
    if (expanded) {
      setOpenedAt(null)
      document.getElementById('main-content')?.focus()
    }
    navigate(`/products${next.size ? `?${next.toString()}` : ''}`)
  }

  return <header className={styles.header} data-app-header>
    <div className={styles.headerInner}>
      <Link to="/" className={styles.brand} aria-label="랜서픽 홈"><span className={styles.brandMark} aria-hidden="true" />랜서픽</Link>
      <nav className={styles.discovery} aria-label="서비스 탐색">
        <Link to="/products" className={styles.browse}>서비스 둘러보기</Link>
        <form ref={desktopForm} className={styles.search} role="search" aria-label="서비스 검색" onSubmit={submit}>
          <label className={styles.srOnly} htmlFor="service-search">서비스 검색</label>
          <Input id="service-search" type="search" placeholder="어떤 서비스를 찾으세요?" value={input} onChange={event => setInput(event.target.value)} />
          <button type="submit" aria-label="검색"><Icon name="search" size={19} /></button>
        </form>
        <button ref={trigger} className={styles.mobileSearchToggle} type="button" aria-label={expanded ? '서비스 검색 닫기' : '서비스 검색 열기'} aria-expanded={expanded} aria-controls="mobile-service-search-form" onClick={() => expanded ? close() : setOpenedAt(location.key)}>
          <Icon name="search" size={21} />
        </button>
      </nav>
      <nav className={styles.nav} aria-label="회원 메뉴"><MemberNav signupClassName={styles.signup} buttonClassName={styles.navButton} /></nav>
      <form ref={mobileForm} id="mobile-service-search-form" className={styles.mobileSearch} hidden={!expanded} role="search" aria-label="모바일 서비스 검색" onSubmit={submit}
        onKeyDown={event => {
          if (event.key === 'Escape' && !event.nativeEvent.isComposing && event.nativeEvent.keyCode !== 229) { event.preventDefault(); close() }
        }}>
        <label className={styles.srOnly} htmlFor="mobile-service-search">서비스 검색</label>
        <div className={styles.mobileSearchField}>
          <Input id="mobile-service-search" type="search" placeholder="어떤 서비스를 찾으세요?" value={input} onChange={event => setInput(event.target.value)} />
          <button type="submit" aria-label="검색"><Icon name="search" size={19} /></button>
        </div>
        <button type="button" className={styles.closeSearch} onClick={close}>닫기</button>
      </form>
    </div>
  </header>
}
