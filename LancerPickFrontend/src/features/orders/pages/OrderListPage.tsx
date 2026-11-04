import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { getOrders } from '../api'
import { orderStatuses, orderStatusNames, orderStatusLabel } from '../status'
import styles from './Orders.module.css'

export function OrderListPage() {
  const [params, setParams] = useSearchParams()
  const side = params.get('side') === 'SELLER' ? 'SELLER' : 'BUYER'
  const status = orderStatuses.find(value => value === params.get('status')) ?? ''
  const requestedPage = Number(params.get('page') ?? 0)
  const page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 2147483647 ? requestedPage : 0

  function changeSide(nextSide: 'BUYER' | 'SELLER') {
    setParams(status ? { side: nextSide, status } : { side: nextSide })
  }

  function changeStatus(nextStatus: string) {
    setParams(nextStatus ? { side, status: nextStatus } : { side })
  }

  function changePage(nextPage: number) {
    setParams(status ? { side, status, page: String(nextPage) } : { side, page: String(nextPage) })
  }

  return <section className={styles.orders}>
    <Link to="/mypage">← 마이페이지</Link>
    <h1>거래 내역</h1>
    <nav aria-label="거래 구분">
      <button aria-pressed={side === 'BUYER'} onClick={() => changeSide('BUYER')}>구매 내역</button>
      <button aria-pressed={side === 'SELLER'} onClick={() => changeSide('SELLER')}>판매 내역</button>
    </nav>
    <label>주문 상태 <select value={status} onChange={event => changeStatus(event.target.value)}>
      <option value="">전체</option>
      {orderStatuses.map((value, index) => <option key={value} value={value}>{orderStatusNames[index]}</option>)}
    </select></label>
    <OrderList side={side} status={status} page={page} onPageChange={changePage} />
  </section>
}

function OrderList({ side, status, page, onPageChange }: { side: 'BUYER' | 'SELLER'; status: string; page: number; onPageChange: (page: number) => void }) {
  const location = useLocation()
  const query = useQuery({ queryKey: ['wallet', 'orders', side, status, page], queryFn: ({ signal }) => getOrders(side, page, signal, status), retry: false, gcTime: 0 })
  if (query.error instanceof ApiError && query.error.status === 401) return <p>로그인 후 거래 내역을 확인할 수 있습니다. <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}>로그인</Link></p>
  if (query.isError) return <div role="alert"><p>{query.error instanceof ApiError ? query.error.message : '거래 내역을 불러오지 못했습니다.'}</p><button onClick={() => { void query.refetch() }}>다시 조회</button></div>
  if (!query.data) return <p role="status">거래 내역을 불러오는 중입니다.</p>
  return <>
    {query.data.items.length === 0 ? <p>이 조건에 맞는 거래 내역이 없습니다.</p> : <ul>{query.data.items.map(order =>
      <li key={order.id}><Link to={`/orders/${order.id}${location.search}`}>{order.productTitle}</Link>
        <p>{order.counterparty.displayName} · {formatWon(order.priceAmount)} · {orderStatusLabel(order.status)}</p>
        <p>주문 {formatDateTime(order.createdAt)}{order.currentDueAt && ` · 현재 납기 ${formatDateTime(order.currentDueAt)}`}</p>
      </li>)}</ul>}
    <nav aria-label="거래 내역 페이지"><button disabled={page === 0 || query.isFetching} onClick={() => onPageChange(page - 1)}>이전</button><span>{page + 1}페이지</span><button disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => onPageChange(page + 1)}>다음</button></nav>
    {page > 0 && query.data.items.length === 0 && <button onClick={() => onPageChange(0)}>첫 페이지로</button>}
  </>
}
