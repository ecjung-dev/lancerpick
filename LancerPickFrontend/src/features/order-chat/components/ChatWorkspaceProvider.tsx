import { useCallback, useRef, useState, type PropsWithChildren } from 'react'
import { skipToken, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCurrentMember } from '../../auth/queries'
import { captureAccountScope } from '../../auth/session'
import { ChatWorkspaceContext } from '../workspace'
import { validChatSequence } from '../validation'
import { activityKey, hasNewChat, type ChatActivity } from '../activity'
import { useChatThreads } from '../queries'
import { ChatDock } from './ChatDock'

export interface ChatWorkspaceState {
  memberId: string
  scope: () => boolean
  orderId: string | null
  minimized: boolean
}

export function ChatWorkspaceProvider({ children }: PropsWithChildren) {
  const member = useCurrentMember()
  const client = useQueryClient()
  const trigger = useRef<HTMLButtonElement>(null)
  const [request, setRequest] = useState<ChatWorkspaceState | null>(null)
  const available = !!member.data && !member.isError
  const visible =
    available &&
    request &&
    request.memberId === member.data?.id &&
    request.scope()
      ? request
      : null
  const memberId = member.data?.id ?? ''
  const activity = useQuery<ChatActivity>({
    queryKey: activityKey(memberId), queryFn: skipToken,
    initialData: () => ({ startedAt: new Date().toISOString(), seen: {} }),
    staleTime: Infinity, gcTime: Infinity,
  }).data!
  const threads = useChatThreads(memberId, '', 0, available)
  const newOrderIds = threads.isError ? [] : (threads.data?.items ?? [])
    .filter((thread) => hasNewChat(thread, memberId, activity)).map((thread) => thread.orderId)
  const acknowledge = useCallback((orderId: string, timestamp: string) => {
    client.setQueryData<ChatActivity>(activityKey(memberId), (value) => {
      if (!value || Date.parse(timestamp) <= Date.parse(value.seen[orderId] ?? value.startedAt)) return value
      return { ...value, seen: { ...value.seen, [orderId]: timestamp } }
    })
  }, [client, memberId])
  function open(orderId: string | null) {
    if (
      !available ||
      !member.data ||
      (orderId !== null && !validChatSequence(orderId))
    )
      return
    setRequest({
      memberId: member.data.id,
      scope: captureAccountScope(client),
      orderId,
      minimized: false,
    })
  }
  function minimize() {
    setRequest((value) => (value ? { ...value, minimized: true } : value))
    trigger.current?.focus()
  }
  function close() {
    setRequest(null)
    trigger.current?.focus()
  }
  return (
    <ChatWorkspaceContext.Provider
      value={{
        available,
        newOrderIds,
        acknowledge,
        expanded: !!visible && !visible.minimized,
        trigger,
        toggle: () => {
          if (visible && !visible.minimized) minimize()
          else if (visible) setRequest({ ...visible, minimized: false })
          else open(null)
        },
        openChat: open,
        minimize,
      }}
    >
      {children}
      {visible && (
        <ChatDock
          key={visible.memberId}
          state={visible}
          onSelect={(orderId) =>
            setRequest((value) =>
              value ? { ...value, orderId, minimized: false } : value,
            )
          }
          onMinimize={minimize}
          onClose={close}
          onRestore={() =>
            setRequest((value) =>
              value ? { ...value, minimized: false } : value,
            )
          }
        />
      )}
    </ChatWorkspaceContext.Provider>
  )
}
