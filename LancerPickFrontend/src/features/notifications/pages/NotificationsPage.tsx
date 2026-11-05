import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatDateTime } from '../../../shared/lib/format'
import { markAllNotificationsRead, markNotificationRead } from '../api'
import { notificationMessage, notificationTarget } from '../messages'
import { notificationKeys, useNotifications } from '../queries'
import type { NotificationItem } from '../types'
import styles from './Notifications.module.css'

export function NotificationsPage() {
  const [params, setParams] = useSearchParams()
  const requested = Number(params.get('page') ?? 0)
  const page = Number.isInteger(requested) && requested >= 0 && requested <= 2147483647 ? requested : 0
  const client = useQueryClient()
  // 변경 요청은 자동 재시도하지 않는다. 결과와 무관하게 서버 상태를 다시 조회한다.
  const readAll = useMutation({
    retry: false,
    mutationFn: markAllNotificationsRead,
    onSettled: () => client.invalidateQueries({ queryKey: notificationKeys.all }),
  })
  return (
    <section className={styles.notifications}>
      <Link to="/mypage">← 마이페이지</Link>
      <h1>알림</h1>
      <div className={styles.toolbar}>
        <button type="button" disabled={readAll.isPending} onClick={() => readAll.mutate()}>
          {readAll.isPending ? '처리 중…' : '모두 읽음'}
        </button>
        {readAll.isError && <span role="alert">읽음 처리하지 못했어요. 목록을 다시 확인해 주세요.</span>}
      </div>
      <NotificationList page={page} onPageChange={(next) => setParams(next === 0 ? {} : { page: String(next) })} />
    </section>
  )
}

function NotificationList({ page, onPageChange }: { page: number; onPageChange: (page: number) => void }) {
  const location = useLocation()
  const query = useNotifications(page)
  if (query.error instanceof ApiError && query.error.status === 401)
    return (
      <p>
        로그인 후 알림을 확인할 수 있습니다.{' '}
        <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}>로그인</Link>
      </p>
    )
  if (query.isError)
    return (
      <div role="alert">
        <p>{query.error instanceof ApiError ? query.error.message : '알림을 불러오지 못했습니다.'}</p>
        <button type="button" onClick={() => void query.refetch()}>다시 조회</button>
      </div>
    )
  if (!query.data) return <p role="status">알림을 불러오는 중입니다.</p>
  return (
    <>
      {query.data.items.length === 0 ? (
        <p>받은 알림이 없습니다.</p>
      ) : (
        <ul className={styles.list}>
          {query.data.items.map((item) => <NotificationRow key={item.id} item={item} />)}
        </ul>
      )}
      <nav aria-label="알림 페이지" className={styles.pager}>
        <button type="button" disabled={page === 0 || query.isFetching} onClick={() => onPageChange(page - 1)}>이전</button>
        <span>{page + 1}페이지</span>
        <button
          type="button"
          disabled={page + 1 >= query.data.totalPages || query.isFetching}
          onClick={() => onPageChange(page + 1)}
        >
          다음
        </button>
      </nav>
      {page > 0 && query.data.items.length === 0 && (
        <button type="button" onClick={() => onPageChange(0)}>첫 페이지로</button>
      )}
    </>
  )
}

function NotificationRow({ item }: { item: NotificationItem }) {
  const client = useQueryClient()
  const read = useMutation({
    retry: false,
    mutationFn: () => markNotificationRead(item.id),
    onSettled: () => client.invalidateQueries({ queryKey: notificationKeys.all }),
  })
  const target = notificationTarget(item)
  const markRead = () => {
    if (!item.read && !read.isPending) read.mutate()
  }
  const body = (
    <>
      <strong>{notificationMessage(item.type)}</strong>
      {item.productTitle && <span className={styles.title}>{item.productTitle}</span>}
      <small>{formatDateTime(item.createdAt)}</small>
    </>
  )
  return (
    <li className={item.read ? styles.read : styles.unread}>
      {!item.read && <span className={styles.srOnly}>읽지 않음</span>}
      {target ? (
        <Link to={target} onClick={markRead}>{body}</Link>
      ) : (
        <button type="button" className={styles.plain} onClick={markRead} disabled={item.read}>{body}</button>
      )}
    </li>
  )
}
