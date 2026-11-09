import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { formatDateTime } from '../../../shared/lib/format'
import { captureAccountScope } from '../../auth/session'
import { notificationMessage, notificationTarget } from '../messages'
import { notificationReadOptions } from '../queries'
import type { NotificationItem } from '../types'
import styles from './NotificationRow.module.css'

/** 링크 이동을 읽음 응답으로 지연하지 않는다. 계정 경계는 공통 mutation 옵션이 검사한다. */
export function NotificationRow({ item, onNavigate }: { item: NotificationItem; onNavigate?: () => void }) {
  const client = useQueryClient()
  const read = useMutation(notificationReadOptions(client, item.id))
  const target = notificationTarget(item)
  const markRead = () => {
    if (!item.read && !read.isPending) read.mutate(captureAccountScope(client))
  }
  const body = <>
    <strong>{notificationMessage(item.type)}</strong>
    {item.productTitle && <span className={styles.title}>{item.productTitle}</span>}
    <small>{formatDateTime(item.createdAt)}</small>
  </>
  return <li className={`${styles.row} ${item.read ? styles.read : styles.unread}`}>
    {!item.read && <span className={styles.srOnly}>읽지 않음</span>}
    {target ? (
      <Link to={target} onClick={() => { markRead(); onNavigate?.() }}
        onAuxClick={(event) => { if (event.button === 1) markRead() }} aria-busy={read.isPending}>{body}</Link>
    ) : (
      <button type="button" className={styles.plain} onClick={markRead} disabled={item.read || read.isPending}>{body}</button>
    )}
    {read.isError && read.variables?.() && <NotificationReadError target={target} onNavigate={onNavigate} />}
  </li>
}

export function NotificationReadError({ target, onNavigate }: { target: string | null; onNavigate?: () => void }) {
  return <p role="alert" className={styles.error}>
    읽음 처리 결과를 확인하지 못했어요. 목록을 다시 확인하거나 다시 눌러 주세요.
    {target && <> <Link to={target} onClick={onNavigate}>읽음 처리 없이 주문 보기</Link></>}
  </p>
}
