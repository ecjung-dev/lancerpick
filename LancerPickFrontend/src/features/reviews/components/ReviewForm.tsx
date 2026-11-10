import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { normalizedLength } from '../../../shared/lib/text'
import { writeReview } from '../api'
import { getOrder } from '../../orders/api'
import styles from './Reviews.module.css'

export function ReviewForm({ orderId, productId }: { orderId: string; productId: string }) {
  const client = useQueryClient()
  const lock = useRef(false)
  const [rating, setRating] = useState(5)
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [message, setMessage] = useState('')
  const length = normalizedLength(content)

  async function submit() {
    if (lock.current || blocked || length > 2000) return
    lock.current = true
    setBusy(true)
    setMessage('')
    try {
      await writeReview(orderId, { rating, content: length === 0 ? null : content })
      setBlocked(true)
      setMessage('후기를 등록했습니다.')
    } catch (error) {
      setBlocked(true)
      setMessage(error instanceof ApiError ? `${error.message} (${error.code})`
        : '처리 결과가 불명확합니다. 다시 전송하기 전에 주문을 조회해 주세요.')
    } finally {
      await Promise.allSettled([
        client.invalidateQueries({ queryKey: ['wallet', 'order', orderId] }),
        client.invalidateQueries({ queryKey: ['products', 'reviews', productId] }),
      ])
      setBusy(false)
      lock.current = false
    }
  }
  async function recheck() {
    if (lock.current) return
    lock.current = true
    setBusy(true)
    try {
      const order = await getOrder(orderId)
      await client.invalidateQueries({ queryKey: ['wallet', 'order', orderId] })
      if (order.allowedActions.includes('WRITE_REVIEW') && !order.review) {
        setBlocked(false)
        setMessage('후기 미작성 상태를 확인했습니다. 내용을 확인하고 등록해 주세요.')
      }
    } catch { setMessage('주문 상태를 확인하지 못했습니다. 로그인과 연결 상태를 확인해 주세요.') }
    finally { lock.current = false; setBusy(false) }
  }
  return <section>
    <h2>후기 작성</h2>
    <p>구매 확정된 주문당 한 번 작성할 수 있어요. 등록 후 수정·삭제할 수 없습니다.</p>
    <form className={styles.form} onSubmit={event => { event.preventDefault(); void submit() }}>
      <label>별점<select value={rating} disabled={busy || blocked} onChange={event => setRating(Number(event.target.value))}>
        {[5, 4, 3, 2, 1].map(value => <option key={value} value={value}>{value}점</option>)}
      </select></label>
      <label>후기 글 (선택)<textarea value={content} disabled={busy || blocked} onChange={event => setContent(event.target.value)} /></label>
      <span>{length} / 2,000자</span>
      <button disabled={busy || blocked || length > 2000} type="submit">{busy ? '처리 중…' : '후기 등록'}</button>
    </form>
    {message && <p role="status">{message}</p>}
    {blocked && <button disabled={busy} onClick={() => { void recheck() }}>주문 상태 다시 확인</button>}
  </section>
}
