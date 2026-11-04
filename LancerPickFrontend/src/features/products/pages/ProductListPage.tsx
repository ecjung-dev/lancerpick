import { useNavigate, useSearchParams } from 'react-router'
import { useCategories, useProducts } from '../queries'
import { ProductCard } from '../components/ProductCard'
import { CategoryIcon } from '../components/CategoryIcon'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Icon } from '../../../shared/components/Icon'
import styles from './ProductListPage.module.css'

export function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const keyword = params.get('keyword') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const pageInput = Number(params.get('page') ?? 0)
  const page = Number.isInteger(pageInput) && pageInput >= 0 && pageInput <= 2147483647 ? pageInput : 0
  const categories = useCategories()
  const products = useProducts({ keyword, categoryId, page, size: 4 })
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
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className={styles.heroCopy}>
        <div className={styles.eyebrow}><span /> YOUR WORK, OUR PICK</div>
        <h1 id="hero-title">필요한 서비스를 찾고,<br /><span>완료까지 한곳에서.</span></h1>
        <p>당신의 아이디어에 딱 맞는 전문가.<br />명확한 조건으로 시작하고, 결과까지 함께하세요.</p>
        <a className={styles.cta} href="#services">서비스 둘러보기 <Icon name="arrow" size={18} /></a>
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
    </section>
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
        <div><div className={styles.sectionEyebrow}>FIND YOUR NEXT EXPERT</div><h2 id="services-title">{keyword ? `'${keyword}' 검색 결과` : categoryId ? '카테고리 서비스' : '새로 등록된 서비스'}</h2></div>
        <span className={styles.total}>{products.data ? `${products.data.totalElements}개의 서비스` : '서비스 탐색'}</span>
      </div>
      {products.isPending ? <AsyncState title="서비스를 불러오고 있습니다." description="잠시만 기다려 주세요." />
        : products.isError ? <AsyncState title="서비스를 불러오지 못했습니다." error={products.error} onRetry={() => void products.refetch()} />
        : products.data.items.length === 0 ? <AsyncState title={outOfRange ? '요청한 페이지에 서비스가 없습니다.' : '조건에 맞는 서비스가 없습니다.'} description={outOfRange ? '첫 페이지로 돌아가 서비스를 확인해 보세요.' : '다른 검색어나 카테고리를 선택해 보세요.'} />
        : <div className={styles.products}>{products.data.items.map((product) => <ProductCard key={product.id} product={product} onSelect={() => navigate(`/products/${product.id}`, { state: { from: `/?${params.toString()}` } })} />)}</div>}
      {!products.isError && products.data && (products.data.totalPages > 1 || page > 0) && <nav className={styles.pagination} aria-label="서비스 페이지">
        <Button variant="secondary" disabled={page === 0 || products.isFetching} onClick={() => changeFilter('page', String(page - 1))}>이전</Button>
        <span aria-live="polite">총 {products.data.totalPages}페이지 · 현재 요청 {page + 1}페이지</span>
        <Button variant="secondary" disabled={page + 1 >= products.data.totalPages || products.isFetching} onClick={() => changeFilter('page', String(page + 1))}>다음</Button>
      </nav>}
      {outOfRange && <Button variant="secondary" onClick={() => changeFilter('page', '')}>첫 페이지로</Button>}
      {(keyword || categoryId || page > 0) && <button className={styles.reset} onClick={() => setParams({})}>검색 조건 초기화 ↗</button>}
    </section>
    <section className={styles.guide} aria-labelledby="guide-title">
      <div><span className={styles.sectionEyebrow}>HOW IT WORKS</span><h2 id="guide-title">시작부터 완료까지,<br />흐름은 간단하게.</h2></div>
      <ol>{[
        ['01', '서비스 선택', '가격과 작업 범위를 확인하세요.'],
        ['02', '요구사항 제출', '필요한 내용을 자세히 전해 주세요.'],
        ['03', '판매자 수락·결제', '수락 후 테스트 적립금으로 결제하세요.'],
        ['04', '납품·구매 확정', '결과물을 확인하고 거래를 완료하세요.'],
      ].map(([number, title, description]) => <li key={number}><span>{number}</span><strong>{title}</strong><p>{description}</p></li>)}</ol>
    </section>
  </>
}
