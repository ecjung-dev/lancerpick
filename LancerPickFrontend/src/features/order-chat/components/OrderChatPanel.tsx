import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatDateTime } from '../../../shared/lib/format'
import { codePointLength, normalizeText } from '../../../shared/lib/text'
import { getMemberSession } from '../../auth/api'
import { isUnclearResult } from '../../auth/session'
import { sendChatMessage } from '../api'
import { mergeChatMessages } from '../messages'
import { chatKeys, useChatMember, useChatMessages } from '../queries'
import { chatStatusAllowsSend, MAX_MESSAGE_LENGTH } from '../validation'
import { useChatRealtime } from '../useChatRealtime'
import type { ChatMessage, SendChatMessageInput } from '../types'
import styles from './OrderChatPanel.module.css'

interface OrderChatPanelProps {
  orderId: string
  orderStatus: string
}

export function OrderChatPanel({ orderId, orderStatus }: OrderChatPanelProps) {
  const member = useChatMember()
  // 배경 재확인으로 같은 계정의 입력을 지우지 않는다. 계정 변경은 아래 key로 입력·조회 모두 초기화한다.
  if (member.isPending)
    return <AsyncState title="대화의 로그인 상태를 확인하고 있습니다." />
  if (member.isError)
    return (
      <section className={styles.panel}>
        <h2>주문 대화</h2>
        <AsyncState
          title="대화를 확인할 수 없습니다."
          error={member.error}
          description={member.error.message}
          onRetry={() => {
            void member.refetch()
          }}
        />
        {member.error instanceof ApiError && member.error.status === 401 && (
          <Link to={`/login?next=${encodeURIComponent(`/orders/${orderId}`)}`}>
            로그인 후 대화 보기
          </Link>
        )}
      </section>
    )
  if (!member.data) return null
  return (
    <ChatConversation
      key={`${orderId}:${member.data.id}`}
      orderId={orderId}
      orderStatus={orderStatus}
      memberId={member.data.id}
    />
  )
}

interface ChatConversationProps extends OrderChatPanelProps {
  memberId: string
}

function ChatConversation({
  orderId,
  orderStatus,
  memberId,
}: ChatConversationProps) {
  const client = useQueryClient()
  const query = useChatMessages(orderId, memberId)
  const realtime = useChatRealtime(
    orderId,
    memberId,
    query.data?.pages[0]?.lastSequence,
  )
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [uncertain, setUncertain] = useState(false)
  const pending = useRef<SendChatMessageInput | null>(null)
  const sending = useRef(false)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  const messages = mergeChatMessages(query.data?.pages ?? [])
  const length = codePointLength(normalizeText(content))
  const canSend =
    Boolean(query.data?.pages[0]?.canSend) && chatStatusAllowsSend(orderStatus)

  async function checkMember() {
    if ((await getMemberSession()).id !== memberId) {
      await client.invalidateQueries({ queryKey: chatKeys.member })
      throw new Error(
        '계정이 변경되었습니다. 현재 계정으로 대화를 다시 확인해 주세요.',
      )
    }
  }

  function completed() {
    pending.current = null
    setContent('')
    setUncertain(false)
    setFailure(null)
  }

  async function refresh() {
    try {
      await checkMember()
      if (!mounted.current) return
      const result = await query.refetch()
      if (!mounted.current) return
      const draft = pending.current
      if (
        draft &&
        result.data &&
        mergeChatMessages(result.data.pages).some(
          (message) =>
            message.senderId === memberId &&
            message.clientMessageId === draft.clientMessageId,
        )
      )
        completed()
    } catch (error) {
      if (mounted.current)
        setFailure(
          error instanceof Error ? error.message : '대화를 다시 확인해 주세요.',
        )
      await client.invalidateQueries({ queryKey: chatKeys.member })
    }
  }

  async function send() {
    if (
      sending.current ||
      (!pending.current &&
        (!canSend || length < 1 || length > MAX_MESSAGE_LENGTH))
    )
      return
    sending.current = true
    setBusy(true)
    setFailure(null)
    try {
      await checkMember()
      if (!mounted.current) return
      pending.current ??= {
        clientMessageId: crypto.randomUUID(),
        content: normalizeText(content),
      }
      if (realtime.connection.current?.isConnected())
        await realtime.connection.current.send(pending.current)
      else await sendChatMessage(orderId, pending.current)
      await checkMember()
      if (!mounted.current) return
      completed()
      await query.refetch()
    } catch (error) {
      if (!mounted.current) return
      if (isUnclearResult(error)) {
        setUncertain(Boolean(pending.current))
        setFailure(
          '전송 결과를 확인하지 못했습니다. 대화를 새로고침하거나 같은 메시지를 재전송해 주세요.',
        )
      } else {
        pending.current = null
        setUncertain(false)
        setFailure(
          error instanceof Error ? error.message : '전송하지 못했습니다.',
        )
      }
      if (error instanceof ApiError && error.status === 401)
        await client.invalidateQueries({ queryKey: chatKeys.member })
      else await query.refetch()
    } finally {
      sending.current = false
      if (mounted.current) setBusy(false)
    }
  }

  if (realtime.state === 'auth-required')
    return (
      <section className={styles.panel}>
        <h2>주문 대화</h2>
        <p role="alert">
          로그인 상태가 변경되었거나 연결 권한을 확인하지 못했습니다. 다시
          확인해 주세요.
        </p>
        <Button variant="secondary" onClick={realtime.reconnect}>
          연결 다시 확인
        </Button>
      </section>
    )
  if (query.isError)
    return (
      <section className={styles.panel}>
        <h2>주문 대화</h2>
        <AsyncState
          title="대화를 조회하지 못했습니다."
          error={query.error}
          description={query.error.message}
          onRetry={() => {
            void refresh()
          }}
        />
      </section>
    )
  if (!query.data) return <AsyncState title="대화를 불러오고 있습니다." />
  return (
    <section className={styles.panel} aria-label="주문 대화">
      <h2>주문 대화</h2>
      <p>
        구매자와 판매자가 대화할 수 있습니다. 대화로 주문 조건이나 납기가 자동
        변경되지는 않습니다.
      </p>
      <div className={styles.toolbar}>
        <span role="status">
          {realtime.state === 'connected'
            ? '실시간 연결됨'
            : realtime.state === 'offline'
              ? '실시간 연결 중단 · 조회·전송은 계속할 수 있습니다.'
              : '실시간 연결 중 · 누락된 대화를 확인합니다.'}
        </span>
        {realtime.state === 'offline' && (
          <Button variant="secondary" onClick={realtime.reconnect}>
            다시 연결
          </Button>
        )}
        <Button
          variant="secondary"
          disabled={busy || query.isFetching}
          onClick={() => {
            void refresh()
          }}
        >
          대화 새로고침
        </Button>
        {query.hasNextPage && (
          <Button
            variant="secondary"
            disabled={busy || query.isFetching}
            onClick={() => {
              void query.fetchNextPage()
            }}
          >
            이전 대화 더 보기
          </Button>
        )}
      </div>
      <ChatMessages messages={messages} memberId={memberId} />
      {!canSend && <p>종료된 주문의 대화는 조회만 할 수 있습니다.</p>}
      {failure && (
        <p className={styles.error} role="alert">
          {failure}
        </p>
      )}
      {(canSend || uncertain) && (
        <div className={styles.composer}>
          <label htmlFor={`chat-${orderId}`}>메시지</label>
          <textarea
            id={`chat-${orderId}`}
            className={styles.input}
            value={content}
            readOnly={busy || uncertain}
            onChange={(event) => setContent(event.target.value)}
          />
          <span>
            {length} / {MAX_MESSAGE_LENGTH}자
          </span>
          <Button
            disabled={
              busy ||
              query.isFetching ||
              (!uncertain && (length < 1 || length > MAX_MESSAGE_LENGTH))
            }
            onClick={() => {
              void send()
            }}
          >
            {busy
              ? '전송 중…'
              : uncertain
                ? '같은 메시지 재전송'
                : '메시지 보내기'}
          </Button>
        </div>
      )}
    </section>
  )
}

export function ChatMessages({
  messages,
  memberId,
}: {
  messages: ChatMessage[]
  memberId: string
}) {
  if (!messages.length) return <p>아직 대화가 없습니다.</p>
  return (
    <ol className={styles.messages} aria-label="메시지 내역">
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
