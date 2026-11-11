import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useSearchParams } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { captureAccountScope, loginPath } from '../../auth/session'
import { notificationReadOptions, useNotifications } from '../queries'
import { NotificationRow } from '../components/NotificationRow'
import styles from './Notifications.module.css'

export { NotificationReadError } from '../components/NotificationRow'

export function NotificationsPage() {
  const [params, setParams] = useSearchParams()
  const requested = Number(params.get('page') ?? 0)
  const page = Number.isInteger(requested) && requested >= 0 && requested <= 2147483647 ? requested : 0
  // 알 수 없는 필터 값은 전체로 본다. 필터를 바꾸면 첫 페이지부터 다시 본다.
  const unreadOnly = params.get('filter') === 'unread'
  const showPage = (next: number, unread = unreadOnly) => {
    const search: Record<string, string> = {}
    if (unread) search.filter = 'unread'
    if (next > 0) search.page = String(next)
    setParams(search)
  }
  const client = useQueryClient()
  // 변경 요청은 자동 재시도하지 않는다. 결과와 무관하게 서버 상태를 다시 조회한다.
  const readAll = useMutation(notificationReadOptions(client))
  return (
    <section className={styles.notifications}>
      <header className={styles.heading}>
        <p className={styles.eyebrow}>NOTIFICATIONS</p>
        <h1>알림</h1>
        <p>거래 진행과 계정 관련 알림을 확인해요. 알림을 누르면 읽음으로 표시돼요.</p>
      </header>
      <div className={styles.toolbar}>
        <div role="group" aria-label="알림 보기" className={styles.filters}>
          <button type="button" className={styles.chip} aria-pressed={!unreadOnly} onClick={() => showPage(0, false)}>전체</button>
          <button type="button" className={styles.chip} aria-pressed={unreadOnly} onClick={() => showPage(0, true)}>읽지 않음</button>
        </div>
        <button type="button" className={styles.secondary} disabled={readAll.isPending} onClick={() => readAll.mutate(captureAccountScope(client))}>
          {readAll.isPending ? '처리 중…' : '모두 읽음'}
        </button>
        {readAll.isError && readAll.variables?.() && <span role="alert">읽음 처리 결과를 확인하지 못했어요. 목록을 다시 확인해 주세요.</span>}
      </div>
      <NotificationList page={page} unreadOnly={unreadOnly} onPageChange={(next) => showPage(next)} />
    </section>
  )
}

function NotificationList({
  page,
  unreadOnly,
  onPageChange,
}: {
  page: number
  unreadOnly: boolean
  onPageChange: (page: number) => void
}) {
  const location = useLocation()
  const query = useNotifications(page, unreadOnly)
  if (isUnauthorized(query.error))
    return (
      <p className={styles.notice}>
        로그인하면 알림을 확인할 수 있어요.{' '}
        <Link to={loginPath(location.pathname + location.search)}>로그인</Link>
      </p>
    )
  if (query.isError)
    return (
      <div role="alert" className={styles.notice}>
        <p>{query.error instanceof ApiError ? query.error.message : '알림을 불러오지 못했어요.'}</p>
        <button type="button" className={styles.secondary} onClick={() => void query.refetch()}>다시 조회</button>
      </div>
    )
  if (!query.data) return <p role="status" className={styles.notice}>알림을 불러오는 중이에요.</p>
  return (
    <>
      {query.data.items.length === 0 ? (
        <p className={styles.empty}>{unreadOnly ? '읽지 않은 알림이 없어요.' : '받은 알림이 없어요.'}</p>
      ) : (
        <ul className={styles.list}>
          {query.data.items.map((item) => <NotificationRow key={item.id} item={item} />)}
        </ul>
      )}
      {(page > 0 || query.data.totalPages > 1) && <nav aria-label="알림 페이지" className={styles.pager}>
        <button type="button" className={styles.secondary} disabled={page === 0 || query.isFetching} onClick={() => onPageChange(page - 1)}>이전</button>
        <span>{page + 1}페이지</span>
        <button
          type="button"
          className={styles.secondary}
          disabled={page + 1 >= query.data.totalPages || query.isFetching}
          onClick={() => onPageChange(page + 1)}
        >
          다음
        </button>
      </nav>}
      {page > 0 && query.data.items.length === 0 && (
        <button type="button" className={`${styles.secondary} ${styles.recover}`} onClick={() => onPageChange(0)}>첫 페이지로</button>
      )}
    </>
  )
}
