import { Link, useLocation, useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { readSettlementFilters, settlementSearch, type SettlementFilters } from '../filters'
import { SettlementFiltersForm } from '../components/SettlementFiltersForm'
import styles from './SettlementPage.module.css'

import { useSettlements, useSettlementSummary } from '../queries'

export function SettlementPage() {
  const [params, setParams] = useSearchParams()
  const filters = readSettlementFilters(params)
  const location = useLocation()
  const { page } = filters
  const apply = (next: SettlementFilters) => setParams(settlementSearch(next))
  const setPage = (page: number) => apply({ ...filters, page })
  const list = useSettlements(page, filters, filters.sort)
  const summary = useSettlementSummary(filters)
  const error = list.error ?? summary.error
  const unauthorized = [list.error, summary.error].some(
    error => error instanceof ApiError && error.status === 401,
  )

  function retry() {
    void list.refetch()
    void summary.refetch()
  }

  if (unauthorized) {
    return <section className={styles.settlements}>
      <h1>판매 수익</h1>
      <p>로그인 후 본인의 판매 수익을 확인할 수 있습니다.</p>
      <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}>로그인</Link>
    </section>
  }

  return <section className={styles.settlements}>
    <Link to="/mypage">← 마이페이지</Link>
    <h1>판매 수익</h1>
    <p>
      구매 확정된 거래의 수익 기록입니다. 새 전자계약 거래의 수익은
      수수료를 제외하고 구매 적립금으로 지급됩니다.
    </p>
    <p>기존 거래의 수익은 별도 기록으로 유지하며, 출금 기능은 제공하지 않습니다.</p>
    <SettlementFiltersForm key={`${filters.from}|${filters.to}|${filters.sort}`} filters={filters} onApply={apply} />
    {error ? <div role="alert">
      <p>판매 수익을 불러오지 못했습니다.</p>
      <button onClick={retry}>다시 조회</button>
    </div>
      : !list.data || !summary.data ? <p role="status">판매 수익을 불러오는 중입니다.</p>
      : <>
        <h2>조회 조건 합계</h2>
        <p className={styles.period}>{filters.from || filters.to ? `${filters.from || '시작 제한 없음'} ~ ${filters.to || '종료 제한 없음'}` : '전체 기간'} · 사용 가능한 적립금 잔액과 다른 금액입니다.</p>
        <dl className={styles['settlement-summary']}>
          <div><dt>구매 확정 거래 수</dt><dd>{summary.data.completedCount}건</dd></div>
          <div><dt>판매 총액</dt><dd>{formatWon(summary.data.grossAmount)}</dd></div>
          <div><dt>플랫폼 수수료</dt><dd>{formatWon(summary.data.feeAmount)}</dd></div>
          <div><dt>수수료 제외 수익</dt><dd>{formatWon(summary.data.sellerAmount)}</dd></div>
        </dl>
        <h2>정산 내역 <small>총 {list.data.totalElements}건</small></h2>
        {list.data.items.length === 0 ? <p>조회 조건에 맞는 정산 내역이 없습니다.</p>
          : <ul className={styles['settlement-list']}>
            {list.data.items.map(item => <li key={item.id}>
              <Link className={styles['order-link']} to={`/orders/${item.orderId}`}>
                주문 #{item.orderId} 상세 보기
              </Link>
              <time dateTime={item.createdAt}>정산 기록일 {formatDateTime(item.createdAt)}</time>
              <p>결제액 {formatWon(item.grossAmount)} · 수수료 {formatWon(item.feeAmount)}</p>
              <b>판매 수익 {formatWon(item.sellerAmount)}</b>
            </li>)}
          </ul>}
        {list.data.items.length === 0 && page > 0 &&
          <button disabled={list.isFetching} onClick={() => setPage(0)}>첫 페이지로</button>}
        {page < list.data.totalPages && <nav aria-label="정산 내역 페이지">
          <button disabled={list.isFetching || page === 0} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page + 1}페이지</span>
          <button disabled={list.isFetching || page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>다음</button>
        </nav>}
      </>}
  </section>
}
