import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { captureAccountScope, loginPath } from '../../auth/session'
import { notificationReadOptions, useNotifications } from '../queries'
import { NotificationRow } from './NotificationRow'
import styles from './NotificationPanel.module.css'

export function NotificationPanel({ onClose }: { onClose: () => void }) {
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const location = useLocation()
  const query = useNotifications(page, unreadOnly)
  const client = useQueryClient()
  const readAll = useMutation(notificationReadOptions(client))
  const expired = isUnauthorized(query.error)
  return <>
    <div className={styles.heading}>
      <h2 id="header-notification-title">알림</h2>
      <button type="button" onClick={onClose} aria-label="알림 닫기" className={styles.close}>×</button>
    </div>
    <div className={styles.toolbar}>
      <div role="group" aria-label="알림 보기" className={styles.filters}>
        <button type="button" aria-pressed={!unreadOnly} onClick={() => { setUnreadOnly(false); setPage(0) }}>전체</button>
        <button type="button" aria-pressed={unreadOnly} onClick={() => { setUnreadOnly(true); setPage(0) }}>읽지 않음</button>
      </div>
      <button type="button" disabled={readAll.isPending || !query.data || query.isError}
        onClick={() => readAll.mutate(captureAccountScope(client))}>{readAll.isPending ? '처리 중…' : '모두 읽음'}</button>
    </div>
    <div className={styles.content} aria-busy={query.isFetching}>
      {readAll.isError && readAll.variables?.() && <p role="alert">읽음 처리 결과를 확인하지 못했어요. 목록을 다시 확인해 주세요.</p>}
      {expired ? <p>로그인하면 알림을 확인할 수 있어요. <Link to={loginPath(location.pathname + location.search)} onClick={onClose}>로그인</Link></p>
        : query.isError ? <div role="alert"><p>알림을 불러오지 못했어요.</p><button type="button" onClick={() => void query.refetch()}>다시 조회</button></div>
        : !query.data ? <p role="status">알림을 불러오는 중이에요.</p>
        : <>
          {query.data.items.length === 0 ? <p className={styles.empty}>{unreadOnly ? '읽지 않은 알림이 없어요.' : '받은 알림이 없어요.'}</p>
            : <ul className={styles.list}>{query.data.items.map((item) => <NotificationRow key={item.id} item={item} onNavigate={onClose} />)}</ul>}
          {(page > 0 || query.data.totalPages > 1) && <nav aria-label="알림 페이지" className={styles.pager}>
            <button type="button" disabled={page === 0 || query.isFetching} onClick={() => setPage(page - 1)}>이전</button>
            <span>{page + 1}페이지</span>
            <button type="button" disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => setPage(page + 1)}>다음</button>
            {page > 0 && query.data.items.length === 0 && <button type="button" onClick={() => setPage(0)}>첫 페이지로</button>}
          </nav>}
        </>}
    </div>
    <Link className={styles.all} to="/notifications" onClick={onClose}>전체 알림 보기 <span aria-hidden="true">↗</span></Link>
  </>
}
