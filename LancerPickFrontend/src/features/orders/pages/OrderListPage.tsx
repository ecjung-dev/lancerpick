import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatWon } from '../../../shared/lib/format'
import { getOrders } from '../api'
import styles from './Orders.module.css'

const statuses = ['REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED'] as const
const statusNames = ['수락 대기', '결제 대기', '진행 중', '납품 확인 대기', '수정 진행 중', '구매 확정', '거절됨', '취소됨']

export function OrderListPage() {
  const [side, setSide] = useState<'BUYER' | 'SELLER'>('BUYER')
  const [status, setStatus] = useState('')
  return <section className={styles.orders}><h1>거래 내역</h1><nav aria-label="거래 구분"><button aria-pressed={side === 'BUYER'} onClick={() => setSide('BUYER')}>구매 내역</button><button aria-pressed={side === 'SELLER'} onClick={() => setSide('SELLER')}>판매 내역</button></nav><label>주문 상태 <select value={status} onChange={event => setStatus(event.target.value)}><option value="">전체</option>{statuses.map((value, index) => <option key={value} value={value}>{statusNames[index]}</option>)}</select></label><OrderList key={`${side}:${status}`} side={side} status={status} /></section>
}
function OrderList({ side, status }: { side: 'BUYER' | 'SELLER'; status: string }) {
  const [page, setPage] = useState(0)
  const query = useQuery({ queryKey: ['wallet', 'orders', side, status, page], queryFn: ({ signal }) => getOrders(side, page, signal, status), retry: false, gcTime: 0 })
  if (query.error instanceof ApiError && query.error.status === 401) return <p>로그인 후 거래 내역을 확인할 수 있습니다. <Link to="/login">로그인</Link></p>
  if (query.isError) return <div role="alert"><p>거래 내역을 불러오지 못했습니다.</p><button onClick={() => { void query.refetch() }}>다시 조회</button></div>
  if (!query.data) return <p role="status">거래 내역을 불러오는 중입니다.</p>
  return <>{query.data.items.length === 0 ? <p>이 조건에 맞는 거래 내역이 없습니다.</p> : <ul>{query.data.items.map(order => <li key={order.id}><Link to={`/orders/${order.id}`}>{order.productTitle}</Link><p>{order.counterparty.displayName} · {formatWon(order.priceAmount)} · {order.status}</p></li>)}</ul>}<nav aria-label="거래 내역 페이지"><button disabled={page === 0 || query.isFetching} onClick={() => setPage(p => p - 1)}>이전</button><span>{page + 1}페이지</span><button disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => setPage(p => p + 1)}>다음</button></nav></>
}
