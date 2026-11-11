import { Link } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { useAdminDashboard, type AdminDashboard } from '../dashboardApi'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import styles from './Admin.module.css'

interface Card { label: string; count: number; to: string; help: string }

/** 확인할 일이 있는 항목을 위에, 0건은 아래에 같은 순서로 둔다. */
function cards(data: AdminDashboard): Card[] {
  return [
    { label: '접수된 운영 문의', count: data.inquiries.open, to: '/admin/inquiries?status=OPEN', help: '구매자가 취소 검토를 요청했어요.' },
    { label: '검토 중인 문의', count: data.inquiries.inReview, to: '/admin/inquiries?status=IN_REVIEW', help: '담당자가 확인하고 있어요.' },
    { label: '24시간 안에 자동 확정', count: data.reviewTimers.expiringWithin24Hours, to: '/admin/review-timers', help: `진행 중 타이머 ${data.reviewTimers.active}건 중 곧 끝나는 것(이미 지났지만 처리 전 포함)이에요.` },
    { label: '처리가 멈춘 알림', count: data.notifications.failed, to: '/admin/outbox?status=FAILED', help: '자동 재시도를 멈췄어요. 거래는 이미 끝났고 알림만 빠졌어요.' },
    { label: '재시도 대기 알림', count: data.notifications.retrying, to: '/admin/outbox?status=RETRYING', help: '실패 후 다시 시도할 예정이에요.' },
    { label: '결과 확인이 필요한 충전', count: data.topups.unknown, to: '/admin/finance?tab=topups&status=UNKNOWN', help: '결제 결과를 확정하지 못했어요(UNKNOWN).' },
  ].sort((a, b) => Number(b.count > 0) - Number(a.count > 0))
}

/** /admin. 관리자 운영 현황(K3). 지금 확인할 일의 건수만 모아 보여 주고, 누르면 해당 관리자 화면으로 간다. 조회만 한다. */
export function AdminDashboardPage() {
  const summary = useAdminDashboard()
  const problem = accessProblem(summary.error)
  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>운영 현황</h1>
    <p className={styles.lead}>지금 확인할 일의 건수예요. 숫자를 누르면 해당 화면으로 이동해요. 각 숫자는 조회한 순간의 값이에요.</p>
    {problem ? <AdminAccessNotice problem={problem} />
      : !summary.data && summary.isError ? <AsyncState title="운영 현황을 불러오지 못했어요" error={summary.error} onRetry={() => { void summary.refetch() }} />
        : !summary.data ? <AsyncState title="운영 현황을 불러오고 있어요" />
          : <>
            {summary.isError && <p className={styles.notice} role="alert">최신 현황을 불러오지 못했어요. 아래는 마지막으로 확인한 값이에요.</p>}
            {cards(summary.data).every((card) => card.count === 0) && <p className={styles.allClear} role="status">
              집계된 확인 대상이 없어요. 아래 여섯 항목이 모두 0건이에요(조회 시각 기준).</p>}
            <ul className={styles.cards}>
              {cards(summary.data).map((card) => <li key={card.to + card.label} className={card.count > 0 ? styles.attention : undefined}>
                <Link to={card.to}>
                  <span>{card.label}</span>
                  <strong>{card.count.toLocaleString()}건</strong>
                </Link>
                <small>{card.help}</small>
              </li>)}
            </ul>
            <div className={styles.actions}>
              <small className={styles.count}>확인 {formatDateTime(summary.data.checkedAt)}</small>
              <button type="button" className={styles.secondary} disabled={summary.isFetching} onClick={() => { void summary.refetch() }}>{summary.isFetching ? '확인 중…' : '다시 확인'}</button>
            </div>
          </>}
  </section>
}
