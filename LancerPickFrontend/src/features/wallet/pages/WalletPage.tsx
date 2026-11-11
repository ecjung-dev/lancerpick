import { useWalletSession, useWallet, useWalletEntries } from '../queries'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import type { PageResponse } from '../../../shared/api/types'
import { previewBalance, previewEntries } from '../preview'
import type { WalletBalance, WalletEntry, WalletEntryType } from '../types'
import styles from './WalletPage.module.css'
import { PageHeading } from '../../../shared/components/PageHeading'
import { Pager } from '../../../shared/components/Pager'

const labels: Record<WalletEntryType, string> = {
  INITIAL_GRANT: '가입 적립금',
  PAYMENT: '서비스 결제',
  REFUND: '결제 환불',
  TOPUP: '적립금 충전',
  SELLER_PAYOUT: '판매대금 지급',
}

/** 서버 세션의 본인 확인 후 계정별 지갑 조회를 시작한다. */
export function WalletPage() {
  const session = useWalletSession()
  if (session.isFetching)
    return (
      <WalletLayout>
        <AsyncState title="로그인 상태를 확인하고 있어요" />
      </WalletLayout>
    )
  if (isUnauthorized(session.error))
    return <WalletGuestPage />
  if (session.isError)
    return (
      <WalletLayout>
        <AsyncState
          title="로그인 상태를 확인하지 못했어요"
          error={session.error}
          onRetry={() => {
            void session.refetch()
          }}
        />
      </WalletLayout>
    )
  if (!session.data)
    return (
      <WalletLayout>
        <AsyncState title="로그인 상태를 확인하고 있어요" />
      </WalletLayout>
    )
  return (
    <ConnectedWalletPage key={session.data.id} sessionKey={session.data.id} />
  )
}

function WalletGuestPage() {
  return (
    <WalletLayout>
      <Link className={styles.previewLink} to="/mypage/settlements">
        판매 수익 확인 →
      </Link>
      <AsyncState
        title="로그인 후 지갑을 확인할 수 있어요"
        description="로그인한 계정의 적립금과 이용 내역을 표시해요."
      />
      <Link className={styles.previewLink} to="/login">
        로그인으로 이동 →
      </Link>
      <Link className={styles.previewLink} to="/mypage/wallet/preview">
        예시 데이터로 화면 미리보기 →
      </Link>
    </WalletLayout>
  )
}

/** WalletPage의 서버 세션 확인 후 연결하는 화면. sessionKey는 API로 전송하지 않는다. */
export function ConnectedWalletPage({ sessionKey }: { sessionKey: string }) {
  const [page, setPage] = usePageSearchParam()
  const balance = useWallet(sessionKey)
  const entries = useWalletEntries(sessionKey, page)

  if (!sessionKey) return <WalletGuestPage />
  if (
    [balance.error, entries.error].some(
      (error) => isUnauthorized(error),
    )
  ) {
    return (
      <WalletLayout>
        <AsyncState
          title="다시 로그인해 주세요"
          description="로그인 상태를 확인할 수 없어 잔액과 내역을 표시하지 않아요."
        />
        <Link className={styles.previewLink} to="/login">
          로그인으로 이동 →
        </Link>
      </WalletLayout>
    )
  }
  return (
    <WalletLayout>
      {balance.isError ? (
        <AsyncState
          title="잔액을 불러오지 못했어요"
          error={balance.error}
          onRetry={() => {
            void balance.refetch()
          }}
        />
      ) : balance.data ? (
        <BalanceCard balance={balance.data} />
      ) : (
        <AsyncState title="잔액을 불러오는 중이에요" />
      )}
      {entries.isError ? (
        <AsyncState
          title="이용 내역을 불러오지 못했어요"
          error={entries.error}
          onRetry={() => {
            void entries.refetch()
          }}
        />
      ) : entries.data ? (
        <EntryList
          data={entries.data}
          onPageChange={setPage}
          isFetching={entries.isFetching}
        />
      ) : (
        <AsyncState title="이용 내역을 불러오는 중이에요" />
      )}
    </WalletLayout>
  )
}

export function WalletPreviewPage() {
  const [page, setPage] = useState(0)
  const size = 3
  const data: PageResponse<WalletEntry> = {
    items: previewEntries.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: previewEntries.length,
    totalPages: Math.ceil(previewEntries.length / size),
  }
  return (
    <WalletLayout preview>
      <BalanceCard balance={previewBalance} preview />
      <EntryList data={data} onPageChange={setPage} preview />
    </WalletLayout>
  )
}

function WalletLayout({ children, preview = false }: { children: ReactNode; preview?: boolean }) {
  return <section className={styles.page}>
    {preview && <div className={styles.notice} role="note"><strong>화면 미리보기</strong><span>아래 잔액과 내역은 예시예요. 실제 계정·결제 정보가 아니에요.</span><Link to="/mypage">미리보기 나가기</Link></div>}
    <PageHeading eyebrow="MY WALLET" title="적립금 · 이용 내역"><p>사용 가능한 잔액과 적립금 변동을 확인할 수 있어요.</p></PageHeading>
    {children}
    <div className={styles.guide}><strong>적립금 이용 안내</strong><ul>
      <li>가입 시 적립금 1,000,000원이 한 번 지급돼요.</li>
      <li>적립금은 현금 가치가 없으며 출금을 지원하지 않아요.</li>
      <li>새 전자계약 거래의 판매 수익은 수수료를 제외하고 적립금으로 지급돼요. 기존 거래의 수익은 별도 기록으로 유지돼요.</li>
    </ul></div>
  </section>
}

function BalanceCard({
  balance,
  preview = false,
}: {
  balance: WalletBalance
  preview?: boolean
}) {
  return (
    <section className={styles.balanceCard} aria-labelledby="balance-title">
      <div>
        <div className={styles.balanceLabel}>
          <h3 id="balance-title">사용 가능한 적립금</h3>

        </div>
        <p className={styles.amount}>{formatWon(balance.balance)}</p>
        <p className={styles.balanceHelp}>
          마음에 드는 서비스를 만나면 적립금으로 결제할 수 있어요.
        </p>
      </div>
      <Link
        to={preview ? '/mypage/wallet/topup/preview' : '/mypage/wallet/topup'}
        className={styles.explore}
      >
        {preview ? '충전 화면 미리보기' : '적립금 충전'}{' '}
        <span aria-hidden="true">↗</span>
      </Link>
    </section>
  )
}

function EntryList({
  data,
  onPageChange,
  preview = false,
  isFetching = false,
}: {
  data: PageResponse<WalletEntry>
  onPageChange: (page: number) => void
  preview?: boolean
  isFetching?: boolean
}) {
  return (
    <section className={styles.history} aria-labelledby="history-title">
      <div className={styles.historyHeading}>
        <h3 id="history-title">
          전체 이용 내역 <span>{data.totalElements}건</span>
        </h3>
        <span>최신순 · 한국 시간</span>
      </div>
      {data.items.length === 0 ? (
        <AsyncState
          title="표시할 이용 내역이 없어요"
          description="적립금의 지급·결제·환불 내역이 여기에 표시돼요."
        />
      ) : (
        <ul className={styles.entries}>
          {data.items.map((entry) => (
            <li key={entry.id} className={styles.entry}>
              <span
                className={`${styles.entryIcon} ${entry.type === 'PAYMENT' ? styles.paymentIcon : ''}`}
                aria-hidden="true"
              >
                {entry.type === 'PAYMENT' ? '−' : '+'}
              </span>
              <div className={styles.entryDetail}>
                <strong>{labels[entry.type] ?? '적립금 변동'}</strong>
                <time dateTime={entry.createdAt}>
                  {formatDateTime(entry.createdAt)}
                </time>
                {entry.orderId &&
                  (preview ? (
                    <small>주문 #{entry.orderId}</small>
                  ) : (
                    <Link
                      className={styles.orderLink}
                      to={`/orders/${entry.orderId}`}
                    >
                      주문 #{entry.orderId} 상세 보기
                    </Link>
                  ))}
              </div>
              <div className={styles.entryAmount}>
                <strong>
                  {entry.amountDelta > 0 ? '+' : ''}
                  {formatWon(entry.amountDelta)}
                </strong>
                <small>처리 후 잔액 {formatWon(entry.balanceAfter)}</small>
              </div>
            </li>
          ))}
        </ul>
      )}
      <Pager page={data.page} totalPages={data.totalPages} busy={isFetching} onPage={onPageChange} label="이용 내역 페이지" />
    </section>
  )
}
