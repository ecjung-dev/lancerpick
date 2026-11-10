import { loginPath } from '../../auth/session'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { apiRequest, ApiError, isUnauthorized } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { AsyncState } from '../../../shared/components/AsyncState'
import { getCategories } from '../api'
import { createProduct, getOwnedProduct, patchProduct, validateProduct, textLimits, type ProductInput, type OwnedProduct } from '../management'
import type { Category } from '../types'
import styles from './ProductEditorPage.module.css'
import { myProductsReturnPath } from '../myProductsNavigation'

export function ProductEditorPage() {
  const { id } = useParams()
  const location = useLocation()
  const member = useQuery({ queryKey: ['product-editor-session'], queryFn: ({ signal }) => apiRequest<{ id: string }>('/me', { signal }), retry: false, gcTime: 0 })
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
  return <section className={styles.page}><Link to={listPath}>← 내 상품 조회</Link><h1>{id ? '상품 수정' : '상품 등록'}</h1>
    {id && <p><Link to={`/mypage/products/${id}/sale-status`} state={{ fromMyProducts: listPath }}>상품 판매 상태 관리 →</Link></p>}
    <ProductForm key={id ?? 'new'} initial={product.data} categories={categories.data} memberId={memberId} checking={checking} />
  </section>
}

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
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current || checking || locked || latest) return
    let input: ProductInput
    try { input = validateProduct(draft) } catch (cause) { setError((cause as Error).message); return }
    inFlight.current = true; setPending(true); setError(''); setSaved(false)
    try {
      const member = await apiRequest<{ id: string }>('/me')
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
  if (accountInvalid) return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="이전 계정의 상품 폼을 숨겼어요. 페이지를 새로고침해 주세요." />
  return <form onSubmit={submit} className={styles.form}>
    {saved && <p role="status">상품을 저장했어요.</p>}
    {baseline && <p>버전 {baseline.version} · {baseline.isHidden ? '관리자 숨김' : '공개'} · {baseline.saleStatus === 'ON_SALE' ? '판매 중' : '판매 중지'}</p>}
    {latest && <div role="alert"><h2>다른 수정 사항이 있어요</h2><p>내 입력은 유지했어요. 아래 최신 내용과 비교해 주세요. 내 입력을 유지하면 이 폼의 모든 값으로 수정될 수 있어요.</p><dl>{Object.entries(toInput(latest)).map(([field, value]) => <div key={field}><dt>{field in labels ? labels[field as keyof typeof labels] : ({ categoryId: '카테고리', priceAmount: '가격 (원)', durationDays: '작업 기간 (일)' }[field] ?? field)}</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{field === 'categoryId' ? latest.category.name : String(value)}</dd></div>)}</dl><p>최신 버전 {latest.version}</p><Button onClick={() => { setBaseline(latest); setLatest(null); setError('최신 버전을 기준으로 내 입력을 검토한 뒤 저장해 주세요.') }}>내 입력 유지하고 최신 버전으로 검토</Button><Button variant="secondary" onClick={() => { setBaseline(latest); setDraft(toInput(latest)); setLatest(null); setError('') }}>최신 내용으로 다시 작성</Button></div>}
    <fieldset disabled={pending || locked || checking}>
      <label htmlFor="product-category">카테고리</label><select id="product-category" value={draft.categoryId} onChange={event => setDraft({ ...draft, categoryId: event.target.value })}><option value="">선택해 주세요</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select>
      {(Object.keys(textLimits) as (keyof typeof textLimits)[]).map(field => <div key={field}><label htmlFor={`product-${field}`}>{labels[field]} (필수, 최대 {textLimits[field]}자)</label><textarea id={`product-${field}`} rows={field === 'title' ? 2 : 5} value={draft[field]} onChange={event => setDraft({ ...draft, [field]: event.target.value })} /></div>)}
      <label htmlFor="product-price">가격 (원)</label><input id="product-price" type="number" min="1" max="1000000" step="1" value={draft.priceAmount} onChange={event => setDraft({ ...draft, priceAmount: Number(event.target.value) })} />
      <label htmlFor="product-days">작업 기간 (일)</label><input id="product-days" type="number" min="1" max="30" step="1" value={draft.durationDays} onChange={event => setDraft({ ...draft, durationDays: Number(event.target.value) })} />
      <Button type="submit" disabled={pending || locked || checking || !!latest}>{pending ? '저장 중…' : '상품 저장'}</Button>
    </fieldset>
    {error && <p role="alert">{error}</p>}
    {locked && <p>결과나 계정을 다시 확인해 주세요. 저장 요청을 자동 재전송하지 않습니다.</p>}
    <Link to="/mypage/seller-profile">판매자 프로필 관리</Link>
  </form>
}
function toInput(product: OwnedProduct): ProductInput { return { categoryId: product.category.id, title: product.title, description: product.description, scopeDescription: product.scopeDescription, preparationDescription: product.preparationDescription, priceAmount: product.priceAmount, durationDays: product.durationDays } }
