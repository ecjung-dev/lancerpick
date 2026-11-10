import { useState } from 'react'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { accessProblem } from '../access'
import { AdminAccessNotice } from '../pages/AdminAccess'
import { AdminPager } from './AdminPager'
import {
  ACTION_LABELS, STATUS_LABELS, resultLabel, targetLabel, useActionLogs, type ActionLogFilter,
} from '../reprocessApi'
import styles from '../pages/Admin.module.css'

/** K6 관리자 작업 이력(최신순). 금융 조회의 "작업 이력" 탭과 주문 금액 화면이 함께 쓴다. 조회만 한다. */
export function AdminActionLogList({ filter, empty }: { filter: ActionLogFilter; empty: string }) {
  const [page, setPage] = useState(0)
  const list = useActionLogs(filter, page)
  const problem = accessProblem(list.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  if (list.isError) return <AsyncState title="작업 이력을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
  if (!list.data) return <AsyncState title="작업 이력을 불러오고 있어요" />
  return <>
    <p className={styles.count}>총 {list.data.totalElements}건</p>
    {list.data.items.length === 0 ? <p>{empty}</p> : <ul className={styles.list}>
      {list.data.items.map((log) => <li key={log.id}>
        <strong>{ACTION_LABELS[log.action] ?? log.action} · {targetLabel(log)}</strong>
        <p><span className={log.status === 'SUCCEEDED' ? styles.badge : styles.hidden}>{STATUS_LABELS[log.status] ?? log.status}</span>
          {resultLabel(log)}</p>
        <p>사유: {log.reason}</p>
        <small>요청 #{log.id} · 관리자 #{log.adminId} · 요청 {formatDateTime(log.requestedAt)}
          {log.completedAt && <> · 결과 {formatDateTime(log.completedAt)}</>}</small>
      </li>)}
    </ul>}
    <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="작업 이력 페이지" />
  </>
}
