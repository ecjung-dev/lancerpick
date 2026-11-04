import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, apiRequest } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatWon } from '../../../shared/lib/format'
import { getProductDetail } from '../../products/api'
import type { ProductDetail } from '../../products/types'
import { createOrder, prepareOrderInput, type CreateOrderInput } from '../create'
import { readOrderAttempt, saveOrderAttempt, clearOrderAttempt } from '../attempt'
import styles from './OrderCreatePage.module.css'

type Member = { id: string; displayName: string }

export function OrderCreatePage() {
  const { id = '' } = useParams()
  const member = useQuery({ queryKey: ['order-entry-session'], queryFn: ({ signal }) => apiRequest<Member>('/me', { signal }), retry: false, gcTime: 0, staleTime: 0 })
  const product = useQuery({ queryKey: ['products', 'detail', id], queryFn: ({ signal }) => getProductDetail(id, signal), retry: false, gcTime: 0, staleTime: 0 })
  if (member.error instanceof ApiError && member.error.status === 401) return <section className={styles.page}><h1>로그인이 필요해요</h1><Link to="/login">로그인으로 이동</Link><p><Link to={`/products/${id}`}>상품으로 돌아가기</Link></p></section>
  if (member.isError || product.isError) return <section className={styles.page}><AsyncState title="주문 정보를 불러오지 못했어요" error={member.error ?? product.error} onRetry={() => { void member.refetch(); void product.refetch() }} /><Link to={`/products/${id}`}>상품으로 돌아가기</Link></section>
  if (!member.data || !product.data) return <AsyncState title="주문 정보를 확인하고 있어요" />
  if (member.data.id === product.data.seller.id) return <section className={styles.page}><h1>내 상품에는 주문할 수 없어요</h1><Link to={`/products/${id}`}>상품으로 돌아가기</Link></section>
  return <section className={styles.page}><Link to={`/products/${id}`}>← 상품 상세</Link><h1>주문 요청 작성</h1>
    <div hidden={member.isFetching || product.isFetching}><OrderForm key={`${member.data.id}:${id}`} member={member.data} product={product.data} checking={member.isFetching || product.isFetching} /></div>
  </section>
}

function OrderForm({ member, product, checking }: { member: Member; product: ProductDetail; checking: boolean }) {
  const navigate = useNavigate()
  const [requirements, setRequirements] = useState('')
  const [restored] = useState(() => {
    try { return { input: typeof window === 'undefined' ? null : readOrderAttempt(sessionStorage, member.id, product.id), failed: false } }
    catch { return { input: null, failed: true } }
  })
  const [attempt, setAttempt] = useState<CreateOrderInput | null>(restored.input)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [blocked, setBlocked] = useState(restored.failed)
  const [rejected, setRejected] = useState(false)
  const inFlight = useRef(false)
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current || checking || blocked) return
    let input: CreateOrderInput
    try {
      input = attempt ?? prepareOrderInput({ productId: product.id, expectedProductVersion: product.version, requirements, requestKey: crypto.randomUUID() })
    } catch (cause) { setError(cause instanceof Error ? cause.message : '입력값을 확인해 주세요.'); return }
    inFlight.current = true
    setPending(true)
    setError('')
    try {
      const current = await apiRequest<Member>('/me')
      if (!active.current) return
      if (current.id !== member.id) { setBlocked(true); throw new Error('로그인 계정이 바뀌었어요. 로그인 상태를 다시 확인해 주세요.') }
      // 재확인 정보를 저장하지 못하면 주문 POST를 시작하지 않는다.
      saveOrderAttempt(sessionStorage, member.id, input)
      setAttempt(input)
      const order = await createOrder(input)
      clearOrderAttempt(sessionStorage, member.id, input.productId)
      if (!active.current) return
      navigate(`/orders/${order.id}`, { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError && [400, 401, 403, 404, 409].includes(cause.status)) {
        setBlocked(true)
        setRejected([400, 404, 409].includes(cause.status))
        setError(`${cause.message} 상품과 주문 내역을 다시 확인해 주세요.`)
      } else setError(`${cause instanceof Error ? cause.message : '연결을 확인해 주세요.'} 주문 결과가 불확실할 수 있어요. 주문 내역을 먼저 확인하거나 같은 요청으로 재확인해 주세요.`)
    } finally { inFlight.current = false; setPending(false) }
  }
  return <form onSubmit={submit} className={styles.form}>
    <div className={styles.summary}><h2>{product.title}</h2><p>판매자 {product.seller.displayName}</p><p>{formatWon(product.priceAmount)} · 작업 기간 {product.durationDays}일</p><h3>작업 범위</h3><p>{product.scopeDescription}</p><h3>준비사항</h3><p>{product.preparationDescription}</p></div>
    <label htmlFor="order-requirements">요구사항 (필수, 공백 정리 후 최대 5,000자)</label>
    <textarea id="order-requirements" value={attempt?.requirements ?? requirements} onChange={event => setRequirements(event.target.value)} disabled={pending || !!attempt || checking || blocked} rows={8} />
    <p>판매자 수락 후 거래 조건을 확인하고 결제해요. 지금은 주문 요청만 보내요.</p>
    {restored.failed && <p role="alert">저장된 요청을 읽지 못했어요. 새 주문 전 내 주문 내역을 확인해 주세요.</p>}
    {error && <p role="alert">{error}</p>}
    {rejected && <Button variant="secondary" onClick={() => {
      try { clearOrderAttempt(sessionStorage, member.id, product.id); navigate(`/products/${product.id}`) }
      catch { setError('저장 정보를 정리하지 못했어요. 주문 내역을 먼저 확인해 주세요.') }
    }}>거절된 요청 정리 후 상품 다시 확인</Button>}
    {attempt && <p>같은 요청의 중복 생성을 막기 위해 입력을 잠갔어요. 새로고침하거나 화면을 나가기 전 주문 내역에서 결과를 확인해 주세요.</p>}
    <div className={styles.actions}><Button type="submit" disabled={pending || checking || blocked}>{pending ? '주문 확인 중…' : attempt ? '같은 요청으로 재확인' : '주문 요청 보내기'}</Button><Link to="/orders?side=BUYER">내 주문 확인</Link></div>
  </form>
}
