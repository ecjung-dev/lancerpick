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
import styles from './Orders.module.css'

export function OrderListPage() {
  const [params, setParams] = useSearchParams()
  const filters = readOrderListFilters(params)

  // 조건이 바뀌면 첫 페이지로 돌아간다.
  function apply(changes: Partial<OrderListFilters>) {
    setParams(orderListSearch({ ...filters, page: 0, ...changes }))
  }

  return (
    <section className={styles.orders}>
      <Link to="/mypage">← 마이페이지</Link>
      <h1>{filters.side === 'SELLER' ? '판매 내역' : '구매 내역'}</h1>
      <SearchForm
        key={`${filters.q}|${filters.from}|${filters.to}`}
        filters={filters}
        onApply={apply}
      />
      <div className={styles['list-options']}>
        <label>
          주문 상태{' '}
          <select
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
      <label>
        검색어{' '}
        <input
          type="search"
          value={q}
          placeholder="상품명·상대방 이름·주문번호"
          onChange={(event) => setQ(event.target.value)}
        />
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
        <button type="button" onClick={() => period(1)}>
          최근 1개월
        </button>
        <button type="button" onClick={() => period(3)}>
          최근 3개월
        </button>
        <button type="button" onClick={() => period(null)}>
          전체 기간
        </button>
      </div>
      {error && <p role="alert">{error}</p>}
      <div className={styles['list-search-actions']}>
        <button type="submit">조회</button>
        <button type="button" onClick={reset}>
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
      <p>총 {query.data.totalElements.toLocaleString('ko-KR')}건</p>
      {query.data.items.length === 0 ? (
        <p>이 조건에 맞는 거래 내역이 없습니다.</p>
      ) : (
        <ul>
          {query.data.items.map((order) => (
            <li key={order.id}>
              <Link to={`/orders/${order.id}${location.search}`}>
                {order.productTitle}
              </Link>
              <p>
                주문번호 {order.id} · {order.counterparty.displayName} ·{' '}
                {formatWon(order.priceAmount)} · {orderStatusLabel(order.status)}
              </p>
              <p>
                주문 {formatDateTime(order.createdAt)}
                {order.currentDueAt &&
                  ` · 현재 납기 ${formatDateTime(order.currentDueAt)}`}
              </p>
            </li>
          ))}
        </ul>
      )}
      <nav aria-label="거래 내역 페이지">
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
        <button onClick={() => onPageChange(0)}>첫 페이지로</button>
      )}
    </>
  )
}
