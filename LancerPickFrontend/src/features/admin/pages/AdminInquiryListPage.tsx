import { Link, useSearchParams } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { inquiryStatusLabel } from '../../inquiries/validation'
import { inquiryTypeLabel, readInquiryFilter, useAdminInquiries } from '../inquiryQueries'
import type { AdminInquiryFilter } from '../inquiryTypes'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { AdminPager } from '../components/AdminPager'
import { canResetFilter } from '../filterReset'
import styles from './Admin.module.css'

/** /admin/inquiries. 필터·페이지를 URL에 두어 새로고침·뒤로가기·상세에서 돌아오기 때 그대로 복원한다. */
export function AdminInquiryListPage() {
  const [params, setParams] = useSearchParams()
  const [page, setPage] = usePageSearchParam()
  const filter = readInquiryFilter(params)

  function applyFilter(next: AdminInquiryFilter) {
    // 조건이 바뀌면 첫 페이지부터 다시 본다. 빈 조건은 주소에 남기지 않는다.
    const query = new URLSearchParams()
    if (next.status) query.set('status', next.status)
    if (next.type) query.set('type', next.type)
    setParams(query)
  }

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>운영 문의</h1>
    <p className={styles.lead}>판매자가 취소 요청에 응답하지 않을 때 구매자가 남긴 검토 요청이에요. 승인하면 결제 금액이 전액 반환되고, 거절하면 대금은 계속 보관돼요.</p>
    <form className={styles.filters} role="search" onSubmit={(e) => e.preventDefault()}>
      <label>상태 <select value={filter.status} onChange={(e) => applyFilter({ ...filter, status: e.target.value as AdminInquiryFilter['status'] })}>
        <option value="">전체</option><option value="OPEN">검토 대기</option><option value="IN_REVIEW">검토 중</option><option value="CLOSED">처리 완료</option>
      </select></label>
      <label>유형 <select value={filter.type} onChange={(e) => applyFilter({ ...filter, type: e.target.value as AdminInquiryFilter['type'] })}>
        <option value="">전체</option><option value="CANCELLATION_REVIEW">취소 검토</option><option value="GENERAL">일반 문의</option>
      </select></label>
      <button type="button" className={`${styles.button} ${styles.secondary}`} disabled={!canResetFilter(params, ['status', 'type'])}
        onClick={() => applyFilter({ status: '', type: '' })}>초기화</button>
    </form>
    <InquiryTable filter={filter} page={page} onPage={setPage} search={params.toString()} />
  </section>
}

function InquiryTable({ filter, page, onPage, search }: { filter: AdminInquiryFilter; page: number; onPage: (page: number) => void; search: string }) {
  const list = useAdminInquiries(filter, page)
  const problem = accessProblem(list.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  if (list.isError) return <AsyncState title="문의 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
  if (!list.data) return <AsyncState title="문의 목록을 불러오고 있어요" />
  const data = list.data
  return <>
    <p className={styles.count}>총 {data.totalElements}건</p>
    {data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 문의가 없어요.</p> : <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr>
        <th scope="col">번호</th><th scope="col">상태</th><th scope="col">유형</th><th scope="col">주문</th>
        <th scope="col">제목</th><th scope="col">신청자</th><th scope="col">접수일</th><th scope="col">처리일</th>
      </tr></thead>
      <tbody>{data.items.map((item) => <tr key={item.id}>
        <td className={styles.cellId} data-label="번호">#{item.id}</td>
        <td data-label="상태"><span className={item.status === 'CLOSED' ? styles.visible : styles.hidden}>{inquiryStatusLabel(item.status)}</span></td>
        <td data-label="유형">{inquiryTypeLabel(item.type)}</td>
        {/* 취소 검토 문의는 제목이 대부분 같아 주문 번호 열로 구분한다. */}
        <td className={styles.cellId} data-label="주문">{item.orderId ? `#${item.orderId}` : '—'}</td>
        <td data-label=""><Link className={styles.cellLink} to={`/admin/inquiries/${item.id}`} state={{ from: `/admin/inquiries${search ? `?${search}` : ''}` }}>{item.subject}</Link></td>
        <td data-label="신청자">{item.requester.displayName ?? `회원 #${item.requester.id}`}</td>
        <td className={styles.cellDate} data-label="접수일">{formatDateTime(item.createdAt)}</td>
        <td className={styles.cellDate} data-label="처리일">{item.resolvedAt ? formatDateTime(item.resolvedAt) : '—'}</td>
      </tr>)}</tbody>
    </table></div>}
    <AdminPager page={page} totalPages={data.totalPages} busy={list.isFetching} onPage={onPage} label="문의 목록 페이지" />
  </>
}
