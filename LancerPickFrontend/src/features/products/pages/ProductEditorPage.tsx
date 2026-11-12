import { loginPath } from '../../auth/session'
import { useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { getMemberSession } from '../../auth/api'
import { Icon } from '../../../shared/components/Icon'
import { formatWon } from '../../../shared/lib/format'
import { normalizedLength } from '../../../shared/lib/text'
import { Button } from '../../../shared/components/Button'
import { AsyncState } from '../../../shared/components/AsyncState'
import { getCategories } from '../api'
import { createProduct, getOwnedProduct, patchProduct, validateProduct, textLimits, type ProductInput, type OwnedProduct } from '../management'
import type { Category } from '../types'
import styles from './ProductEditorPage.module.css'
import { myProductsReturnPath } from '../myProductsNavigation'
import { PageHeading } from '../../../shared/components/PageHeading'
import { useMountedRef } from '../../../shared/lib/useMountedRef'

export function ProductEditorPage() {
  const { id } = useParams()
  const location = useLocation()
  const member = useQuery({ queryKey: ['product-editor-session'], queryFn: ({ signal }) => getMemberSession(signal), retry: false, gcTime: 0 })
  if (isUnauthorized(member.error)) return <section className={styles.page}><h1>로그인 후 상품을 관리해 주세요</h1><Link to={loginPath(location.pathname + location.search)}>로그인</Link></section>
  if (member.isError) return <AsyncState title="계정을 확인하지 못했어요" error={member.error} onRetry={() => { void member.refetch() }} />
  if (!member.data) return <AsyncState title="계정 확인 중" />
  return <div hidden={member.isFetching}><Editor key={`${member.data.id}:${id ?? 'new'}`} id={id} memberId={member.data.id} checking={member.isFetching} /></div>
}

function Editor({ id, memberId, checking }: { id?: string; memberId: string; checking: boolean }) {
  const location = useLocation()
  const listPath = myProductsReturnPath(location.state?.fromMyProducts)
  const product = useQuery({ queryKey: ['product-editor', memberId, id], queryFn: ({ signal }) => getOwnedProduct(id!, signal), enabled: !!id, retry: false, gcTime: 0, refetchOnWindowFocus: false })
  const categories = useQuery({ queryKey: ['categories'], queryFn: ({ signal }) => getCategories(signal) })
  if (product.isError || categories.isError) return <AsyncState title="상품 정보를 불러오지 못했어요" error={product.error ?? categories.error} onRetry={() => { if (id) void product.refetch(); void categories.refetch() }} />
  if (!categories.data || (id && !product.data)) return <AsyncState title="상품 정보 확인 중" />
  return <section className={styles.page}>
    <PageHeading eyebrow="MY SERVICE" title={id ? '상품 수정' : '상품 등록'}
      actions={<Link className={styles.back} to={listPath}>내 상품 조회 <span aria-hidden="true">↗</span></Link>}>
      <p>서비스의 범위와 조건을 명확하게 안내해 주세요.</p>
    </PageHeading>
    {id && <p><Link to={`/mypage/products/${id}/sale-status`} state={{ fromMyProducts: listPath }}>상품 판매 상태 관리 →</Link></p>}
    <ProductForm key={id ?? 'new'} initial={product.data} categories={categories.data} memberId={memberId} checking={checking} />
  </section>
}

const hints = { title: '어떤 서비스를 제공하는지 한눈에 알 수 있는 이름을 적어 주세요.', description: '서비스의 특징, 진행 방식, 결과물을 자세히 소개해 주세요.', scopeDescription: '제공하는 작업과 결과물, 수정 범위 등을 구체적으로 적어 주세요.', preparationDescription: '작업을 시작하기 전에 구매자가 전달해야 할 자료를 안내해 주세요.' }
const placeholders = { title: '예: 브랜드의 개성을 담은 로고 디자인', description: '예: 브랜드의 방향을 함께 정리하고, 로고 시안과 최종 파일을 제공합니다.', scopeDescription: '예: 로고 시안 2종, 수정 1회, 최종 PNG·SVG 파일 제공', preparationDescription: '예: 브랜드명, 원하는 분위기, 참고 이미지, 사용 용도' }
const labels = { title: '상품명', description: '상품 설명', scopeDescription: '작업 범위', preparationDescription: '준비사항' }
function ProductForm({ initial, categories, memberId, checking }: { initial?: OwnedProduct; categories: Category[]; memberId: string; checking: boolean }) {
  const location = useLocation()
  const navigate = useNavigate()
  const client = useQueryClient()
  const [baseline, setBaseline] = useState(initial)
  const [draft, setDraft] = useState<ProductInput>(() => initial ? toInput(initial) : { categoryId: '', title: '', description: '', scopeDescription: '', preparationDescription: '', priceAmount: 1, durationDays: 1 })
  const [error, setError] = useState('')
  const [latest, setLatest] = useState<OwnedProduct | null>(null)
  const [pending, setPending] = useState(false)
  const [locked, setLocked] = useState(false)
  const [saved, setSaved] = useState(false)
  const [accountInvalid, setAccountInvalid] = useState(false)
  const inFlight = useRef(false)
  const active = useMountedRef()
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current || checking || locked || latest) return
    let input: ProductInput
    try { input = validateProduct(draft) } catch (cause) { setError((cause as Error).message); return }
    inFlight.current = true; setPending(true); setError(''); setSaved(false)
    try {
      const member = await getMemberSession()
      if (!active.current) return
      if (member.id !== memberId) { setAccountInvalid(true); throw new Error('로그인 계정이 바뀌었어요. 페이지를 다시 열어 주세요.') }
      let result: OwnedProduct
      if (baseline) {
        const before = toInput(baseline)
        const patch = Object.fromEntries(Object.entries(input).filter(([field, value]) => before[field as keyof ProductInput] !== value))
        if (Object.keys(patch).length === 0) { setError('수정할 내용을 입력해 주세요.'); return }
        result = await patchProduct(baseline.id, patch, baseline.version)
      } else result = await createProduct(input)
      if (!active.current) return
      setBaseline(result); setDraft(toInput(result)); setSaved(true)
      void client.invalidateQueries({ queryKey: ['products'] })
      navigate(`/mypage/products/${result.id}/edit`, { replace: true, state: { fromMyProducts: myProductsReturnPath(location.state?.fromMyProducts) } })
    } catch (cause) {
      if (!active.current) return
      if (isUnauthorized(cause)) setAccountInvalid(true)
      if (cause instanceof ApiError && cause.code === 'PRODUCT_VERSION_CONFLICT' && baseline) {
        try { setLatest(await getOwnedProduct(baseline.id)) } catch { setLocked(true) }
      } else if (!(cause instanceof ApiError) || cause.status === 0 || cause.status >= 500 || cause.status === 401) setLocked(true)
      setError((cause as Error).message)
    } finally { inFlight.current = false; setPending(false) }
  }
  function textField(field: keyof typeof textLimits) {
    const count = normalizedLength(draft[field])
    const over = count > textLimits[field]
    const props = {
      id: `product-${field}`, value: draft[field], placeholder: placeholders[field],
      'aria-required': true as const, 'aria-invalid': over,
      'aria-describedby': `product-${field}-hint product-${field}-count`,
      onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setDraft({ ...draft, [field]: event.target.value }),
    }
    return <div className={styles.field}>
      <label htmlFor={`product-${field}`}>{labels[field]}</label>
      <p id={`product-${field}-hint`} className={styles.hint}>{hints[field]}</p>
      {field === 'title' ? <input type="text" {...props} /> : <textarea rows={field === 'description' ? 6 : 4} {...props} />}
      <small id={`product-${field}-count`} className={`${styles.counter} ${over ? styles.over : ''}`}>{count.toLocaleString('ko-KR')} / {textLimits[field].toLocaleString('ko-KR')}자{over ? ' · 최대 글자 수를 초과했어요.' : ''}</small>
    </div>
  }
  if (accountInvalid) return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="이전 계정의 상품 폼을 숨겼어요. 페이지를 새로고침해 주세요." />
  return <form onSubmit={submit} className={styles.form}>
    {saved && <p role="status">상품을 저장했어요.</p>}
    {baseline && <p className={styles.version}>버전 {baseline.version} · {baseline.isHidden ? '관리자 숨김' : '공개'} · {baseline.saleStatus === 'ON_SALE' ? '판매 중' : '판매 중지'}</p>}
    {latest && <div role="alert"><h2>다른 수정 사항이 있어요</h2><p>내 입력은 유지했어요. 아래 최신 내용과 비교해 주세요. 내 입력을 유지하면 이 폼의 모든 값으로 수정될 수 있어요.</p><dl>{Object.entries(toInput(latest)).map(([field, value]) => <div key={field}><dt>{field in labels ? labels[field as keyof typeof labels] : ({ categoryId: '카테고리', priceAmount: '가격 (원)', durationDays: '작업 기간 (일)' }[field] ?? field)}</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{field === 'categoryId' ? latest.category.name : String(value)}</dd></div>)}</dl><p>최신 버전 {latest.version}</p><Button onClick={() => { setBaseline(latest); setLatest(null); setError('최신 버전을 기준으로 내 입력을 검토한 뒤 저장해 주세요.') }}>내 입력 유지하고 최신 버전으로 검토</Button><Button variant="secondary" onClick={() => { setBaseline(latest); setDraft(toInput(latest)); setLatest(null); setError('') }}>최신 내용으로 다시 작성</Button></div>}
    <fieldset disabled={pending || locked || checking}>
      <legend className={styles.srOnly}>상품 정보 입력</legend>
      <div className={styles.editorGrid}>
        <div className={styles.sections}>
          <section className={styles.section} aria-labelledby="product-basics">
            <div className={styles.sectionHeading}><span>01</span><div><h2 id="product-basics">기본 정보</h2><p>서비스를 찾기 쉽게 분류하고 이름을 붙여 주세요.</p></div><small>모든 항목 필수</small></div>
            <div className={styles.field}>
              <label htmlFor="product-category">카테고리</label>
              <select id="product-category" aria-required="true" value={draft.categoryId} onChange={event => setDraft({ ...draft, categoryId: event.target.value })}><option value="">선택해 주세요</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select>
            </div>
            {textField('title')}
          </section>
          <section className={styles.section} aria-labelledby="product-service">
            <div className={styles.sectionHeading}><span>02</span><div><h2 id="product-service">서비스 안내</h2><p>구매자가 확인할 작업 내용과 준비사항이에요.</p></div></div>
            {textField('description')}
            {textField('scopeDescription')}
            {textField('preparationDescription')}
          </section>
          <section className={styles.section} aria-labelledby="product-terms">
            <div className={styles.sectionHeading}><span>03</span><div><h2 id="product-terms">가격 및 작업 기간</h2><p>안내한 작업 범위에 맞는 조건을 설정해 주세요.</p></div></div>
            <div className={styles.termsGrid}>
              <div className={styles.field}><label htmlFor="product-price">가격 (원)</label><div className={styles.unitInput}><input id="product-price" aria-required="true" aria-describedby="product-price-hint" type="number" min="1" max="1000000" step="1" value={draft.priceAmount} onChange={event => setDraft({ ...draft, priceAmount: Number(event.target.value) })} /><span aria-hidden="true">원</span></div><small id="product-price-hint">1~1,000,000원 · 정수 입력</small></div>
              <div className={styles.field}><label htmlFor="product-days">작업 기간 (일)</label><div className={styles.unitInput}><input id="product-days" aria-required="true" aria-describedby="product-days-hint" type="number" min="1" max="30" step="1" value={draft.durationDays} onChange={event => setDraft({ ...draft, durationDays: Number(event.target.value) })} /><span aria-hidden="true">일</span></div><small id="product-days-hint">1~30일 · 정수 입력</small></div>
            </div>
          </section>
        </div>
        <aside className={styles.summary} aria-label="작성 중인 상품 요약">
          <span className={styles.summaryLabel}>SERVICE SUMMARY</span>
          <span className={styles.category}>{categories.find(category => category.id === draft.categoryId)?.name ?? '카테고리 선택 전'}</span>
          <h2>{draft.title.trim() || '나의 서비스를 소개해 주세요'}</h2>
          <dl><div><dt>주문 금액</dt><dd>{Number.isFinite(draft.priceAmount) ? formatWon(draft.priceAmount) : '입력 전'}</dd></div><div><dt>작업 기간</dt><dd>{draft.durationDays}일</dd></div></dl>
          <p><Icon name="document" size={17} />등록 전 확인해 주세요</p>
          <ul><li>설명과 작업 범위가 일치하나요?</li><li>결과물과 수정 범위가 명확한가요?</li><li>구매자가 준비할 자료를 적었나요?</li></ul>
        </aside>
      </div>
      <div className={styles.saveBar}><p>입력한 내용을 확인한 후 저장해 주세요.</p><Button type="submit" disabled={pending || locked || checking || !!latest}>{pending ? '저장 중…' : '상품 저장'}<Icon name="arrow" size={17} /></Button></div>
    </fieldset>
    {error && <p role="alert">{error}</p>}
    {locked && <p>결과나 계정을 다시 확인해 주세요. 저장 요청을 자동 재전송하지 않아요.</p>}
    <Link to="/mypage/seller-profile">판매자 프로필 관리</Link>
  </form>
}
function toInput(product: OwnedProduct): ProductInput { return { categoryId: product.category.id, title: product.title, description: product.description, scopeDescription: product.scopeDescription, preparationDescription: product.preparationDescription, priceAmount: product.priceAmount, durationDays: product.durationDays } }
