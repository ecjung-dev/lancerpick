import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { normalizeText, codePointLength } from '../../../shared/lib/text'
import { getOrder, payOrder, cancelOrder, confirmOrder, acceptOrder, rejectOrder } from '../api'
import styles from './Orders.module.css'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  return <OrderDetail key={id} id={id} />
}
function OrderDetail({ id }: { id: string }) {
  const client = useQueryClient()
  const key = ['wallet', 'order', id]
  const query = useQuery({ queryKey: key, queryFn: ({ signal }) => getOrder(id, signal), retry: false, gcTime: 0 })
  const [agreed, setAgreed] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [reason, setReason] = useState('')
  const [rejectionReason, setRejectionReason] = useState('')
  const rejectionLength = codePointLength(normalizeText(rejectionReason))
  const validRejection = rejectionLength > 0 && rejectionLength <= 1000
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  const lock = useRef(false)
  async function act(action: 'PAY' | 'CANCEL' | 'CONFIRM' | 'ACCEPT' | 'REJECT') {
    if (lock.current || failure || !query.data?.allowedActions.includes(action)) return
    if ((action === 'PAY' && !agreed) || (action === 'CONFIRM' && !confirmed) || (action === 'CANCEL' && !reason.trim())) return
    if (action === 'REJECT' && !validRejection) return
    lock.current = true; setBusy(true); setMessage('')
    try {
      const result = action === 'PAY' ? await payOrder(id)
        : action === 'CANCEL' ? await cancelOrder(id, reason)
        : action === 'ACCEPT' ? await acceptOrder(id)
        : action === 'REJECT' ? await rejectOrder(id, rejectionReason)
        : await confirmOrder(id)
      // 변경 응답을 개인 캐시에 직접 넣지 않고 현재 세션으로 재조회한다.
      setMessage(`주문 상태가 ${result.status}(으)로 확인됐습니다.`)
      setAgreed(false); setConfirmed(false); setReason(''); setRejectionReason('')
    } catch (error) {
      setFailure(error instanceof ApiError ? `${error.message} (${error.code})` : '처리 결과를 확인하지 못했습니다. 다시 요청하기 전에 주문 상태를 조회해 주세요.')
    } finally {
      // 성공·실패 모두 조회로 금액/상태를 확인한다. 변경 요청은 자동 재전송하지 않는다.
      await client.invalidateQueries({ queryKey: ['wallet'] })
      lock.current = false; setBusy(false)
    }
  }
  async function refresh() {
    const result = await query.refetch()
    if (!result.error) { setFailure(null); setAgreed(false); setConfirmed(false) }
  }
  if (query.error instanceof ApiError && query.error.status === 401) return <section className={styles.orders}><h1>주문 상세</h1><p>로그인이 필요합니다.</p><Link to="/login">로그인</Link></section>
  if (query.isError) return <section className={styles.orders}><h1>주문을 확인할 수 없습니다.</h1><p>접근 권한이나 주문 상태를 확인해 주세요.</p><button onClick={() => { void refresh() }}>다시 조회</button></section>
  if (!query.data) return <p role="status">주문을 불러오는 중입니다.</p>
  const order = query.data
  const disabled = busy || !!failure || query.isFetching
  return <section className={styles.orders}><Link to="/orders">← 거래 내역</Link><h1>{order.productSnapshot.title}</h1><p>주문 #{order.id} · {order.status}</p><h2>{formatWon(order.priceAmount)}</h2><p>구매자 {order.buyer.displayName} · 판매자 {order.seller.displayName}</p>
    <h2>보관된 거래 조건</h2><p className={styles.text}>{order.productSnapshot.scopeDescription}</p><p>소요일 {order.durationDays}일{order.currentDueAt && ` · 납기 ${formatDateTime(order.currentDueAt)}`}</p><p className={styles.text}>{order.termsSnapshot}</p><h2>제출한 요구사항</h2><p className={styles.text}>{order.requirements}</p>
    <h2>납품 내역</h2>{order.deliveries.length === 0 ? <p>등록된 납품이 없습니다.</p> : order.deliveries.map(delivery => <article key={delivery.id}><h3>{delivery.sequenceNo}차 납품</h3><p className={styles.text}>{delivery.description}</p>{delivery.resultUrl.startsWith('https://') && <a href={delivery.resultUrl} target="_blank" rel="noopener noreferrer">결과물 확인</a>}</article>)}
    {message && <p role="status">{message}</p>}{failure && <p role="alert">{failure}</p>}
    <button disabled={busy || query.isFetching} onClick={() => { void refresh() }}>현재 상태 다시 조회</button>
    {order.allowedActions.includes('ACCEPT') && <fieldset disabled={disabled}><legend>주문 수락</legend><p>보관된 거래 조건과 구매자의 요구사항을 확인해 주세요. 수락하면 구매자가 결제할 수 있습니다.</p><button onClick={() => { void act('ACCEPT') }}>주문 수락</button></fieldset>}
    {order.allowedActions.includes('REJECT') && <fieldset disabled={disabled}><legend>주문 거절</legend><label>거절 사유 (필수, 최대 1,000자)<textarea value={rejectionReason} onChange={e => setRejectionReason(e.target.value)} /></label><p>{rejectionLength} / 1,000자</p><p>거절하면 이 주문은 종료됩니다. 작성한 사유는 구매자에게 전달됩니다.</p><button disabled={!validRejection} onClick={() => { void act('REJECT') }}>주문 거절</button></fieldset>}
    {order.allowedActions.includes('PAY') && <fieldset disabled={disabled}><legend>적립금 결제</legend><label><input type="checkbox" checked={agreed} onChange={e => setAgreed(e.target.checked)} /> 위 거래 조건과 {formatWon(order.priceAmount)} 결제에 동의합니다.</label><button disabled={!agreed} onClick={() => { void act('PAY') }}>적립금으로 결제</button></fieldset>}
    {order.allowedActions.includes('CANCEL') && <fieldset disabled={disabled}><legend>직접 취소</legend><p>결제된 주문은 전액 적립금 환불과 함께 취소됩니다.</p><label>취소 사유<textarea value={reason} onChange={e => setReason(e.target.value)} /></label><button disabled={!reason.trim()} onClick={() => { void act('CANCEL') }}>주문 취소</button></fieldset>}
    {order.allowedActions.includes('CONFIRM') && <fieldset disabled={disabled}><legend>구매 확정</legend><p>납품을 확인한 후 확정해 주세요. 확정 후에는 취소·환불할 수 없습니다.</p><label><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} /> 결과물을 확인했고 구매 확정에 동의합니다.</label><button disabled={!confirmed} onClick={() => { void act('CONFIRM') }}>구매 확정</button></fieldset>}
    <Link to="/mypage/settlements">판매 수익 확인 →</Link>
  </section>
}
