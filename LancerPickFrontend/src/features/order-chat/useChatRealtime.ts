import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { InfiniteData } from '@tanstack/react-query'
import { chatKeys } from './queries'
import { createChatRealtime } from './realtime'
import { mergeChatMessages } from './messages'
import type { ChatConnectionState, ChatMessagesPage } from './types'

export function useChatRealtime(
  orderId: string,
  memberId: string,
  initialSequence?: string,
) {
  const client = useQueryClient()
  const connection = useRef<ReturnType<typeof createChatRealtime> | null>(null)
  const [state, setState] = useState<ChatConnectionState>('connecting')
  const [generation, setGeneration] = useState(0)
  // 초기 조회 이후의 refetch는 연결을 다시 만들거나 복구 cursor를 되돌리지 않는다.
  const enabled = initialSequence !== undefined
  const sequence = useRef(initialSequence)
  useEffect(() => {
    sequence.current = initialSequence
  }, [initialSequence])
  useEffect(() => {
    if (!enabled) return
    const transport = createChatRealtime({
      orderId,
      memberId,
      lastSequence: sequence.current ?? '0',
      onState(next) {
        setState((previous) =>
          previous === 'auth-required' && next !== 'connected'
            ? previous
            : next,
        )
        if (next === 'auth-required')
          void client.invalidateQueries({ queryKey: chatKeys.member })
      },
      onMessages(messages, recovered) {
        client.setQueryData<InfiniteData<ChatMessagesPage>>(
          chatKeys.messages(orderId, memberId),
          (data) => {
            if (!data?.pages.length) return data
            const [first, ...rest] = data.pages
            return {
              ...data,
              pages: [
                {
                  ...first,
                  items: mergeChatMessages([
                    { ...first, items: [...first.items, ...messages] },
                  ]),
                  // 이전 페이지 cursor는 유지한다. 복구 조회로만 전송 가능 여부와 마지막 순번을 갱신한다.
                  canSend: recovered?.canSend ?? first.canSend,
                  lastSequence:
                    recovered && !recovered.hasMore
                      ? recovered.lastSequence
                      : first.lastSequence,
                },
                ...rest,
              ],
            }
          },
        )
      },
    })
    connection.current = transport
    transport.start()
    return () => {
      connection.current = null
      transport.stop()
    }
  }, [client, orderId, memberId, enabled, generation])
  return {
    connection,
    state,
    reconnect: () => setGeneration((value) => value + 1),
  }
}
