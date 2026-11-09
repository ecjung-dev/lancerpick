import { createContext, useContext, type RefObject } from 'react'

export interface ChatWorkspace {
  available: boolean
  expanded: boolean
  trigger: RefObject<HTMLButtonElement | null>
  toggle: () => void
  openChat: (orderId: string) => void
  minimize: () => void
}
export const ChatWorkspaceContext = createContext<ChatWorkspace | null>(null)
export function useChatWorkspace() {
  return useContext(ChatWorkspaceContext)
}
