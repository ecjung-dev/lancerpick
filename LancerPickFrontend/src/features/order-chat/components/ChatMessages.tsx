import { useEffect, useRef } from 'react'
import { formatDateTime } from '../../../shared/lib/format'
import type { ChatMessage } from '../types'
import styles from './OrderChatPanel.module.css'

/** 대화 목록. followLatest면 사용자가 맨 아래 근처에 있거나 내가 보낸 직후에만 최신 메시지로 내린다. */
export function ChatMessages({
  messages,
  memberId,
  followLatest = false,
}: {
  messages: ChatMessage[]
  memberId: string
  followLatest?: boolean
}) {
  const list = useRef<HTMLOListElement>(null)
  const following = useRef(true)
  const lastId = messages.at(-1)?.id
  const lastSender = messages.at(-1)?.senderId
  useEffect(() => {
    if (
      followLatest &&
      (following.current || lastSender === memberId) &&
      list.current
    ) {
      list.current.scrollTop = list.current.scrollHeight
    }
  }, [followLatest, lastId, lastSender, memberId])
  if (!messages.length) return <p>아직 대화가 없습니다.</p>
  return (
    <ol
      ref={list}
      className={styles.messages}
      aria-label="메시지 내역"
      onScroll={() => {
        if (followLatest && list.current)
          following.current =
            list.current.scrollHeight -
              list.current.clientHeight -
              list.current.scrollTop <
            48
      }}
    >
      {messages.map((message) => (
        <li
          className={`${styles.message} ${message.senderId === memberId ? styles.mine : ''}`}
          key={message.id}
        >
          <strong>{message.senderId === memberId ? '나' : '상대방'}</strong>
          <p className={styles.content}>{message.content}</p>
          <time className={styles.time} dateTime={message.createdAt}>
            {formatDateTime(message.createdAt)}
          </time>
        </li>
      ))}
    </ol>
  )
}
