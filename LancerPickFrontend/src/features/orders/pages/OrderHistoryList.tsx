import { formatDateTime } from '../../../shared/lib/format'
import { describeHistory } from '../history'
import type { OrderDetail } from '../types'
import styles from './Orders.module.css'

export function OrderHistoryList({ order }: { order: OrderDetail }) {
  return (
    <section aria-labelledby="order-history-title">
      <h2 id="order-history-title">거래 이력</h2>
      {order.histories.length === 0 ? (
        <p>아직 기록된 이력이 없어요.</p>
      ) : (
        <ol>
          {order.histories.map((history) => {
            const item = describeHistory(history, order)
            return (
              <li key={history.id}>
                <strong>{item.action}</strong> · {item.actor} ·{' '}
                <time dateTime={history.createdAt}>
                  {formatDateTime(history.createdAt)}
                </time>
                <p>{item.transition}</p>
                {item.reason !== null && (
                  <p className={styles.text}>사유: {item.reason}</p>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
