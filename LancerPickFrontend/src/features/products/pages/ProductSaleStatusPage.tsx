import { loginPath } from '../../auth/session'
import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { getMemberSession } from '../../auth/api'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { getOwnedProduct } from '../management'
import { useSaleStatusProduct, useSaleStatusSession } from '../queries'
import { changeProductSaleStatus, type SaleStatusChange } from '../saleStatus'
import styles from './ProductSaleStatusPage.module.css'
import { myProductsReturnPath } from '../myProductsNavigation'

export function ProductSaleStatusPage() {
  const { id = '' } = useParams()
  const session = useSaleStatusSession()
  const location = useLocation()
  if (isUnauthorized(session.error)) {
    return <section className={styles.page}><h1>로그인 후 판매 상태를 관리해 주세요</h1>
      <Link to={loginPath(location.pathname + location.search)}>로그인</Link></section>
  }
  if (session.isError) return <AsyncState title="계정을 확인하지 못했어요" error={session.error} onRetry={() => { void session.refetch() }} />
  if (!session.data) return <AsyncState title="계정 확인 중" />
  return <section className={styles.page}>
    <Link to={myProductsReturnPath(location.state?.fromMyProducts)}>← 내 상품 조회</Link>
    <p><Link to={`/mypage/products/${id}/edit`} state={{ fromMyProducts: myProductsReturnPath(location.state?.fromMyProducts) }}>상품 수정</Link></p>
    <h1>상품 판매 상태 관리</h1>
    {session.isFetching && <p role="status">계정을 다시 확인하고 있어요.</p>}
    <div hidden={session.isFetching}>
      <SaleStatusPanel key={`${session.data.id}:${id}`} memberId={session.data.id} id={id} checking={session.isFetching} />
    </div>
  </section>
}

function SaleStatusPanel({ memberId, id, checking }: { memberId: string; id: string; checking: boolean }) {
  const product = useSaleStatusProduct(memberId, id)
  const client = useQueryClient()
  const [target, setTarget] = useState<SaleStatusChange | null>(null)
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [needsReview, setNeedsReview] = useState(false)
  const [accountInvalid, setAccountInvalid] = useState(false)
  const inFlight = useRef(false)
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
      void client.cancelQueries({ queryKey: ['product-sale-status', memberId, id], exact: true })
      client.removeQueries({ queryKey: ['product-sale-status', memberId, id], exact: true })
    }
  }, [client, memberId, id])

  async function applyChange() {
    if (!target || pending || inFlight.current || checking || needsReview) return
    inFlight.current = true
    setPending(true)
    setError('')
    setMessage('')
    try {
      const member = await getMemberSession()
      if (!active.current) return
      if (member.id !== memberId) { setAccountInvalid(true); return }
      const result = await changeProductSaleStatus(id, target)
      if (!active.current) return
      if (result.seller.id !== memberId) { setAccountInvalid(true); return }
      client.setQueryData(['product-sale-status', memberId, id], result)
      void client.invalidateQueries({ queryKey: ['products'] })
      void client.invalidateQueries({ queryKey: ['product-editor', memberId, id], exact: true })
      setTarget(null)
      setMessage(result.saleStatus === 'ON_SALE' ? '판매를 재개했어요.' : '판매를 중지했어요.')
    } catch (cause) {
      if (!active.current) return
      setError(cause instanceof Error ? cause.message : '판매 상태를 확인해 주세요.')
      setNeedsReview(true)
      if (isUnauthorized(cause)) setAccountInvalid(true)
      // 결과 불명확·버전 충돌 모두 변경 요청을 자동 재전송하지 않는다.
    } finally {
      inFlight.current = false
      if (active.current) setPending(false)
    }
  }

  async function reviewLatest() {
    if (inFlight.current || checking) return
    inFlight.current = true
    setPending(true)
    try {
      const member = await getMemberSession()
      if (!active.current) return
      if (member.id !== memberId) { setAccountInvalid(true); return }
      const result = await getOwnedProduct(id)
      if (!active.current) return
      if (result.seller.id !== memberId) { setAccountInvalid(true); return }
      client.setQueryData(['product-sale-status', memberId, id], result)
      setTarget(null)
      setNeedsReview(false)
      setError('')
      setMessage('현재 상태를 확인했어요. 변경하려면 다시 선택해 주세요.')
    } catch (cause) {
      if (active.current) {
        setError(cause instanceof Error ? cause.message : '조회하지 못했어요.')
        if (isUnauthorized(cause)) setAccountInvalid(true)
      }
    } finally { inFlight.current = false; if (active.current) setPending(false) }
  }

  if (accountInvalid || (product.data && product.data.seller.id !== memberId)) {
    return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="이전 계정의 상품 정보를 숨겼어요. 페이지를 다시 열어 주세요." />
  }
  if (product.isPending) return <AsyncState title="상품 판매 상태를 불러오고 있어요" />
  if (product.isError) return <AsyncState title="본인 상품을 조회하지 못했어요" error={product.error} onRetry={() => { void product.refetch() }} />
  const value = product.data
  return <div className={styles.panel}>
    <h2>{value.title}</h2>
    <dl><dt>현재 판매 상태</dt><dd>{value.saleStatus === 'ON_SALE' ? '판매 중' : '판매 중지'}</dd>
      <dt>공개 상태</dt><dd>{value.isHidden ? '관리자 숨김' : '공개'}</dd></dl>
    <p>판매 중지하면 신규 주문을 받을 수 없어요. 이미 생성된 주문과 보관된 거래 조건은 유지돼요.</p>
    {value.isHidden && <p>관리자 숨김은 판매 재개로 해제되지 않아요. 공개 목록에 다시 표시하려면 관리자 복구도 필요해요.</p>}
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
    {needsReview ? <><p>자동으로 다시 변경하지 않아요. 현재 상태부터 확인해 주세요.</p>
      <Button disabled={pending || checking} onClick={() => { void reviewLatest() }}>현재 상태 다시 확인</Button></>
      : target ? <div className={styles.confirm}>
        <p>{target.saleStatus === 'STOPPED' ? '판매를 중지할까요?' : '판매를 재개할까요?'}</p>
        <div className={styles.actions}>
          <Button disabled={pending || checking} onClick={() => { void applyChange() }}>{pending ? '처리 중…' : target.saleStatus === 'STOPPED' ? '판매 중지 적용' : '판매 재개 적용'}</Button>
          <Button variant="secondary" disabled={pending || checking} onClick={() => setTarget(null)}>취소</Button>
        </div>
      </div> : <Button disabled={pending || checking} onClick={() => {
        setMessage('')
        setTarget({ saleStatus: value.saleStatus === 'ON_SALE' ? 'STOPPED' : 'ON_SALE', expectedVersion: value.version })
      }}>{value.saleStatus === 'ON_SALE' ? '판매 중지하기' : '판매 재개하기'}</Button>}
  </div>
}
