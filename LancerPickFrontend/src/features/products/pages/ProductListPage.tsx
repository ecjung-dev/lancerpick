import { Link, useNavigate, useSearchParams } from 'react-router'
import { useState } from 'react'
import { useCategories, useProducts } from '../queries'
import { ProductCard } from '../components/ProductCard'
import { CategoryIcon } from '../components/CategoryIcon'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Icon } from '../../../shared/components/Icon'
import styles from './ProductListPage.module.css'

const filterKeys = ['minPrice', 'maxPrice', 'maxDurationDays', 'sort'] as const

function SearchFilters({ params, apply, reset }: {
  params: URLSearchParams
  apply: (values: FormData) => void
  reset: () => void
}) {
  const [error, setError] = useState('')
  return (
    <form className={styles.filters} aria-label="상품 검색 조건" onSubmit={(event) => {
      event.preventDefault()
      const values = new FormData(event.currentTarget)
      const min = Number(values.get('minPrice'))
      const max = Number(values.get('maxPrice'))
      if (min > 1_000_000 || max > 1_000_000 || Number(values.get('maxDurationDays')) > 30
        || filterKeys.slice(0, 3).some((key) => values.get(key) !== '' && Number(values.get(key)) < 1)
        || (max > 0 && min > max)) {
        setError('가격은 1~1,000,000원, 작업 기간은 1~30일로 입력해 주세요. 최저가는 최고가 이하여야 해요.')
        return
      }
      setError('')
      apply(values)
    }}>
      <div className={styles.filterHeading}><div><h3>조회 조건</h3><p>원하는 가격과 작업 기간으로 서비스를 좁혀보세요.</p></div><span>SEARCH FILTERS</span></div>
      <label className={styles.keywordField}>검색어
        <span className={styles.filterSearch}><Icon name="search" size={18} /><input name="keyword" type="search" placeholder="어떤 서비스를 찾으세요?" defaultValue={params.get('keyword') ?? ''} /></span>
      </label>
      <div className={styles.filterGrid}>
        <fieldset className={styles.priceRange}><legend>가격 범위</legend><div>
          <label><span className={styles.srOnly}>최저가 (원)</span><span className={styles.filterUnit}><input name="minPrice" inputMode="numeric" pattern="[0-9]{1,7}" placeholder="최저가" defaultValue={params.get('minPrice') ?? ''} /><span aria-hidden="true">원</span></span></label>
          <span aria-hidden="true" className={styles.rangeDash}>~</span>
          <label><span className={styles.srOnly}>최고가 (원)</span><span className={styles.filterUnit}><input name="maxPrice" inputMode="numeric" pattern="[0-9]{1,7}" placeholder="최고가" defaultValue={params.get('maxPrice') ?? ''} /><span aria-hidden="true">원</span></span></label>
        </div></fieldset>
        <label>최대 작업 기간 (일)
          <span className={styles.filterUnit}><input name="maxDurationDays" inputMode="numeric" pattern="[0-9]{1,7}" placeholder="기간 제한 없음" defaultValue={params.get('maxDurationDays') ?? ''} /><span aria-hidden="true">일</span></span>
        </label>
        <label>정렬
          <select name="sort" aria-label="서비스 정렬" defaultValue={params.get('sort') ?? 'NEWEST'}>
            <option value="NEWEST">최신순</option>
            <option value="PRICE_ASC">낮은 가격순</option>
            <option value="PRICE_DESC">높은 가격순</option>
          </select>
        </label>
      </div>
      <div className={styles.filterFooter}><p>가격 1~1,000,000원 · 작업 기간 1~30일</p><div className={styles.filterActions}>
        <Button variant="secondary" onClick={(event) => { event.currentTarget.form?.reset(); setError(''); reset() }}><Icon name="refresh" size={15} />초기화</Button>
        <Button type="submit"><Icon name="search" size={16} />적용</Button>
      </div></div>
      {error && <p className={styles.filterError} role="alert">{error}</p>}
    </form>
  )
}

export function ProductListPage({ view = 'home' }: { view?: 'home' | 'catalog' }) {
  const catalog = view === 'catalog'
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const keyword = params.get('keyword') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const pageInput = Number(params.get('page') ?? 0)
  const page = Number.isInteger(pageInput) && pageInput >= 0 && pageInput <= 2147483647 ? pageInput : 0
  const categories = useCategories()
  const filters = {
    minPrice: params.get('minPrice') ?? undefined,
    maxPrice: params.get('maxPrice') ?? undefined,
    maxDurationDays: params.get('maxDurationDays') ?? undefined,
    sort: params.get('sort') ?? undefined,
  }
  const hasFilters = filterKeys.some((key) => params.has(key))
  const products = useProducts({ keyword, categoryId, page, size: catalog ? 12 : 4, ...filters })
  const outOfRange = products.data !== undefined && page > 0 && page >= products.data.totalPages

  function changeFilter(key: string, value: string) {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set(key, value)
      else next.delete(key)
      if (key !== 'page') next.delete('page')
      return next
    })
  }

  return <>
    {!catalog && <section className={styles.hero} aria-labelledby="hero-title">
      <div className={styles.heroCopy}>
        <div className={styles.eyebrow}><span /> YOUR WORK, OUR PICK</div>
        <h1 id="hero-title">필요한 서비스를 찾고,<br /><span>완료까지 한곳에서.</span></h1>
        <p>당신의 아이디어에 딱 맞는 전문가.<br />명확한 조건으로 시작하고, 결과까지 함께하세요.</p>
        <Link className={styles.cta} to="/products">서비스 둘러보기 <Icon name="arrow" size={18} /></Link>
        <div className={styles.heroTags}><span>고정 가격</span><i /><span>주문 조건 보관</span><i /><span>거래 이력 관리</span></div>
      </div>
      <div className={styles.illustration} aria-hidden="true">
        <div className={styles.orbit} /><div className={styles.gridShape} />
        <div className={styles.window}>
          <div className={styles.windowBar}><span /><span /><span /><i /></div>
          <div className={styles.windowBody}>
            <span className={styles.windowLabel}>YOUR NEXT PROJECT</span>
            <div className={styles.windowHeading}>생각이<br />결과가 되는 곳<span>↗</span></div>
            <div className={styles.windowLines}><i /><i /></div>
            <div className={styles.windowBottom}><span>LET'S MAKE IT HAPPEN</span><Icon name="arrow" size={23} /></div>
          </div>
        </div>
        <div className={styles.codeTile}><Icon name="code" size={31} /></div>
        <div className={styles.designTile}><Icon name="design" size={28} /></div>
        <div className={styles.doneTile}><Icon name="check" size={23} /><span>좋은 결과의 시작</span></div>
        <span className={styles.spark}>✳</span>
      </div>
    </section>}
    {catalog && <header className={styles.catalogHeading}><p>SERVICES</p><h1>서비스 둘러보기</h1><p>가격과 작업 기간을 비교하고, 나에게 맞는 서비스를 찾아보세요.</p></header>}
    <section className={styles.categories} aria-label="서비스 카테고리">
      {categories.isPending ? <AsyncState title="카테고리를 불러오고 있습니다." />
        : categories.isError ? <AsyncState title="카테고리를 불러오지 못했습니다." error={categories.error} onRetry={() => void categories.refetch()} />
        : <div className={styles.categoryGrid}>
          <button type="button" className={categoryId === '' ? styles.activeCategory : ''} aria-pressed={categoryId === ''} onClick={() => changeFilter('categoryId', '')}><span className={styles.allIcon}>＋</span><span>전체 서비스</span></button>
          {categories.data.map((category) => <button type="button" key={category.id} className={categoryId === category.id ? styles.activeCategory : ''} aria-pressed={categoryId === category.id} onClick={() => changeFilter('categoryId', category.id)}><CategoryIcon name={category.name} size={26} /><span>{category.name}</span></button>)}
        </div>}
    </section>
    <section className={styles.catalog} id="services" aria-labelledby="services-title" aria-busy={products.isFetching}>
      <div className={styles.sectionHeading}>
        <div><div className={styles.sectionEyebrow}>FIND YOUR NEXT EXPERT</div><h2 id="services-title">{keyword ? `'${keyword}' 검색 결과` : categoryId ? '카테고리 서비스' : hasFilters ? '서비스 검색 결과' : '새로 등록된 서비스'}</h2></div>
        {!catalog && <Link to="/products">전체 보기 ↗</Link>}
        <span className={styles.total}>{products.data ? `${products.data.totalElements}개의 서비스` : '서비스 탐색'}</span>
      </div>
      <SearchFilters key={`${keyword}|${JSON.stringify(filters)}`} params={params} reset={() => setParams({})} apply={(values) => {
        const next = new URLSearchParams(params)
        const term = String(values.get('keyword') ?? '').trim()
        if (term) next.set('keyword', term)
        else next.delete('keyword')
        for (const key of filterKeys) {
          const value = String(values.get(key) ?? '')
          if (value && value !== 'NEWEST') next.set(key, value)
          else next.delete(key)
        }
        next.delete('page')
        setParams(next, { preventScrollReset: true })
      }} />
      {products.isPending ? <AsyncState title="서비스를 불러오고 있습니다." description="잠시만 기다려 주세요." />
        : products.isError ? <AsyncState title="서비스를 불러오지 못했습니다." error={products.error} onRetry={() => void products.refetch()} />
        : products.data.items.length === 0 ? <AsyncState title={outOfRange ? '요청한 페이지에 서비스가 없습니다.' : '조건에 맞는 서비스가 없습니다.'} description={outOfRange ? '첫 페이지로 돌아가 서비스를 확인해 보세요.' : '검색어나 카테고리, 가격·기간 조건을 변경해 보세요.'} />
        : <div className={styles.products}>{products.data.items.map((product) => <ProductCard key={product.id} product={product} onSelect={() => navigate(`/products/${product.id}`, { state: { from: `${catalog ? '/products' : '/'}${params.size ? `?${params.toString()}` : ''}` } })} />)}</div>}
      {!products.isError && products.data && (products.data.totalPages > 1 || page > 0) && <nav className={styles.pagination} aria-label="서비스 페이지">
        <Button variant="secondary" disabled={page === 0 || products.isFetching} onClick={() => changeFilter('page', String(page - 1))}>이전</Button>
        <span aria-live="polite">총 {products.data.totalPages}페이지 · 현재 요청 {page + 1}페이지</span>
        <Button variant="secondary" disabled={page + 1 >= products.data.totalPages || products.isFetching} onClick={() => changeFilter('page', String(page + 1))}>다음</Button>
      </nav>}
      {outOfRange && <Button variant="secondary" onClick={() => changeFilter('page', '')}>첫 페이지로</Button>}
    </section>
    {!catalog && <section className={styles.guide} aria-labelledby="guide-title">
      <div><span className={styles.sectionEyebrow}>HOW IT WORKS</span><h2 id="guide-title">시작부터 완료까지,<br />흐름은 간단하게.</h2></div>
      <ol>{[
        ['01', '서비스 선택', '가격과 작업 범위를 확인하세요.'],
        ['02', '요구사항 제출', '필요한 내용을 자세히 전해 주세요.'],
        ['03', '판매자 수락·결제', '수락 후 적립금으로 결제하세요.'],
        ['04', '납품·구매 확정', '결과물을 확인하고 거래를 완료하세요.'],
      ].map(([number, title, description]) => <li key={number}><span>{number}</span><strong>{title}</strong><p>{description}</p></li>)}</ol>
    </section>}
  </>
}
