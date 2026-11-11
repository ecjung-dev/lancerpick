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
    <FilterForm key={params.toString()} filter={filter} onApply={applyFilter} />
    {tooLong ? <p className={styles.fieldError} role="alert">{tooLong}</p>
      : <ProductTable filter={filter} page={page} onPage={setPage} search={params.toString()} />}
  </section>
}

function FilterForm({ filter, onApply }: { filter: AdminProductFilter; onApply: (next: AdminProductFilter) => void }) {
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
    <button type="submit">검색</button>
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
    {data.items.length === 0 ? <p className={styles.empty}>조건에 맞는 상품이 없어요.</p> : <ul className={styles.list}>
      {data.items.map((item) => <li key={item.id}>
        <Link to={`/admin/products/${item.id}`} state={{ from: `/admin/products${search ? `?${search}` : ''}` }}>{item.title}</Link>
        <p>
          <span className={item.isHidden ? styles.hidden : styles.visible}>{item.isHidden ? '숨김' : '공개'}</span>
          <span className={styles.badge}>{saleStatusLabel(item.saleStatus)}</span>
          {item.category.name} · {item.seller.displayName} · {formatWon(item.priceAmount)}
        </p>
        <small>등록 {formatDateTime(item.createdAt)} · 버전 {item.version}</small>
      </li>)}
    </ul>}
    {data.items.length === 0 && page > 0 && <button className={styles.secondary} disabled={list.isFetching} onClick={() => onPage(0)}>첫 페이지로</button>}
    <nav className={styles.pager} aria-label="상품 목록 페이지">
      <button disabled={list.isFetching || page === 0} onClick={() => onPage(page - 1)}>이전</button>
      <span>{page + 1}페이지</span>
      <button disabled={list.isFetching || page + 1 >= data.totalPages} onClick={() => onPage(page + 1)}>다음</button>
    </nav>
  </>
}
