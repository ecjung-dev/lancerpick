import { loginPath } from '../../auth/session'
import { useOrders } from '../queries'
import { Link, useLocation, useSearchParams } from 'react-router'
import { useState, type FormEvent } from 'react'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { orderStatuses, orderStatusNames, orderStatusLabel } from '../status'
import {
  MAX_ORDER_KEYWORD_LENGTH,
  keywordLength,
  orderListSearch,
  readOrderListFilters,
  recentMonths,
  type OrderListFilters,
} from '../listFilters'
import { Icon } from '../../../shared/components/Icon'
import styles from './OrderListPage.module.css'

export function OrderListPage() {
  const [params, setParams] = useSearchParams()
  const filters = readOrderListFilters(params)

  // 조건이 바뀌면 첫 페이지로 돌아간다.
  function apply(changes: Partial<OrderListFilters>) {
    setParams(orderListSearch({ ...filters, page: 0, ...changes }))
  }

  return (
    <section className={styles.orders}>
      <header className={styles.heading}>
        <div>
          <h1>{filters.side === 'SELLER' ? '판매 내역' : '구매 내역'}</h1>
          <p>{filters.side === 'SELLER' ? '받은 주문과 진행 상황을 확인하세요.' : '주문한 서비스와 진행 상황을 확인하세요.'}</p>
        </div>
        <Link className={styles.back} to="/mypage">마이페이지 <span aria-hidden="true">↗</span></Link>
      </header>
      <div className={styles.filters}>
        <SearchForm
          key={`${filters.q}|${filters.from}|${filters.to}`}
          filters={filters}
          onApply={apply}
        />
        <div className={styles['list-options']}>
          <label>
            주문 상태{' '}
            <select
              aria-label="주문 상태"
              value={filters.status}
              onChange={(event) => apply({ status: event.target.value })}
            >
              <option value="">전체</option>
              {orderStatuses.map((value, index) => (
                <option key={value} value={value}>
                  {orderStatusNames[index]}
                </option>
              ))}
            </select>
          </label>
          <label>
            정렬{' '}
            <select
              aria-label="정렬"
              value={filters.sort}
              onChange={(event) =>
                apply({ sort: event.target.value === 'oldest' ? 'oldest' : 'latest' })
              }
            >
              <option value="latest">최신순</option>
              <option value="oldest">오래된순</option>
            </select>
          </label>
        </div>
      </div>
      <OrderList
        filters={filters}
        onPageChange={(page) => setParams(orderListSearch({ ...filters, page }))}
      />
    </section>
  )
}

function SearchForm({
  filters,
  onApply,
}: {
  filters: OrderListFilters
  onApply: (changes: Partial<OrderListFilters>) => void
}) {
  const [q, setQ] = useState(filters.q)
  const [from, setFrom] = useState(filters.from)
  const [to, setTo] = useState(filters.to)
  const [error, setError] = useState('')

  function submit(next: { q: string; from: string; to: string }) {
    const keyword = next.q.trim()
    if (keywordLength(keyword) > MAX_ORDER_KEYWORD_LENGTH) {
      setError(`검색어는 ${MAX_ORDER_KEYWORD_LENGTH}자 이하로 입력해 주세요.`)
      return
    }
    if (next.from && next.to && next.from > next.to) {
      setError('시작일이 종료일보다 늦습니다.')
      return
    }
    setError('')
    onApply({ q: keyword, from: next.from, to: next.to })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    submit({ q, from, to })
  }

  // 적용된 조건이 이미 비어 있으면 key가 그대로라 다시 마운트되지 않으므로 입력·오류를 직접 비운다.
  function reset() {
    setQ('')
    setFrom('')
    setTo('')
    setError('')
    onApply({ q: '', from: '', to: '', status: '', sort: 'latest' })
  }

  function period(months: number | null) {
    const range = months === null ? { from: '', to: '' } : recentMonths(months)
    setFrom(range.from)
    setTo(range.to)
    submit({ q, ...range })
  }

  return (
    <form className={styles['list-search']} onSubmit={onSubmit} role="search" aria-label="거래 내역 검색">
      <label className={styles.keyword}>
        검색어{' '}
        <span className={styles.searchInput}>
        <Icon name="search" size={18} />
        <input
          type="search"
          value={q}
          placeholder="상품명·상대방 이름·주문번호"
          onChange={(event) => setQ(event.target.value)}
        />
        </span>
      </label>
      <div className={styles['list-period']} role="group" aria-label="주문일">
        <label>
          시작일{' '}
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          종료일{' '}
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        <div className={styles.presets} role="group" aria-label="빠른 기간 선택">
          <button type="button" aria-pressed={from === recentMonths(1).from && to === recentMonths(1).to} onClick={() => period(1)}>
            최근 1개월
          </button>
          <button type="button" aria-pressed={from === recentMonths(3).from && to === recentMonths(3).to} onClick={() => period(3)}>
            최근 3개월
          </button>
          <button type="button" aria-pressed={!from && !to} onClick={() => period(null)}>
            전체 기간
          </button>
        </div>
      </div>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <div className={styles['list-search-actions']}>
        <button className={styles.primary} type="submit"><Icon name="search" size={16} />조회</button>
        <button className={styles.reset} type="button" onClick={reset}>
          <Icon name="refresh" size={15} />
          초기화
        </button>
      </div>
    </form>
  )
}

function OrderList({
  filters,
  onPageChange,
}: {
  filters: OrderListFilters
  onPageChange: (page: number) => void
}) {
  const location = useLocation()
  const query = useOrders(filters)
  const { page } = filters
  if (isUnauthorized(query.error))
    return (
      <p>
        로그인 후 거래 내역을 확인할 수 있습니다.{' '}
        <Link
          to={loginPath(location.pathname + location.search)}
        >
          로그인
        </Link>
      </p>
    )
  if (query.isError)
    return (
      <div role="alert">
        <p>
          {query.error instanceof ApiError
            ? query.error.message
            : '거래 내역을 불러오지 못했습니다.'}
        </p>
        <button
          onClick={() => {
            void query.refetch()
          }}
        >
          다시 조회
        </button>
      </div>
    )
  if (!query.data) return <p role="status">거래 내역을 불러오는 중입니다.</p>
  return (
    <>
      <div className={styles.results} aria-live="polite">
        <p>총 {query.data.totalElements.toLocaleString('ko-KR')}건</p>
        <span>{filters.status ? orderStatusLabel(filters.status) : '전체 거래'} · {filters.sort === 'oldest' ? '오래된순' : '최신순'}</span>
      </div>
      {query.data.items.length === 0 ? (
        <div className={styles.empty}><Icon name="document" size={28} /><p>이 조건에 맞는 거래 내역이 없습니다.</p><span>검색어나 조회 기간을 변경해 보세요.</span></div>
      ) : (
        <ul className={styles.list}>
          {query.data.items.map((order) => (
            <li key={order.id}>
              <Link className={styles.card} to={`/orders/${order.id}${location.search}`}>
                <div className={styles.cardTop}>
                  <span className={`${styles.badge} ${['IN_PROGRESS', 'REVISION_IN_PROGRESS'].includes(order.status) ? styles.working : ''}`}>
                    {orderStatusLabel(order.status)}
                  </span>
                  <span className={styles.orderNumber}>주문번호 {order.id}</span>
                </div>
                <div className={styles.cardMain}>
                  <div className={styles.product}>
                    <h2>{order.productTitle}</h2>
                    <p><span>{filters.side === 'SELLER' ? '구매자' : '판매자'}</span> {order.counterparty.displayName}</p>
                  </div>
                  <div className={styles.amount}><span>주문 금액</span><strong>{formatWon(order.priceAmount)}</strong></div>
                </div>
                <div className={styles.cardBottom}>
                  <div className={styles.dates}>
                    <span>주문일 <time dateTime={order.createdAt}>{formatDateTime(order.createdAt)}</time></span>
                    {order.currentDueAt && <span>현재 납기 <time dateTime={order.currentDueAt}>{formatDateTime(order.currentDueAt)}</time></span>}
                  </div>
                  <span className={styles.detail}>거래 상세 <Icon name="arrow" size={16} /></span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <nav className={styles.pager} aria-label="거래 내역 페이지">
        <button
          disabled={page === 0 || query.isFetching}
          onClick={() => onPageChange(page - 1)}
        >
          이전
        </button>
        <span>{page + 1}페이지</span>
        <button
          disabled={page + 1 >= query.data.totalPages || query.isFetching}
          onClick={() => onPageChange(page + 1)}
        >
          다음
        </button>
      </nav>
      {page > 0 && query.data.items.length === 0 && (
        <button className={styles.firstPage} onClick={() => onPageChange(0)}>첫 페이지로</button>
      )}
    </>
  )
}
