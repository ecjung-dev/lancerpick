import { useRef, useState, type PropsWithChildren } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useCurrentMember } from '../../auth/queries'
import { captureAccountScope } from '../../auth/session'
import { ChatWorkspaceContext } from '../workspace'
import { validChatSequence } from '../validation'
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
