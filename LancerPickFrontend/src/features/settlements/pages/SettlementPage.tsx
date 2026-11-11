import { loginPath } from '../../auth/session'
import { Link, useLocation, useSearchParams } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { readSettlementFilters, settlementSearch, type SettlementFilters } from '../filters'
import { SettlementFiltersForm } from '../components/SettlementFiltersForm'
import type { Settlement } from '../types'
import styles from './SettlementPage.module.css'

import { useSettlements, useSettlementSummary } from '../queries'

function payoutLabel(status: Settlement['payoutStatus']) {
  return status === 'CREDITED' ? '적립금 지급 완료'
    : status === 'RECORD_ONLY' ? '기존 거래 · 수익 기록만 보관'
    : '적립금 지급 기록 확인 필요'
}

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
    error => isUnauthorized(error),
  )

  function retry() {
    void list.refetch()
    void summary.refetch()
  }

  const heading = <header className={styles.heading}>
    <p className={styles.eyebrow}>SELLER EARNINGS</p>
    <h1>판매 수익</h1>
    {!unauthorized && <>
      <p>
        구매 확정된 거래의 수익 기록입니다. 새 전자계약 거래의 수익은
        수수료를 제외하고 구매 적립금으로 지급됩니다.
      </p>
      <p>기존 거래의 수익은 별도 기록으로 유지하며, 출금 기능은 제공하지 않습니다.</p>
    </>}
  </header>

  if (unauthorized) {
    return <section className={styles.settlements}>
      {heading}
      <p className={styles.notice}>로그인 후 본인의 판매 수익을 확인할 수 있습니다.{' '}
        <Link to={loginPath(location.pathname + location.search)}>로그인</Link></p>
    </section>
  }

  return <section className={styles.settlements}>
    {heading}
    <SettlementFiltersForm key={JSON.stringify([filters.from, filters.to, filters.sort, filters.q])} filters={filters} onApply={apply} />
    {error ? <div role="alert" className={styles.notice}>
      <p>판매 수익을 불러오지 못했습니다.</p>
      <button type="button" className={styles.primary} onClick={retry}>다시 조회</button>
    </div>
      : !list.data || !summary.data ? <p role="status" className={styles.notice}>판매 수익을 불러오는 중입니다.</p>
      : <>
        <div className={styles.sectionHead}>
          <h2>조회 조건 합계</h2>
          <p className={styles.period}>
            {filters.q && <>검색: {filters.q} · </>}
            {filters.from || filters.to ? `${filters.from || '시작 제한 없음'} ~ ${filters.to || '종료 제한 없음'}` : '전체 기간'} · 사용 가능한 적립금 잔액과 다른 금액입니다.
          </p>
        </div>
        <dl className={styles['settlement-summary']}>
          <div><dt>구매 확정 거래</dt><dd>{summary.data.completedCount}건</dd></div>
          <div><dt>판매 총액</dt><dd>{formatWon(summary.data.grossAmount)}</dd></div>
          <div><dt>플랫폼 수수료</dt><dd>{formatWon(summary.data.feeAmount)}</dd></div>
          <div className={styles.net}><dt>수수료 제외 수익</dt><dd>{formatWon(summary.data.sellerAmount)}</dd></div>
        </dl>
        <div className={styles.sectionHead}>
          <h2>정산 내역 <small>총 {list.data.totalElements}건</small></h2>
        </div>
        {list.data.items.length === 0 ? <div className={styles.empty}>
          <strong>조회 조건에 맞는 정산 내역이 없습니다.</strong>
          <p>검색어나 정산 기록일 기간을 변경해 보세요.</p>
        </div>
          : <ul className={styles['settlement-list']}>
            {list.data.items.map(item => <li key={item.id}>
              <div className={styles.cardMeta}>
                <time dateTime={item.createdAt}>정산 기록일 {formatDateTime(item.createdAt)}</time>
                <span className={item.payoutStatus === 'CREDITED' ? styles.badge
                  : item.payoutStatus === 'RECORD_ONLY' ? styles.badgeMuted : styles.badgeWarning}>
                  {payoutLabel(item.payoutStatus)}
                </span>
              </div>
              <div className={styles.cardBody}>
                <div>
                  <strong className={styles.productTitle}>{item.productTitle ?? '보관된 상품 정보 없음'}</strong>
                  <p className={styles.counterparty}>구매자: {item.buyerDisplayName ?? '정보 없음'}</p>
                </div>
                <b className={styles.earning}>판매 수익 {formatWon(item.sellerAmount)}</b>
              </div>
              <div className={styles.cardFooter}>
                <p>결제액 {formatWon(item.grossAmount)} · 수수료 {formatWon(item.feeAmount)}</p>
                <Link className={styles['order-link']} to={`/orders/${item.orderId}`}>
                  주문 #{item.orderId} 상세 보기
                </Link>
              </div>
            </li>)}
          </ul>}
        {list.data.items.length === 0 && page > 0 &&
          <button type="button" className={styles.secondary} disabled={list.isFetching} onClick={() => setPage(0)}>첫 페이지로</button>}
        {page < list.data.totalPages && (page > 0 || list.data.totalPages > 1) && <nav aria-label="정산 내역 페이지">
          <button type="button" className={styles.secondary} disabled={list.isFetching || page === 0} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page + 1}페이지</span>
          <button type="button" className={styles.secondary} disabled={list.isFetching || page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>다음</button>
        </nav>}
      </>}
  </section>
}
