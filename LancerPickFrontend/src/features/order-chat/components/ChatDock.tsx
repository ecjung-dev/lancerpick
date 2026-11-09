import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatDateTime } from '../../../shared/lib/format'
import { useOrder } from '../../orders/queries'
import { orderStatusLabel } from '../../orders/status'
import { useChatThreads } from '../queries'
import type { ChatWorkspaceState } from './ChatWorkspaceProvider'
import { OrderChatPanel } from './OrderChatPanel'
import styles from './ChatWorkspace.module.css'

interface Props {
  state: ChatWorkspaceState
  onSelect: (id: string | null) => void
  onClose: () => void
  onMinimize: () => void
  onRestore: () => void
}
export function ChatDock({
  state,
  onSelect,
  onClose,
  onMinimize,
  onRestore,
}: Props) {
  const list = useRef<HTMLDivElement>(null)
  const conversation = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (state.minimized) return
    const target = state.orderId ? conversation.current : list.current
    target?.focus()
  }, [state.orderId, state.minimized])
  useEffect(() => {
    if (state.minimized) return
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || event.defaultPrevented) return
      event.preventDefault()
      if (state.orderId) onSelect(null)
      else onClose()
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [state.minimized, state.orderId, onSelect, onClose])
  return createPortal(
    <div className={styles.dock}>
      {state.minimized && (
        <button type="button" className={styles.restore} onClick={onRestore}>
          메시지 다시 열기
        </button>
      )}
      <div
        hidden={state.minimized}
        className={`${styles.panes} ${state.orderId ? styles.selected : ''}`}
      >
        {state.orderId && (
          <div
            ref={conversation}
            role="dialog"
            aria-label="주문 대화창"
            tabIndex={-1}
            className={styles.conversation}
          >
            <Conversation
              key={state.orderId}
              orderId={state.orderId}
              memberId={state.memberId}
              active={!state.minimized}
              onBack={() => onSelect(null)}
              onMinimize={onMinimize}
            />
          </div>
        )}
        <div
          ref={list}
          role="dialog"
          aria-label="메시지 목록"
          tabIndex={-1}
          className={styles.inbox}
        >
          <div className={styles.heading}>
            <h2>메시지</h2>
            <div>
              <button
                type="button"
                onClick={onMinimize}
                aria-label="메시지 최소화"
              >
                ―
              </button>
              <button type="button" onClick={onClose} aria-label="메시지 닫기">
                ×
              </button>
            </div>
          </div>
          <ThreadList
            memberId={state.memberId}
            active={!state.minimized}
            selected={state.orderId}
            onSelect={onSelect}
          />
        </div>
      </div>
    </div>,
    document.body,
  )
}

function ThreadList({
  memberId,
  active,
  selected,
  onSelect,
}: {
  memberId: string
  active: boolean
  selected: string | null
  onSelect: (id: string) => void
}) {
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const query = useChatThreads(memberId, q, page, active)
  return (
    <>
      <form
        className={styles.search}
        role="search"
        aria-label="대화 검색"
        onSubmit={(event) => {
          event.preventDefault()
          const value = draft.trim()
          if ([...value].length > 100) {
            setError('검색어는 100자 이하로 입력해 주세요.')
            return
          }
          setError('')
          setQ(value)
          setPage(0)
        }}
      >
        <label className={styles.srOnly} htmlFor="chat-thread-search">
          상품명·상대방·주문번호
        </label>
        <input
          id="chat-thread-search"
          type="search"
          value={draft}
          placeholder="상품명·상대방·주문번호"
          onChange={(event) => setDraft(event.target.value)}
        />
        <button type="submit">검색</button>
      </form>
      {error && <p role="alert">{error}</p>}
      <div className={styles.threadContent}>
        {query.isError ? (
          <div role="alert">
            <p>
              {query.error instanceof ApiError && query.error.status === 401
                ? '로그인 상태를 다시 확인해 주세요.'
                : '대화 목록을 불러오지 못했어요.'}
            </p>
            <button type="button" onClick={() => void query.refetch()}>
              다시 조회
            </button>
          </div>
        ) : !query.data ? (
          <p role="status">대화 목록을 불러오는 중입니다.</p>
        ) : (
          <>
            {!query.data.items.length ? (
              <p>
                {q
                  ? '검색 조건에 맞는 대화가 없습니다.'
                  : '대화할 주문이 없습니다. 주문 후 구매자·판매자와 대화할 수 있어요.'}
              </p>
            ) : (
              <ul className={styles.threadList}>
                {query.data.items.map((thread) => (
                  <li key={thread.orderId}>
                    <button
                      type="button"
                      aria-pressed={selected === thread.orderId}
                      onClick={() => onSelect(thread.orderId)}
                    >
                      <strong>
                        {thread.counterparty.displayName}{' '}
                        <small>
                          {thread.side === 'BUYER' ? '구매' : '판매'} · 주문 #
                          {thread.orderId}
                        </small>
                      </strong>
                      <span>{thread.productTitle}</span>
                      <span className={styles.preview}>
                        {thread.lastMessagePreview === null
                          ? '아직 대화가 없습니다. 대화를 시작해 보세요.'
                          : `${thread.lastSenderId === memberId ? '나: ' : ''}${thread.lastMessagePreview}`}
                      </span>
                      <small>
                        {formatDateTime(thread.activityAt)} ·{' '}
                        {thread.canSend
                          ? orderStatusLabel(thread.orderStatus)
                          : '조회 전용'}
                      </small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <div className={styles.pager}>
              <button
                type="button"
                disabled={page === 0 || query.isFetching}
                onClick={() => setPage(page - 1)}
              >
                이전
              </button>
              <span>{page + 1}페이지</span>
              <button
                type="button"
                disabled={page + 1 >= query.data.totalPages || query.isFetching}
                onClick={() => setPage(page + 1)}
              >
                다음
              </button>
              {page > 0 && !query.data.items.length && (
                <button type="button" onClick={() => setPage(0)}>
                  첫 페이지로
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function Conversation({
  orderId,
  memberId,
  active,
  onBack,
  onMinimize,
}: {
  orderId: string
  memberId: string
  active: boolean
  onBack: () => void
  onMinimize: () => void
}) {
  const query = useOrder(orderId)
  const other =
    query.data?.buyer.id === memberId ? query.data.seller : query.data?.buyer
  return (
    <>
      <div className={styles.heading}>
        <div>
          <button type="button" onClick={onBack} className={styles.back}>
            ← 목록
          </button>
          <strong>{other?.displayName ?? '주문 대화'}</strong>
        </div>
        <div>
          <button type="button" onClick={onMinimize} aria-label="대화창 최소화">
            ―
          </button>
          <button type="button" onClick={onBack} aria-label="대화창 닫기">
            ×
          </button>
        </div>
      </div>
      <Link className={styles.orderLink} to={`/orders/${orderId}`}>
        주문 #{orderId} ·{' '}
        {query.data?.productSnapshot.title ?? '주문 상세 보기'} ↗
      </Link>
      <div className={styles.conversationBody}>
        {query.isError ? (
          <div role="alert">
            <p>주문 대화를 확인할 수 없습니다.</p>
            <button type="button" onClick={() => void query.refetch()}>
              다시 조회
            </button>
          </div>
        ) : !query.data ? (
          <p role="status">주문을 확인하고 있습니다.</p>
        ) : (
          <OrderChatPanel
            orderId={orderId}
            orderStatus={query.data.status}
            expectedMemberId={memberId}
            floating
            active={active}
          />
        )}
      </div>
    </>
  )
}
