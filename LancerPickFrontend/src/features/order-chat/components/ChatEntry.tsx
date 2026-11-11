import { Icon } from '../../../shared/components/Icon'
import { useChatWorkspace } from '../workspace'
import styles from './ChatWorkspace.module.css'

export function ChatEntry({ orderId }: { orderId?: string }) {
  const workspace = useChatWorkspace()
  return (
    <button
      type="button"
      className={orderId ? styles.entry : styles.headerEntry}
      ref={orderId ? undefined : workspace?.trigger}
      disabled={!workspace?.available}
      aria-expanded={orderId ? undefined : (workspace?.expanded ?? false)}
      aria-label={!orderId && workspace?.newOrderIds.length ? "메시지, 새 메시지 있음" : undefined}
      title={!orderId ? "메시지 · 현재 세션에서 감지한 새 메시지 표시" : undefined}
      aria-haspopup="dialog"
      onClick={() =>
        orderId ? workspace?.openChat(orderId) : workspace?.toggle()
      }
    >
      {!orderId && <span className={styles.headerIcon}><Icon name="message" size={22} />{!!workspace?.newOrderIds.length && <span className={styles.newDot} aria-hidden="true" />}</span>}
      {orderId ? '이 주문 대화하기' : '메시지'}
    </button>
  )
}
