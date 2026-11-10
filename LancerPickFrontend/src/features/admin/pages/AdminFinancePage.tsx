import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import {
  decisionLabel, readPaymentFilter, readTopupFilter, usePayments, useRecoveryJobs, useTopups,
  type PaymentFilter, type TopupFilter,
} from '../financeApi'
import { orderStatusLabel } from '../../orders/status'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { AdminReprocessPanel } from '../components/AdminReprocessPanel'
import { AdminActionLogList } from '../components/AdminActionLogList'
import { ACTION_LABELS, type ReprocessAction } from '../reprocessApi'
import styles from './Admin.module.css'

type Tab = 'topups' | 'payments' | 'jobs' | 'logs'
const TABS: { key: Tab; label: string }[] = [
  { key: 'payments', label: '결제·환불' }, { key: 'topups', label: '충전' }, { key: 'jobs', label: '충전 복구 작업' },
  { key: 'logs', label: '관리자 작업 이력' },
]
const ACTIONS = Object.keys(ACTION_LABELS) as ReprocessAction[]
const time = (value: string | null) => value ? formatDateTime(value) : '—'

/** /admin/finance. 탭·필터·페이지를 URL에 두어 새로고침·뒤로가기·주문 금액 화면에서 돌아오기 때 그대로 복원한다. 조회만 한다. */
export function AdminFinancePage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('tab')
  const tab: Tab = raw === 'topups' || raw === 'jobs' || raw === 'logs' ? raw : 'payments'

  function apply(next: Record<string, string>) {
    // 조건이 바뀌면 첫 페이지부터. 빈 조건은 주소에 남기지 않는다.
    const query = new URLSearchParams({ tab: next.tab ?? tab })
    for (const [key, value] of Object.entries(next)) if (value && key !== 'tab') query.set(key, value)
    setParams(query)
  }

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>금융 조회</h1>
    <p className={styles.lead}>충전·결제·보관금·정산과 충전 복구 작업을 조회해요. "재처리 후보"인 충전·복구 작업은 사유를 적고 재확인·재시작을 요청할 수 있어요. 실행 직전에 서버가 조건을 다시 확인하고, 모든 요청은 관리자 작업 이력에 남아요.</p>
    <nav className={styles.tabs} aria-label="금융 조회 종류">
      {TABS.map((item) => <Link key={item.key} to={`/admin/finance?tab=${item.key}`} aria-current={tab === item.key ? 'page' : undefined}>{item.label}</Link>)}
    </nav>
    {tab === 'payments' && <Payments key={params.toString()} filter={readPaymentFilter(params)} onApply={(f) => apply({ tab, ...f })} search={params.toString()} />}
    {tab === 'topups' && <Topups key={params.toString()} filter={readTopupFilter(params)} onApply={(f) => apply({ tab, ...f })} />}
    {tab === 'jobs' && <Jobs />}
    {tab === 'logs' && <Logs key={params.toString()} action={readAction(params)} onApply={(action) => apply({ tab, action })} />}
  </section>
}

/** 관리자 조회 목록 공통 이전/다음 페이지. 알림 처리 화면도 같이 쓴다. */
export function Pager({ page, totalPages, busy, onPage, label }: { page: number; totalPages: number; busy: boolean; onPage: (page: number) => void; label: string }) {
  return <nav className={styles.pager} aria-label={label}>
    <button disabled={busy || page === 0} onClick={() => onPage(page - 1)}>이전</button>
    <span>{page + 1}페이지</span>
    <button disabled={busy || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</button>
  </nav>
}

function Payments({ filter, onApply, search }: { filter: PaymentFilter; onApply: (next: PaymentFilter) => void; search: string }) {
  const [page, setPage] = usePageSearchParam()
  const [draft, setDraft] = useState(filter)
  const list = usePayments(filter, page)
  const problem = accessProblem(list.error)
  const idField = (key: 'orderId' | 'buyerId' | 'sellerId', label: string) =>
    <label>{label} <input inputMode="numeric" value={draft[key]} onChange={(e) => setDraft({ ...draft, [key]: e.target.value.replace(/[^0-9]/g, '') })} /></label>
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => { e.preventDefault(); onApply(draft) }}>
      {idField('orderId', '주문 번호')}{idField('buyerId', '구매자 번호')}{idField('sellerId', '판매자 번호')}
      <label>상태 <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as PaymentFilter['status'] })}>
        <option value="">전체</option><option value="PAID">결제</option><option value="REFUNDED">환불</option>
      </select></label>
      <label>정책 <select value={draft.policyVersion} onChange={(e) => setDraft({ ...draft, policyVersion: e.target.value as PaymentFilter['policyVersion'] })}>
        <option value="">전체</option><option value="CONTRACT_V3">V3(전액 보관)</option><option value="CONTRACT_V2">V2</option><option value="LEGACY_V1">기존</option>
      </select></label>
      <button type="submit">조회</button>
    </form>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="결제 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="결제 목록을 불러오고 있어요" />
          : <>
            <p className={styles.count}>총 {list.data.totalElements}건</p>
            {list.data.items.length === 0 ? <p>조건에 맞는 결제가 없어요.</p> : <ul className={styles.list}>
              {list.data.items.map((item) => <li key={item.id}>
                <Link to={`/admin/finance/orders/${item.orderId}`} state={{ from: `/admin/finance?${search}` }}>주문 #{item.orderId}</Link>
                <p><span className={styles.badge}>{item.status === 'PAID' ? '결제' : item.status === 'REFUNDED' ? '환불' : item.status}</span>
                  {formatWon(item.amount)}{item.refundedAmount > 0 && <> · 환불 {formatWon(item.refundedAmount)}</>} · {item.policyVersion} · 주문 {orderStatusLabel(item.orderStatus)}</p>
                <small>구매자 #{item.buyerId} · 판매자 #{item.sellerId} · 결제 {time(item.paidAt)}{item.refundedAt && <> · 환불 {time(item.refundedAt)}</>}</small>
              </li>)}
            </ul>}
            <Pager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="결제 목록 페이지" />
          </>}
  </>
}

function Topups({ filter, onApply }: { filter: TopupFilter; onApply: (next: TopupFilter) => void }) {
  const [page, setPage] = usePageSearchParam()
  const [memberId, setMemberId] = useState(filter.memberId)
  const list = useTopups(filter, page)
  const problem = accessProblem(list.error)
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => { e.preventDefault(); onApply({ ...filter, memberId }) }}>
      <label>회원 번호 <input inputMode="numeric" value={memberId} onChange={(e) => setMemberId(e.target.value.replace(/[^0-9]/g, ''))} /></label>
      <label>상태 <select value={filter.status} onChange={(e) => onApply({ memberId, status: e.target.value as TopupFilter['status'] })}>
        <option value="">전체</option>{['READY', 'PROCESSING', 'UNKNOWN', 'COMPLETED', 'FAILED'].map((s) => <option key={s} value={s}>{s}</option>)}
      </select></label>
      <button type="submit">조회</button>
    </form>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="충전 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="충전 목록을 불러오고 있어요" />
          : <>
            <p className={styles.count}>총 {list.data.totalElements}건</p>
            {list.data.items.length === 0 ? <p>조건에 맞는 충전이 없어요.</p> : <ul className={styles.list}>
              {list.data.items.map((item) => <li key={item.id}>
                <strong>충전 #{item.id}</strong>
                <p><span className={item.status === 'UNKNOWN' || item.status === 'FAILED' ? styles.hidden : styles.badge}>{item.status}</span>
                  {formatWon(item.amount)} · 회원 #{item.memberId} · 복구 시도 {item.recoveryAttemptCount}회{item.lastErrorCode && <> · 오류 {item.lastErrorCode}</>}</p>
                <small>신청 {time(item.createdAt)} · 처리 시작 {time(item.processingStartedAt)} · 다음 복구 {time(item.nextRecoveryAt)} · 처리권 만료 {time(item.recoveryLeaseUntil)} · 완료 {time(item.completedAt)}</small>
                <p>자동 복구: {decisionLabel(item.automaticRecovery.eligible, item.automaticRecovery.reason)}</p>
                <p>관리자 재확인: {decisionLabel(item.manualRecheck.eligible, item.manualRecheck.reason)}</p>
                <AdminReprocessPanel action="TOPUP_RECHECK" target={item.id} eligible={item.manualRecheck.eligible} />
              </li>)}
            </ul>}
            <Pager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="충전 목록 페이지" />
          </>}
  </>
}

function Jobs() {
  const [page, setPage] = usePageSearchParam()
  const list = useRecoveryJobs(page)
  const problem = accessProblem(list.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  if (list.isError) return <AsyncState title="복구 작업 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
  if (!list.data) return <AsyncState title="복구 작업 목록을 불러오고 있어요" />
  return <>
    <p className={styles.count}>작업 {list.data.totalElements}개 (작업별 최신 실행만 표시)</p>
    {list.data.items.length === 0 ? <p>실행된 충전 복구 작업이 없어요.</p> : <ul className={styles.list}>
      {list.data.items.map((job) => <li key={job.instanceId}>
        <strong>작업 #{job.instanceId}{job.executionId ? ` · 실행 #${job.executionId}` : ' · 실행 기록 없음'}</strong>
        <p><span className={job.status === 'FAILED' || job.status === 'STOPPED' ? styles.hidden : styles.badge}>{job.status}</span>
          읽음 {job.readCount} · 지급 {job.creditedCount} · 제외 {job.filteredCount}
          {Object.entries(job.outcomes).map(([key, count]) => <span key={key}> · {key} {count}</span>)}</p>
        <small>원래 스캔 요청 시각 {time(job.scanRequestedAt)}</small>
        <p>재시작: {decisionLabel(job.restart.eligible, job.restart.reason)}</p>
        {job.executionId && <AdminReprocessPanel action="TOPUP_RECOVERY_RESTART" target={job.executionId} eligible={job.restart.eligible} />}
      </li>)}
    </ul>}
    <Pager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="복구 작업 페이지" />
  </>
}

/** 허용하지 않는 값은 "전체"로 바꿔 서버 400을 일으키지 않는다. */
function readAction(params: URLSearchParams): ReprocessAction | undefined {
  const value = params.get('action') ?? ''
  return (ACTIONS as string[]).includes(value) ? (value as ReprocessAction) : undefined
}

function Logs({ action, onApply }: { action?: ReprocessAction; onApply: (action: string) => void }) {
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => e.preventDefault()}>
      <label>작업 종류 <select value={action ?? ''} onChange={(e) => onApply(e.target.value)}>
        <option value="">전체</option>{ACTIONS.map((a) => <option key={a} value={a}>{ACTION_LABELS[a]}</option>)}
      </select></label>
    </form>
    <AdminActionLogList filter={{ action }} empty="관리자 작업 이력이 없어요." />
  </>
}
