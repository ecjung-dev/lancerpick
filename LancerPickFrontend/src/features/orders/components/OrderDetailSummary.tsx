import { Link } from 'react-router'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { ChatEntry } from '../../order-chat/components/ChatEntry'
import type { OrderDetail } from '../types'
import { OrderFundsPanel } from './OrderFundsPanel'
import styles from '../pages/OrderDetailPage.module.css'

export function OrderDetailSummary({ order, isSeller }: { order: OrderDetail; isSeller: boolean }) {
  const earningsPath = order.status === 'COMPLETED'
    ? `/mypage/settlements?q=${encodeURIComponent(order.id)}` : '/mypage/settlements'
  return <aside className={styles.summary} aria-labelledby="order-summary-title">
    <h2 id="order-summary-title">거래 요약</h2>
    <p className={styles.amount}>{formatWon(order.priceAmount)}</p>
    {order.policyVersion === 'CONTRACT_V3'
      ? <OrderFundsPanel orderId={order.id} orderStatus={order.status} compact
        sellerView={isSeller} feeRateBps={order.payment?.feeRateBps} />
      : <p className={styles.meta}>결제·환불 내역은 거래 기록에서 확인할 수 있어요.</p>}
    <dl className={styles.summaryRows}>
      <div><dt>{isSeller ? '구매자' : '판매자'}</dt><dd>{isSeller ? order.buyer.displayName : order.seller.displayName}</dd></div>
      <div><dt>소요 기간</dt><dd>{order.durationDays}일</dd></div>
      {order.currentDueAt && <div><dt>현재 납기</dt><dd>{formatDateTime(order.currentDueAt)}</dd></div>}
    </dl>
    <div className={styles.chatEntry}><ChatEntry orderId={order.id} /></div>
    {isSeller && <div className={styles.earningsLink}>
      <Link to={earningsPath}>{order.status === 'COMPLETED' ? '이 주문의 판매 수익 보기' : '판매 수익 내역 보기'} <span aria-hidden="true">→</span></Link>
      {order.status !== 'COMPLETED' && <small>이 주문의 수익은 구매 확정 후 기록돼요.</small>}
    </div>}
  </aside>
}
