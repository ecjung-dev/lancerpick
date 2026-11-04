import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import type { PageResponse } from '../../../shared/api/types'
import { previewBalance, previewEntries } from '../preview'
import { useWallet, useWalletEntries } from '../queries'
import { getWalletSession } from '../api'
import type { WalletBalance, WalletEntry, WalletEntryType } from '../types'
import styles from './WalletPage.module.css'

const labels: Record<WalletEntryType, string> = {
  INITIAL_GRANT: '가입 적립금', PAYMENT: '서비스 결제', REFUND: '결제 환불', TOPUP: '테스트 결제 충전',
}

/** 서버 세션의 본인 확인 후 계정별 지갑 조회를 시작한다. */
export function WalletPage() {
  const session = useQuery({
    queryKey: ['wallet', 'session'],
    queryFn: ({ signal }) => getWalletSession(signal),
    retry: false,
    gcTime: 0,
    staleTime: 0,
  })
  if (session.isFetching) return <WalletLayout><AsyncState title="로그인 상태를 확인하고 있어요" /></WalletLayout>
  if (session.error instanceof ApiError && session.error.status === 401) return <WalletGuestPage />
  if (session.isError) return <WalletLayout><AsyncState title="로그인 상태를 확인하지 못했어요" error={session.error} onRetry={() => { void session.refetch() }} /></WalletLayout>
  if (!session.data) return <WalletLayout><AsyncState title="로그인 상태를 확인하고 있어요" /></WalletLayout>
  return <ConnectedWalletPage key={session.data.id} sessionKey={session.data.id} />
}

function WalletGuestPage() {
  return <WalletLayout>
    <Link className={styles.previewLink} to="/mypage/settlements">판매 수익 확인 →</Link>
    <AsyncState title="로그인 후 지갑을 확인할 수 있어요" description="로그인한 계정의 적립금과 이용 내역을 표시합니다." />
    <Link className={styles.previewLink} to="/login">로그인으로 이동 →</Link>
    <Link className={styles.previewLink} to="/mypage/wallet/preview">예시 데이터로 화면 미리보기 →</Link>
  </WalletLayout>
}

/** WalletPage의 서버 세션 확인 후 연결하는 화면. sessionKey는 API로 전송하지 않는다. */
export function ConnectedWalletPage({ sessionKey }: { sessionKey: string }) {
  const [page, setPage] = useState(0)
  const balance = useWallet(sessionKey)
  const entries = useWalletEntries(sessionKey, page)

  if (!sessionKey) return <WalletGuestPage />
  if ([balance.error, entries.error].some((error) => error instanceof ApiError && error.status === 401)) {
    return <WalletLayout>
      <AsyncState title="다시 로그인해 주세요" description="로그인 상태를 확인할 수 없어 잔액과 내역을 표시하지 않습니다." />
      <Link className={styles.previewLink} to="/login">로그인으로 이동 →</Link>
    </WalletLayout>
  }
  return <WalletLayout>
    {balance.isError
      ? <AsyncState title="잔액을 불러오지 못했어요" error={balance.error} onRetry={() => { void balance.refetch() }} />
      : balance.data ? <BalanceCard balance={balance.data} /> : <AsyncState title="잔액을 불러오고 있어요" />}
    {entries.isError
      ? <AsyncState title="이용 내역을 불러오지 못했어요" error={entries.error} onRetry={() => { void entries.refetch() }} />
      : entries.data ? <EntryList data={entries.data} onPageChange={setPage} /> : <AsyncState title="이용 내역을 불러오고 있어요" />}
  </WalletLayout>
}

export function WalletPreviewPage() {
  const [page, setPage] = useState(0)
  const size = 3
  const data: PageResponse<WalletEntry> = {
    items: previewEntries.slice(page * size, (page + 1) * size),
    page, size, totalElements: previewEntries.length, totalPages: Math.ceil(previewEntries.length / size),
  }
  return <WalletLayout preview>
    <BalanceCard balance={previewBalance} preview />
    <EntryList data={data} onPageChange={setPage} />
  </WalletLayout>
}

function WalletLayout({ children, preview = false }: { children: ReactNode; preview?: boolean }) {
  return <section className={styles.page}>
    <div className={styles.breadcrumb}><Link to="/">홈</Link><span aria-hidden="true">/</span><span>마이페이지</span></div>
    <div className={styles.heading}><div><p className={styles.eyebrow}>MY ACCOUNT</p><h1>마이페이지</h1></div><p>나의 거래를 한눈에, 관리도 간편하게.</p></div>
    {preview && <div className={styles.notice} role="note"><strong>화면 미리보기</strong><span>아래 잔액과 내역은 예시입니다. 실제 계정·결제 정보가 아닙니다.</span><Link to="/mypage">미리보기 나가기</Link></div>}
    <div className={styles.columns}>
      <aside className={styles.sidebar} aria-label="마이페이지 메뉴">
        <div className={styles.account}><span className={styles.avatar} aria-hidden="true">L</span><strong>나의 공간</strong><small>{preview ? '예시 화면을 둘러보고 있어요' : '적립금과 거래 내역을 확인하세요'}</small></div>
        <nav className={styles.menu} aria-label="내 활동">
          <span className={styles.menuLabel}>내 활동</span>
          <Link to="/mypage/seller-profile">판매자 프로필</Link>
          <span className={styles.active} aria-current="page">적립금 · 이용 내역 <span aria-hidden="true">↗</span></span>
          <Link to="/orders?side=BUYER">구매 내역</Link>
          <Link to="/orders?side=SELLER">판매 내역</Link>
          <Link to="/mypage/settlements">판매 수익</Link>
          <span className={styles.disabled}>내 정보 <small>준비 중</small></span>
        </nav>
      </aside>
      <div className={styles.content}>
        <div className={styles.sectionHeading}><h2>적립금 · 이용 내역</h2><p>사용 가능한 잔액과 적립금 변동을 확인하세요.</p></div>
        {children}
        <div className={styles.guide}><strong>적립금 이용 안내</strong><ul><li>가입 시 테스트 적립금 1,000,000원이 한 번 지급됩니다.</li><li>테스트 적립금은 현금 가치가 없으며 실제 현금 충전·출금을 지원하지 않습니다.</li><li>구매 적립금과 판매 수익은 별도로 관리됩니다.</li></ul></div>
      </div>
    </div>
  </section>
}

function BalanceCard({ balance, preview = false }: { balance: WalletBalance; preview?: boolean }) {
  return <section className={styles.balanceCard} aria-labelledby="balance-title">
    <div><div className={styles.balanceLabel}><h3 id="balance-title">사용 가능한 적립금</h3>{balance.isTest && <span>TEST CREDIT</span>}</div><p className={styles.amount}>{formatWon(balance.balance)}</p><p className={styles.balanceHelp}>마음에 드는 서비스를 만나면, 적립금으로 결제하세요.</p></div>
    <Link to={preview ? '/mypage/wallet/topup/preview' : '/mypage/wallet/topup'} className={styles.explore}>{preview ? '충전 화면 미리보기' : '테스트 적립금 충전'} <span aria-hidden="true">↗</span></Link>
  </section>
}

function EntryList({ data, onPageChange }: { data: PageResponse<WalletEntry>; onPageChange: (page: number) => void }) {
  return <section className={styles.history} aria-labelledby="history-title">
    <div className={styles.historyHeading}><h3 id="history-title">전체 이용 내역 <span>{data.totalElements}건</span></h3><span>최신순 · 한국 시간</span></div>
    {data.items.length === 0 ? <AsyncState title="표시할 이용 내역이 없어요" description="적립금의 지급·결제·환불 내역이 여기에 표시됩니다." /> : <ul className={styles.entries}>
      {data.items.map((entry) => <li key={entry.id} className={styles.entry}>
        <span className={`${styles.entryIcon} ${entry.type === 'PAYMENT' ? styles.paymentIcon : ''}`} aria-hidden="true">{entry.type === 'PAYMENT' ? '−' : '+'}</span>
        <div className={styles.entryDetail}><strong>{labels[entry.type] ?? '적립금 변동'}</strong><time dateTime={entry.createdAt}>{formatDateTime(entry.createdAt)}</time>{entry.orderId && <small>주문 #{entry.orderId}</small>}</div>
        <div className={styles.entryAmount}><strong>{entry.amountDelta > 0 ? '+' : ''}{formatWon(entry.amountDelta)}</strong><small>처리 후 잔액 {formatWon(entry.balanceAfter)}</small></div>
      </li>)}
    </ul>}
    {data.totalPages > 1 && <nav className={styles.pagination} aria-label="이용 내역 페이지">
      <Button variant="secondary" disabled={data.page === 0} onClick={() => onPageChange(data.page - 1)}>이전</Button>
      <span aria-live="polite">{data.page + 1} / {data.totalPages}</span>
      <Button variant="secondary" disabled={data.page + 1 >= data.totalPages} onClick={() => onPageChange(data.page + 1)}>다음</Button>
    </nav>}
  </section>
}
