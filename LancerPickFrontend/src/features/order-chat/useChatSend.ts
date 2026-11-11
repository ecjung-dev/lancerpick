import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { isUnauthorized } from '../../shared/api/client'
import { normalizedLength } from '../../shared/lib/text'
import { getMemberSession } from '../auth/api'
import { captureAccountScope, isUnclearResult, recheckSession } from '../auth/session'
import { sendChatMessage } from './api'
import { mergeChatMessages } from './messages'
import { chatKeys, usePendingChatSend, type useChatMessages } from './queries'
import {
  beginChatSend,
  confirmChatSendFromHistory,
  settleChatSend,
  type ChatSendAttempt,
} from './pending'
import type { useChatRealtime } from './useChatRealtime'
import { MAX_MESSAGE_LENGTH } from './validation'

/**
 * 주문 대화의 입력·발송·복구 흐름. 같은 clientMessageId로만 재전송하고(멱등), 발송 전후로 현재 계정을 확인하며,
 * 결과가 불명확한 발송은 'uncertain'으로 남겨 이력에서 확인되거나 같은 메시지로 재전송할 때까지 입력을 잠근다.
 * 화면(ChatConversation)은 표시만 맡는다.
 */
export function useChatSend({
  orderId,
  memberId,
  query,
  realtime,
  canSend,
}: {
  orderId: string
  memberId: string
  query: ReturnType<typeof useChatMessages>
  realtime: ReturnType<typeof useChatRealtime>
  canSend: boolean
}) {
  const client = useQueryClient()
  const draftKey = ['order-chat', 'draft', memberId, orderId] as const
  // Controlled input must update synchronously. Query observer notifications are
  // batched asynchronously and can restore an older value during IME composition.
  // The conversation is keyed by order/member; cache is only for room restoration.
  const [draft, setLocalDraft] = useState(
    () => client.getQueryData<string>(draftKey) ?? '',
  )
  const setDraft = (value: string) => {
    setLocalDraft(value)
    client.setQueryDefaults(['order-chat', 'draft'], { gcTime: Infinity })
    client.setQueryData(draftKey, value)
  }
  const [preparing, setPreparing] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const pending = usePendingChatSend(memberId, orderId)
  const content = pending?.input.content ?? draft
  const busy = preparing || pending?.phase === 'sending'
  const uncertain = pending?.phase === 'uncertain'
  const sending = useRef(false)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  const length = normalizedLength(content)

  useEffect(() => {
    if (
      pending &&
      query.data &&
      mergeChatMessages(query.data.pages).some(
        (message) =>
          message.senderId === memberId &&
          message.clientMessageId === pending.input.clientMessageId,
      )
    ) {
      confirmChatSendFromHistory(
        client,
        memberId,
        orderId,
        pending.input.clientMessageId,
      )
    }
  }, [client, memberId, orderId, pending, query.data])

  async function checkMember() {
    if ((await getMemberSession()).id !== memberId) {
      await recheckSession(client)
      await client.invalidateQueries({ queryKey: chatKeys.member })
      throw new Error(
        '계정이 변경됐어요. 현재 계정으로 대화를 다시 확인해 주세요.',
      )
    }
  }

  async function refresh() {
    try {
      await checkMember()
      if (!mounted.current) return
      await query.refetch()
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
      busy ||
      (!pending && (!canSend || length < 1 || length > MAX_MESSAGE_LENGTH))
    )
      return
    sending.current = true
    const isCurrentAccount = captureAccountScope(client)
    let attempt: ChatSendAttempt | undefined
    setPreparing(true)
    setFailure(null)
    try {
      await checkMember()
      if (!mounted.current || !isCurrentAccount()) return
      attempt = beginChatSend(client, memberId, orderId, content)
      if (!attempt) return
      setDraft('')
      if (realtime.connection.current?.isConnected())
        await realtime.connection.current.send(attempt.input)
      else await sendChatMessage(orderId, attempt.input, isCurrentAccount)
      await checkMember()
      settleChatSend(client, memberId, orderId, attempt, 'confirmed')
      if (!isCurrentAccount()) return
      if (mounted.current) setFailure(null)
      await client.invalidateQueries({
        queryKey: chatKeys.messages(orderId, memberId),
      })
      await client.invalidateQueries({ queryKey: chatKeys.threads })
    } catch (error) {
      const unclear = isUnclearResult(error)
      const settled =
        attempt &&
        settleChatSend(
          client,
          memberId,
          orderId,
          attempt,
          unclear ? 'uncertain' : 'rejected',
        )
      if (!mounted.current || !isCurrentAccount()) return
      if (!unclear && attempt && settled === 'cleared')
        setDraft(attempt.input.content)
      if (!attempt || (!unclear && settled === 'cleared'))
        setFailure(
          error instanceof Error ? error.message : '전송하지 못했어요.',
        )
      if (isUnauthorized(error))
        await client.invalidateQueries({ queryKey: chatKeys.member })
      else await query.refetch()
    } finally {
      sending.current = false
      if (mounted.current) setPreparing(false)
    }
  }

  return { content, setDraft, busy, uncertain, failure, length, send, refresh }
}
