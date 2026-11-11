import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { normalizeText } from '../../../shared/lib/text'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { useAdminProducts } from '../queries'
import type { AdminProductFilter } from '../types'
import { keywordError, readFilter, saleStatusLabel } from '../validation'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { AdminPager } from '../components/AdminPager'
import { canResetFilter } from '../filterReset'
import styles from './Admin.module.css'

/** /admin/products. 필터·페이지를 URL에 두어 새로고침·뒤로가기·상세에서 돌아오기 때 그대로 복원한다. */
export function AdminProductListPage() {
  const [params, setParams] = useSearchParams()
  const [page, setPage] = usePageSearchParam()
  const filter = readFilter(params)
  const tooLong = keywordError(filter.keyword)

  function applyFilter(next: AdminProductFilter) {
    // 조건이 바뀌면 첫 페이지부터 다시 본다. 빈 조건은 주소에 남기지 않는다.
    const query = new URLSearchParams()
    if (next.keyword) query.set('keyword', next.keyword)
    if (next.isHidden) query.set('isHidden', next.isHidden)
    if (next.saleStatus) query.set('saleStatus', next.saleStatus)
    setParams(query)
  }

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>상품 운영</h1>
    <p className={styles.lead}>숨김은 판매 상태와 별개예요. 숨긴 상품은 공개 목록과 신규 주문에서 빠지고, 기존 거래는 그대로 유지돼요.</p>
    <FilterForm key={params.toString()} filter={filter} onApply={applyFilter} params={params} />
    {tooLong ? <p className={styles.fieldError} role="alert">{tooLong}</p>
      : <ProductTable filter={filter} page={page} onPage={setPage} search={params.toString()} />}
  </section>
}

function FilterForm({ filter, onApply, params }: { filter: AdminProductFilter; onApply: (next: AdminProductFilter) => void; params: URLSearchParams }) {
  const [keyword, setKeyword] = useState(filter.keyword)
  function submit(event: FormEvent) {
    event.preventDefault()
    onApply({ ...filter, keyword: normalizeText(keyword) })
  }
  // 선택 상자를 바꿀 때도 입력해 둔 검색어를 함께 적용한다(입력값이 사라지지 않게).
  const typed = () => normalizeText(keyword)
  return <form className={styles.filters} role="search" onSubmit={submit}>
    <label>검색어 <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="제목 또는 설명" /></label>
    <label>노출 <select value={filter.isHidden} onChange={(e) => onApply({ ...filter, keyword: typed(), isHidden: e.target.value as AdminProductFilter['isHidden'] })}>
      <option value="">전체</option><option value="false">공개</option><option value="true">숨김</option>
    </select></label>
    <label>판매 상태 <select value={filter.saleStatus} onChange={(e) => onApply({ ...filter, keyword: typed(), saleStatus: e.target.value as AdminProductFilter['saleStatus'] })}>
      <option value="">전체</option><option value="ON_SALE">판매 중</option><option value="STOPPED">판매 중지</option>
    </select></label>
    <button className={styles.button} type="submit">검색</button>
    <button type="button" className={`${styles.button} ${styles.secondary}`} disabled={!canResetFilter(params, ['keyword', 'isHidden', 'saleStatus'], [keyword])}
      onClick={() => { setKeyword(''); onApply({ keyword: '', isHidden: '', saleStatus: '' }) }}>초기화</button>
  </form>
}

function ProductTable({ filter, page, onPage, search }: { filter: AdminProductFilter; page: number; onPage: (page: number) => void; search: string }) {
  const list = useAdminProducts(filter, page)
  const problem = accessProblem(list.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  if (list.isError) return <AsyncState title="상품 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
  if (!list.data) return <AsyncState title="상품 목록을 불러오고 있어요" />
  const data = list.data
  return <>
    <p className={styles.count}>총 {data.totalElements}개</p>
    {data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 상품이 없어요.</p> : <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr>
        <th scope="col">번호</th><th scope="col">상품명</th><th scope="col">카테고리</th><th scope="col">판매자</th>
        <th scope="col">노출</th><th scope="col">판매 상태</th><th scope="col" className={styles.cellNum}>가격</th><th scope="col">등록일</th>
      </tr></thead>
      <tbody>{data.items.map((item) => <tr key={item.id}>
        <td className={styles.cellId} data-label="번호">#{item.id}</td>
        <td data-label=""><Link className={styles.cellLink} to={`/admin/products/${item.id}`} state={{ from: `/admin/products${search ? `?${search}` : ''}` }}>{item.title}</Link></td>
        <td data-label="카테고리">{item.category.name}</td>
        <td data-label="판매자">{item.seller.displayName}</td>
        {/* 숨김·판매 중지만 배지로 두고 공개·판매 중은 보통 글자로 낮춰, 운영자가 확인할 상품이 눈에 띄게 한다. */}
        <td data-label="노출"><span className={item.isHidden ? styles.hidden : styles.cellMuted}>{item.isHidden ? '숨김' : '공개'}</span></td>
        <td data-label="판매 상태"><span className={item.saleStatus === 'ON_SALE' ? styles.cellMuted : styles.toneWarn}>{saleStatusLabel(item.saleStatus)}</span></td>
        <td className={styles.cellNum} data-label="가격"><strong>{formatWon(item.priceAmount)}</strong></td>
        <td className={styles.cellDate} data-label="등록일">{formatDateTime(item.createdAt)}</td>
      </tr>)}</tbody>
    </table></div>}
    <AdminPager page={page} totalPages={data.totalPages} busy={list.isFetching} onPage={onPage} label="상품 목록 페이지" />
  </>
}
