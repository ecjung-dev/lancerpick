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
import { MemberLink } from '../components/MemberLink'
import { AdminPager } from '../components/AdminPager'
import { ACTION_LABELS, type ReprocessAction } from '../reprocessApi'
import { canResetFilter } from '../filterReset'
import { codeLabel, jobOutcomeLabels, jobStatusLabels, plainLabel, policyLabels, topupStatusLabels } from '../statusLabels'
import styles from './Admin.module.css'

type Tab = 'topups' | 'payments' | 'jobs' | 'logs'
const TABS: { key: Tab; label: string }[] = [
  { key: 'payments', label: '결제·환불' }, { key: 'topups', label: '충전' }, { key: 'jobs', label: '충전 복구 작업' },
  { key: 'logs', label: '관리자 작업 이력' },
]
const ACTIONS = Object.keys(ACTION_LABELS) as ReprocessAction[]
const time = (value: string | null) => value ? formatDateTime(value) : '—'
/** 초기화 대상 주소 키. tab은 넣지 않아 초기화해도 같은 탭에 머문다. */
const PAYMENT_KEYS = ['orderId', 'buyerId', 'sellerId', 'status', 'policyVersion'] as const
const EMPTY_PAYMENT: PaymentFilter = { orderId: '', buyerId: '', sellerId: '', status: '', policyVersion: '' }
const TOPUP_KEYS = ['memberId', 'status'] as const

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
    <nav className={styles.subtabs} aria-label="금융 조회 종류">
      {TABS.map((item) => <Link key={item.key} to={`/admin/finance?tab=${item.key}`} aria-current={tab === item.key ? 'page' : undefined}>{item.label}</Link>)}
    </nav>
    {tab === 'payments' && <Payments key={params.toString()} filter={readPaymentFilter(params)} onApply={(f) => apply({ tab, ...f })} search={params.toString()} />}
    {tab === 'topups' && <Topups key={params.toString()} filter={readTopupFilter(params)} onApply={(f) => apply({ tab, ...f })} search={params.toString()} />}
    {tab === 'jobs' && <Jobs />}
    {tab === 'logs' && <Logs key={params.toString()} action={readAction(params)} onApply={(action) => apply({ tab, action })} search={params.toString()} />}
  </section>
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
        <option value="">전체</option>{(["CONTRACT_V3", "CONTRACT_V2", "LEGACY_V1"] as const).map((p) => <option key={p} value={p}>{plainLabel(policyLabels, p)}</option>)}
      </select></label>
      <button type="submit">조회</button>
      <button type="button" className={styles.secondary} disabled={!canResetFilter(new URLSearchParams(search), PAYMENT_KEYS, Object.values(draft))}
        onClick={() => { setDraft(EMPTY_PAYMENT); onApply(EMPTY_PAYMENT) }}>초기화</button>
    </form>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="결제 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="결제 목록을 불러오고 있어요" />
          : <>
            <p className={styles.count}>총 {list.data.totalElements}건</p>
            {list.data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 결제가 없어요.</p> : <div className={styles.tableWrap}><table className={styles.table}>
              <thead><tr>
                <th scope="col">주문</th><th scope="col">상태</th><th scope="col">정책</th><th scope="col">주문 상태</th>
                <th scope="col">구매자</th><th scope="col">판매자</th><th scope="col" className={styles.cellNum}>금액</th><th scope="col">결제일</th>
              </tr></thead>
              <tbody>{list.data.items.map((item) => <tr key={item.id}>
                <td data-label="주문"><Link className={styles.cellLink} to={`/admin/finance/orders/${item.orderId}`} state={{ from: `/admin/finance?${search}` }}>#{item.orderId}</Link></td>
                {/* 환불만 배지로 두고 결제는 보통 글자로 둔다. */}
                <td data-label="상태"><span className={item.status === 'PAID' ? styles.cellMuted : styles.toneWarn}>{item.status === 'PAID' ? '결제' : item.status === 'REFUNDED' ? '환불' : item.status}</span></td>
                <td data-label="정책">{codeLabel(policyLabels, item.policyVersion)}</td>
                <td data-label="주문 상태">{orderStatusLabel(item.orderStatus)}</td>
                <td data-label="구매자"><MemberLink id={item.buyerId} from={`/admin/finance?${search}`}><span className={styles.srOnly}>구매자 </span>#{item.buyerId}</MemberLink></td>
                <td data-label="판매자"><MemberLink id={item.sellerId} from={`/admin/finance?${search}`}><span className={styles.srOnly}>판매자 </span>#{item.sellerId}</MemberLink></td>
                <td className={styles.cellNum} data-label="금액"><strong>{formatWon(item.amount)}</strong>
                  {item.refundedAmount > 0 && <span className={styles.cellSub}>환불 {formatWon(item.refundedAmount)}</span>}</td>
                <td className={styles.cellDate} data-label="결제일">{time(item.paidAt)}
                  {item.refundedAt && <span className={styles.cellSub}>환불 {time(item.refundedAt)}</span>}</td>
              </tr>)}</tbody>
            </table></div>}
            <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="결제 목록 페이지" />
          </>}
  </>
}

function Topups({ filter, onApply, search }: { filter: TopupFilter; onApply: (next: TopupFilter) => void; search: string }) {
  const [page, setPage] = usePageSearchParam()
  const [memberId, setMemberId] = useState(filter.memberId)
  const list = useTopups(filter, page)
  const problem = accessProblem(list.error)
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => { e.preventDefault(); onApply({ ...filter, memberId }) }}>
      <label>회원 번호 <input inputMode="numeric" value={memberId} onChange={(e) => setMemberId(e.target.value.replace(/[^0-9]/g, ''))} /></label>
      <label>상태 <select value={filter.status} onChange={(e) => onApply({ memberId, status: e.target.value as TopupFilter['status'] })}>
        <option value="">전체</option>{['READY', 'PROCESSING', 'UNKNOWN', 'COMPLETED', 'FAILED'].map((s) => <option key={s} value={s}>{plainLabel(topupStatusLabels, s)}</option>)}
      </select></label>
      <button type="submit">조회</button>
      <button type="button" className={styles.secondary} disabled={!canResetFilter(new URLSearchParams(search), TOPUP_KEYS, [memberId])}
        onClick={() => { setMemberId(''); onApply({ memberId: '', status: '' }) }}>초기화</button>
    </form>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="충전 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="충전 목록을 불러오고 있어요" />
          : <>
            <p className={styles.count}>총 {list.data.totalElements}건</p>
            {list.data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 충전이 없어요.</p> : <div className={styles.tableWrap}><table className={styles.table}>
              <thead><tr>
                <th scope="col">충전</th><th scope="col">상태</th><th scope="col">회원</th><th scope="col" className={styles.cellNum}>금액</th>
                <th scope="col">복구 시도</th><th scope="col">신청일</th><th scope="col">진행 시각</th>
              </tr></thead>
              {/* 충전 한 건 = 기본 행 + 복구 판단·재처리 보조 행. tbody로 묶어 두 행을 한 덩어리로 다룬다. */}
              {list.data.items.map((item) => <tbody key={item.id}>
                <tr className={styles.hasDetail}>
                  <td className={styles.cellId} data-label="충전"><strong>#{item.id}</strong></td>
                  <td data-label="상태"><span className={item.status === 'FAILED' ? styles.toneDanger : item.status === 'UNKNOWN' || item.status === 'PROCESSING' ? styles.toneWarn : item.status === 'COMPLETED' ? styles.toneOk : styles.badge}>{codeLabel(topupStatusLabels, item.status)}</span></td>
                  <td data-label="회원"><MemberLink id={item.memberId} from={`/admin/finance?${search}`}><span className={styles.srOnly}>회원 </span>#{item.memberId}</MemberLink></td>
                  <td className={styles.cellNum} data-label="금액"><strong>{formatWon(item.amount)}</strong></td>
                  <td data-label="복구 시도">{item.recoveryAttemptCount}회{item.lastErrorCode && <span className={styles.cellSub}>오류 {item.lastErrorCode}</span>}</td>
                  <td className={styles.cellDate} data-label="신청일">{time(item.createdAt)}</td>
                  {/* 기록이 있는 시각만 보여 준다. */}
                  <td className={styles.cellDate} data-label="진행 시각">
                    {!item.processingStartedAt && !item.nextRecoveryAt && !item.recoveryLeaseUntil && !item.completedAt && '—'}
                    {item.processingStartedAt && <span className={styles.cellSub}>처리 시작 {time(item.processingStartedAt)}</span>}
                    {item.nextRecoveryAt && <span className={styles.cellSub}>다음 복구 {time(item.nextRecoveryAt)}</span>}
                    {item.recoveryLeaseUntil && <span className={styles.cellSub}>처리권 만료 {time(item.recoveryLeaseUntil)}</span>}
                    {item.completedAt && <span className={styles.cellSub}>완료 {time(item.completedAt)}</span>}
                  </td>
                </tr>
                <tr className={styles.detailRow}>
                  <td colSpan={7} data-label="">
                    <p>자동 복구: {decisionLabel(item.automaticRecovery.eligible, item.automaticRecovery.reason)} · 관리자 재확인: {decisionLabel(item.manualRecheck.eligible, item.manualRecheck.reason)}</p>
                    <AdminReprocessPanel action="TOPUP_RECHECK" target={item.id} eligible={item.manualRecheck.eligible} />
                  </td>
                </tr>
              </tbody>)}
            </table></div>}
            <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="충전 목록 페이지" />
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
    {list.data.items.length === 0 ? <p className={styles.empty}>실행된 충전 복구 작업이 없어요.</p> : <ul className={styles.list}>
      {list.data.items.map((job) => <li key={job.instanceId}>
        <strong>작업 #{job.instanceId}{job.executionId ? ` · 실행 #${job.executionId}` : ' · 실행 기록 없음'}</strong>
        <p><span className={job.status === 'FAILED' || job.status === 'STOPPED' ? styles.toneDanger : job.status === 'COMPLETED' ? styles.toneOk : styles.badge}>{codeLabel(jobStatusLabels, job.status)}</span>
          읽음 {job.readCount} · 지급 {job.creditedCount} · 제외 {job.filteredCount}</p>
        {Object.keys(job.outcomes).length > 0 && <p className={styles.help}>
          결과별 {Object.entries(job.outcomes).map(([key, count], index) => <span key={key}>{index > 0 && ' · '}{codeLabel(jobOutcomeLabels, key)} {count}</span>)}
          <br />제외는 충전 완료가 아닌 결과를 모두 세므로 결과별 집계와 겹쳐요.</p>}
        <small>원래 스캔 요청 시각 {time(job.scanRequestedAt)}</small>
        <p>재시작: {decisionLabel(job.restart.eligible, job.restart.reason)}</p>
        {job.executionId && <AdminReprocessPanel action="TOPUP_RECOVERY_RESTART" target={job.executionId} eligible={job.restart.eligible} />}
      </li>)}
    </ul>}
    <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="복구 작업 페이지" />
  </>
}

/** 허용하지 않는 값은 "전체"로 바꿔 서버 400을 일으키지 않는다. */
function readAction(params: URLSearchParams): ReprocessAction | undefined {
  const value = params.get('action') ?? ''
  return (ACTIONS as string[]).includes(value) ? (value as ReprocessAction) : undefined
}

function Logs({ action, onApply, search }: { action?: ReprocessAction; onApply: (action: string) => void; search: string }) {
  return <>
    <form className={styles.filters} role="search" onSubmit={(e: FormEvent) => e.preventDefault()}>
      <label>작업 종류 <select value={action ?? ''} onChange={(e) => onApply(e.target.value)}>
        <option value="">전체</option>{ACTIONS.map((a) => <option key={a} value={a}>{ACTION_LABELS[a]}</option>)}
      </select></label>
      <button type="button" className={styles.secondary} disabled={!canResetFilter(new URLSearchParams(search), ['action'])} onClick={() => onApply('')}>초기화</button>
    </form>
    <AdminActionLogList filter={{ action }} empty="관리자 작업 이력이 없어요." />
  </>
}
