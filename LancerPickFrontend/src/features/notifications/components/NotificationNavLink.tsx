import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useLocation } from 'react-router'
import { Icon } from '../../../shared/components/Icon'
import { unreadBadge } from '../messages'
import { useUnreadNotificationCount } from '../queries'
import { NotificationPanel } from './NotificationPanel'
import styles from './NotificationNavLink.module.css'

/** 화면 이동 시 패널 상태를 버린다. 개인 목록은 패널이 열린 동안에만 조회한다. */
export function NotificationNavLink() {
  const location = useLocation()
  return <NotificationMenu key={location.key} />
}

function NotificationMenu() {
  const unread = useUnreadNotificationCount()
  const badge = unreadBadge(unread.isError ? undefined : unread.data?.count)
  const [position, setPosition] = useState<{ left: number; top: number; maxHeight: number } | null>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)

  const close = (restoreFocus: boolean) => {
    setPosition(null)
    if (restoreFocus) trigger.current?.focus()
  }

  useEffect(() => {
    if (!position) return
    panel.current?.focus()
    const outside = (event: PointerEvent | FocusEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !trigger.current?.contains(event.target)) {
        setPosition(null)
      }
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setPosition(null)
        trigger.current?.focus()
      }
    }
    const dismiss = () => setPosition(null)
    document.addEventListener('pointerdown', outside)
    document.addEventListener('focusin', outside)
    document.addEventListener('keydown', escape)
    window.addEventListener('resize', dismiss)
    window.addEventListener('scroll', dismiss)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('focusin', outside)
      document.removeEventListener('keydown', escape)
      window.removeEventListener('resize', dismiss)
      window.removeEventListener('scroll', dismiss)
    }
  }, [position])

  const toggle = () => {
    if (position) { close(false); return }
    const rect = trigger.current?.getBoundingClientRect()
    if (!rect) return
    const width = Math.min(420, window.innerWidth - 32)
    const top = Math.max(16, Math.min(rect.bottom + 12, window.innerHeight - 360))
    setPosition({
      left: Math.max(16, Math.min(rect.right - width, window.innerWidth - width - 16)),
      top,
      maxHeight: Math.min(600, window.innerHeight - top - 16),
    })
  }

  return <>
    <button ref={trigger} type="button" className={styles.trigger} onClick={toggle}
      aria-label={badge ? `알림, 읽지 않은 알림 ${badge}개` : '알림'}
      aria-expanded={!!position} aria-haspopup="dialog" aria-controls={position ? 'header-notification-panel' : undefined}>
      <span className={styles.icon}><Icon name="bell" size={22} />{badge && <span className={styles.badge} aria-hidden="true">{badge}</span>}</span>
      <span>알림</span>
    </button>
    {position && createPortal(
      <div ref={panel} id="header-notification-panel" role="dialog" aria-labelledby="header-notification-title"
        tabIndex={-1} className={styles.panel} style={position}>
        <NotificationPanel onClose={() => close(true)} />
      </div>, document.body,
    )}
  </>
}
