import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Icon } from '../../../shared/components/Icon'
import { useChatWorkspace } from '../workspace'
import { Button } from '../../../shared/components/Button'
import { loginPath, recheckSession } from '../../auth/session'
import { chatEnterAction } from '../keyboard'
import { mergeChatMessages } from '../messages'
import { useChatMember, useChatMessages } from '../queries'
import { chatStatusAllowsSend, MAX_MESSAGE_LENGTH } from '../validation'
import { useChatRealtime } from '../useChatRealtime'
import { useChatSend } from '../useChatSend'
import { ChatMessages } from './ChatMessages'
import styles from './OrderChatPanel.module.css'

interface OrderChatPanelProps {
  orderId: string
  orderStatus: string
  floating?: boolean
  active?: boolean
  expectedMemberId?: string
}

export function OrderChatPanel({
  orderId,
  orderStatus,
  floating = false,
  active = true,
  expectedMemberId,
}: OrderChatPanelProps) {
  const member = useChatMember()
  const client = useQueryClient()
  const changed =
    !!expectedMemberId && !!member.data && expectedMemberId !== member.data.id
  useEffect(() => {
    if (changed) void recheckSession(client)
  }, [changed, client])
  if (changed)
    return <AsyncState title="현재 계정으로 대화를 다시 확인하고 있어요" />
  // 배경 재확인으로 같은 계정의 입력을 지우지 않는다. 계정 변경은 아래 key로 입력·조회 모두 초기화한다.
  if (member.isPending)
    return <AsyncState title="대화의 로그인 상태를 확인하고 있어요" />
  if (member.isError)
    return (
      <section className={`${styles.panel} ${floating ? styles.floating : ''}`}>
        <h2>주문 대화</h2>
        <AsyncState
          title="대화를 확인할 수 없어요."
          error={member.error}
          description={member.error.message}
          onRetry={() => {
            void member.refetch()
          }}
        />
        {isUnauthorized(member.error) && (
          <Link to={loginPath(`/orders/${orderId}`)}>
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
      floating={floating}
      active={active}
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
  floating = false,
  active = true,
}: ChatConversationProps) {
  const query = useChatMessages(orderId, memberId)
  const realtime = useChatRealtime(
    orderId,
    memberId,
    query.data?.pages[0]?.lastSequence,
  )
  const messages = mergeChatMessages(query.data?.pages ?? [])
  const workspace = useChatWorkspace()
  const latest = messages.at(-1)?.createdAt
  const acknowledge = workspace?.acknowledge
  useEffect(() => {
    const viewed = () => {
      if (active && latest && !query.isError && document.visibilityState === 'visible')
        acknowledge?.(orderId, latest)
    }
    viewed()
    document.addEventListener('visibilitychange', viewed)
    return () => document.removeEventListener('visibilitychange', viewed)
  }, [active, latest, orderId, acknowledge, query.isError])
  const canSend =
    Boolean(query.data?.pages[0]?.canSend) && chatStatusAllowsSend(orderStatus)
  const { content, setDraft, busy, uncertain, failure, length, send, refresh } =
    useChatSend({ orderId, memberId, query, realtime, canSend })

  if (realtime.state === 'auth-required')
    return (
      <section className={`${styles.panel} ${floating ? styles.floating : ''}`}>
        <h2>주문 대화</h2>
        <p role="alert">
          로그인 상태가 변경되었거나 연결 권한을 확인하지 못했어요. 다시
          확인해 주세요.
        </p>
        <Button variant="secondary" onClick={realtime.reconnect}>
          연결 다시 확인
        </Button>
      </section>
    )
  if (query.isError)
    return (
      <section className={`${styles.panel} ${floating ? styles.floating : ''}`}>
        <h2>주문 대화</h2>
        <AsyncState
          title="대화를 조회하지 못했어요."
          error={query.error}
          description={query.error.message}
          onRetry={() => {
            void refresh()
          }}
        />
      </section>
    )
  if (!query.data) return <AsyncState title="대화를 불러오는 중이에요" />
  return (
    <section
      className={`${styles.panel} ${floating ? styles.floating : ''}`}
      aria-label="주문 대화"
    >
      {!floating && <h2>주문 대화</h2>}
      {!floating && (
        <p>
          구매자와 판매자가 대화할 수 있어요. 대화로 주문 조건이나 납기가 자동
          변경되지는 않아요.
        </p>
      )}
      <div className={styles.toolbar}>
        <span role="status">
          {realtime.state === 'connected'
            ? '실시간 연결됨'
            : realtime.state === 'offline'
              ? '실시간 연결 중단 · 조회·전송은 계속할 수 있어요.'
              : '실시간 연결 중 · 누락된 대화를 확인해요.'}
        </span>
        {realtime.state === 'offline' && (
          <Button variant="secondary" onClick={realtime.reconnect}>
            다시 연결
          </Button>
        )}
        <Button
          variant="secondary"
          disabled={query.isFetching}
          aria-label="대화 새로고침"
          title="대화 새로고침"
          onClick={() => {
            void refresh()
          }}
        >
          <Icon name="refresh" size={18} />
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
      <ChatMessages
        messages={messages}
        memberId={memberId}
        followLatest={floating && active}
      />
      {!canSend && <p>종료된 주문의 대화는 조회만 할 수 있어요.</p>}
      {failure && (
        <p className={styles.error} role="alert">
          {failure}
        </p>
      )}
      {uncertain && (
        <p className={styles.error} role="alert">
          전송 결과를 확인하지 못했어요. 대화를 새로고침하거나 같은 메시지를
          재전송해 주세요.
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
            onChange={(event) => setDraft(event.target.value)}
            aria-describedby={`chat-hint-${orderId}`}
            onKeyDown={(event) => {
              const action = chatEnterAction({
                key: event.key, altKey: event.altKey, repeat: event.repeat,
                isComposing: event.nativeEvent.isComposing, keyCode: event.nativeEvent.keyCode,
              })
              if (action === 'newline') {
                event.preventDefault()
                if (busy || uncertain) return
                const input = event.currentTarget
                const start = input.selectionStart
                setDraft(content.slice(0, start) + '\n' + content.slice(input.selectionEnd))
                requestAnimationFrame(() => input.setSelectionRange(start + 1, start + 1))
              } else if (action === 'send') {
                event.preventDefault()
                if (!busy && !uncertain && !query.isFetching) void send()
              } else if (event.key === 'Enter' && event.repeat && !event.nativeEvent.isComposing) {
                event.preventDefault()
              }
            }}
          />
          <small id={`chat-hint-${orderId}`}>Enter 전송 · Alt+Enter 줄바꿈</small>
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
