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
import { StatusBadge } from './StatusBadge'
import { actionTones, toneOf } from '../statusTone'

/** K6 관리자 작업 이력(최신순). 금융 조회의 "작업 이력" 탭과 주문 금액 화면이 함께 쓴다. 조회만 한다. */
export function AdminActionLogList({ filter, empty }: { filter: ActionLogFilter; empty: string }) {
  const [page, setPage] = useState(0)
  const list = useActionLogs(filter, page)
  const problem = accessProblem(list.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  if (list.isError) return <AsyncState title="작업 이력을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
  if (!list.data) return <AsyncState title="작업 이력을 불러오는 중이에요" />
  return <>
    <p className={styles.count}>총 {list.data.totalElements}건</p>
    {list.data.items.length === 0 ? <p className={styles.empty}>{empty}</p> : <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr>
        <th scope="col">요청</th><th scope="col">작업</th><th scope="col">대상</th><th scope="col">상태</th>
        <th scope="col">결과</th><th scope="col">관리자</th><th scope="col">요청일</th><th scope="col">결과 시각</th>
      </tr></thead>
      {/* 작업 한 건 = 기본 행 + 사유 보조 행. */}
      {list.data.items.map((log) => <tbody key={log.id}>
        <tr className={styles.hasDetail}>
          <td className={styles.cellId} data-label="요청">#{log.id}</td>
          <td data-label="작업"><strong>{ACTION_LABELS[log.action] ?? log.action}</strong></td>
          <td data-label="대상">{targetLabel(log)}</td>
          <td data-label="상태"><StatusBadge tone={toneOf(actionTones, log.status)}>{STATUS_LABELS[log.status] ?? log.status}</StatusBadge></td>
          <td data-label="결과">{resultLabel(log)}</td>
          <td className={styles.cellId} data-label="관리자">#{log.adminId}</td>
          <td className={styles.cellDate} data-label="요청일">{formatDateTime(log.requestedAt)}</td>
          <td className={styles.cellDate} data-label="결과 시각">{log.completedAt ? formatDateTime(log.completedAt) : '—'}</td>
        </tr>
        <tr className={styles.detailRow}><td colSpan={8} data-label=""><p className={styles.text}>사유: {log.reason}</p></td></tr>
      </tbody>)}
    </table></div>}
    <AdminPager page={page} totalPages={list.data.totalPages} busy={list.isFetching} onPage={setPage} label="작업 이력 페이지" />
  </>
}
