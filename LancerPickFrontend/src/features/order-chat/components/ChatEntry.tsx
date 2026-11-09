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
      aria-haspopup="dialog"
      onClick={() =>
        orderId ? workspace?.openChat(orderId) : workspace?.toggle()
      }
    >
      {!orderId && <Icon name="message" size={22} />}
      {orderId ? '이 주문 대화하기' : '메시지'}
    </button>
  )
}
