import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { orderDeadlineView } from '../deadlines'
import type { OrderDetail } from '../types'
import { OrderHistoryList } from './OrderHistoryList'
import styles from './OrderDetailPage.module.css'

export function OrderDeliveries({ order }: { order: OrderDetail }) {
  const deadlines = orderDeadlineView(order)
  return <section className={styles.card} aria-labelledby="order-deliveries-title">
    <h2 id="order-deliveries-title">납품 내역 <span className={styles.count}>{order.deliveries.length}건</span></h2>
    {order.deliveries.length === 0 ? <p className={styles.empty}>아직 등록된 납품이 없어요.</p>
      : order.deliveries.map(delivery => {
        const deadline = deadlines.deliveries.find(item => item.id === delivery.id)
        return <article className={styles.delivery} key={delivery.id}>
          <div className={styles.deliveryHeading}>
            <h3>{delivery.sequenceNo}차 납품</h3>
            <span className={styles.meta}>제출 {formatDateTime(delivery.submittedAt)}
              {deadline?.dueAt && (deadline.late ? ' · 기한 후 제출' : ' · 기한 내 제출')}</span>
          </div>
          <p className={styles.text}>{delivery.description}</p>
          {/^https:\/\//i.test(delivery.resultUrl) && <a className={styles.outlineLink}
            href={delivery.resultUrl} target="_blank" rel="noopener noreferrer">결과물 확인 ↗</a>}
        </article>
      })}
  </section>
}

export function OrderConditions({ order }: { order: OrderDetail }) {
  return <section className={styles.card} aria-labelledby="order-conditions-title">
    <h2 id="order-conditions-title">보관된 거래 조건</h2>
    <p className={styles.meta}>현재 상품이 변경되어도 주문 당시 확정한 조건은 유지돼요.</p>
    <dl className={styles.conditions}>
      <div><dt>상품 설명</dt><dd>{order.productSnapshot.description}</dd></div>
      <div><dt>작업 범위</dt><dd>{order.productSnapshot.scopeDescription}</dd></div>
      <div><dt>준비 사항</dt><dd>{order.productSnapshot.preparationDescription}</dd></div>
      <div><dt>소요 기간</dt><dd>{order.durationDays}일</dd></div>
      <div><dt>제출한 요구사항</dt><dd>{order.requirements}</dd></div>
      <div><dt>거래 규칙</dt><dd>{order.termsSnapshot}</dd></div>
    </dl>
  </section>
}

export function OrderRecords({ order }: { order: OrderDetail }) {
  return <>
    <section className={styles.card} aria-labelledby="order-payment-title">
      <h2 id="order-payment-title">결제 기록</h2>
      {order.payment ? <>
        <p className={styles.recordAmount}>{formatWon(order.payment.amount)}</p>
        <p>{order.payment.status === 'REFUNDED' ? '전액 환불됨'
          : order.payment.status === 'PAID' ? '결제 완료' : order.payment.status}</p>
        <p className={styles.meta}>결제 {formatDateTime(order.payment.paidAt)}
          {order.payment.refundedAt && ` · 환불 ${formatDateTime(order.payment.refundedAt)}`}</p>
      </> : <p className={styles.empty}>아직 결제 기록이 없어요.</p>}
    </section>
    <div className={`${styles.card} ${styles.history}`}><OrderHistoryList order={order} /></div>
  </>
}
