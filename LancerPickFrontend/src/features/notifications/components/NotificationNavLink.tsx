import { NavLink } from 'react-router'
import { unreadBadge } from '../messages'
import { useUnreadNotificationCount } from '../queries'

/** 로그인한 회원 메뉴에만 표시한다. 개수 조회 실패는 링크를 숨기지 않고 배지만 생략한다. */
export function NotificationNavLink() {
  const unread = useUnreadNotificationCount()
  const badge = unreadBadge(unread.data?.count)
  return (
    <NavLink to="/notifications" aria-label={badge ? `알림, 읽지 않은 알림 ${badge}개` : '알림'}>
      알림{badge && <span aria-hidden="true"> ({badge})</span>}
    </NavLink>
  )
}
