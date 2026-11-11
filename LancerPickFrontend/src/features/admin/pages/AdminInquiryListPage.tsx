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
    {data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 문의가 없어요.</p> : <ul className={styles.list}>
      {data.items.map((item) => <li key={item.id}>
        <Link to={`/admin/inquiries/${item.id}`} state={{ from: `/admin/inquiries${search ? `?${search}` : ''}` }}>{item.subject}</Link>
        <p>
          <span className={item.status === 'CLOSED' ? styles.visible : styles.hidden}>{inquiryStatusLabel(item.status)}</span>
          <span className={styles.badge}>{inquiryTypeLabel(item.type)}</span>
          {item.orderId && <>주문 #{item.orderId} · </>}신청 {item.requester.displayName ?? `회원 #${item.requester.id}`}
        </p>
        <small>접수 {formatDateTime(item.createdAt)}{item.resolvedAt && <> · 처리 {formatDateTime(item.resolvedAt)}</>}</small>
      </li>)}
    </ul>}
    {data.items.length === 0 && page > 0 && <button className={styles.secondary} disabled={list.isFetching} onClick={() => onPage(0)}>첫 페이지로</button>}
    <nav className={styles.pager} aria-label="문의 목록 페이지">
      <button disabled={list.isFetching || page === 0} onClick={() => onPage(page - 1)}>이전</button>
      <span>{page + 1}페이지</span>
      <button disabled={list.isFetching || page + 1 >= data.totalPages} onClick={() => onPage(page + 1)}>다음</button>
    </nav>
  </>
}
