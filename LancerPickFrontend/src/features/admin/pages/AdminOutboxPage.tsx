import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { outboxErrorLabel, readOutboxFilter, useOutboxIssues, type OutboxFilter, type OutboxIssueStatus } from '../outboxApi'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { AdminPager } from '../components/AdminPager'
import { canResetFilter } from '../filterReset'
import { codeLabel, outboxTypeLabels } from '../statusLabels'
import styles from './Admin.module.css'
import { StatusBadge } from '../components/StatusBadge'
import { outboxTones, toneOf } from '../statusTone'

const TABS: { key: OutboxIssueStatus; label: string }[] = [
  { key: 'FAILED', label: '처리 중단' }, { key: 'RETRYING', label: '재시도 대기' },
]

/**
 * /admin/outbox. 알림 이벤트 중 자동 재시도를 멈춘 것(FAILED)과 실패 후 다시 시도할 예정인 것(RETRYING)을 조회만 한다(K6).
 * 조건·페이지를 URL에 두어 새로고침·뒤로가기 때 복원한다. 재처리 버튼은 관리자 작업 감사 이력 확정 뒤 추가한다.
 */
export function AdminOutboxPage() {
  const [params, setParams] = useSearchParams()
  const filter = readOutboxFilter(params)

  function apply(next: OutboxFilter) {
    // 조건이 바뀌면 첫 페이지부터. 빈 조건은 주소에 남기지 않는다.
    const query = new URLSearchParams({ status: next.status })
    if (next.orderId) query.set('orderId', next.orderId)
    setParams(query)
  }

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>알림 처리 상태</h1>
    <p className={styles.lead}>주문 알림을 만들지 못한 이벤트를 조회만 해요. 거래 자체는 이미 끝났고 알림만 빠진 상태예요. 다시 보내기는 운영 이력 기능과 함께 추가할 예정이에요.</p>
    <nav className={styles.subtabs} aria-label="알림 처리 상태 종류">
      {TABS.map((item) => <Link key={item.key} to={`/admin/outbox?status=${item.key}${filter.orderId ? `&orderId=${filter.orderId}` : ''}`}
        aria-current={filter.status === item.key ? 'page' : undefined}>{item.label}</Link>)}
    </nav>
    <Issues key={params.toString()} filter={filter} onApply={apply} search={params.toString()} />
  </section>
}

function Issues({ filter, onApply, search }: { filter: OutboxFilter; onApply: (next: OutboxFilter) => void; search: string }) {
  const [page, setPage] = usePageSearchParam()
  const [orderId, setOrderId] = useState(filter.orderId)
  const list = useOutboxIssues(filter, page)
  const problem = accessProblem(list.error)
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => { e.preventDefault(); onApply({ ...filter, orderId }) }}>
      <label>주문 번호 <input inputMode="numeric" value={orderId} onChange={(e) => setOrderId(e.target.value.replace(/[^0-9]/g, ''))} /></label>
      <button className={styles.button} type="submit">조회</button>
      {/* 상태 탭(status)은 유지하고 주문 번호·page만 지운다. */}
      <button type="button" className={`${styles.button} ${styles.secondary}`} disabled={!canResetFilter(new URLSearchParams(search), ['orderId'], [orderId])}
        onClick={() => { setOrderId(''); onApply({ ...filter, orderId: '' }) }}>초기화</button>
    </form>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="알림 처리 상태를 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="알림 처리 상태를 불러오고 있어요" />
          : <>
            <p className={styles.count}>총 {list.data.totalElements}건</p>
            {list.data.items.length === 0
              ? <p className={styles.empty}>{filter.status === 'FAILED' ? '처리가 중단된 알림이 없어요.' : '재시도를 기다리는 알림이 없어요.'}</p>
              : <ul className={styles.list}>
                {list.data.items.map((item) => <li key={item.id}>
                  <strong>{item.aggregateType === 'ORDER'
                    ? <Link to={`/admin/finance/orders/${item.aggregateId}`} state={{ from: `/admin/outbox?${search}` }}>주문 #{item.aggregateId}</Link>
                    : `${item.aggregateType} #${item.aggregateId}`} · {codeLabel(outboxTypeLabels, item.type)}</strong>
                  <p><StatusBadge tone={toneOf(outboxTones, item.status)}>{item.status === 'FAILED' ? '처리 중단' : '재시도 대기'}</StatusBadge>
                    {outboxErrorLabel(item.lastErrorCode)} · 시도 {item.attemptCount}회</p>
                  <small>이벤트 #{item.id} · 생성 {formatDateTime(item.createdAt)}{item.nextAttemptAt && <> · 다음 시도 {formatDateTime(item.nextAttemptAt)}</>}</small>
                </li>)}
              </ul>}
            <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="알림 처리 상태 페이지" />
          </>}
  </>
}
