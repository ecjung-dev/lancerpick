import type { QueryClient } from '@tanstack/react-query'
import { captureAccountScope, isUnclearResult } from '../auth/session.ts'
import { executeMemberSanction } from './memberApi.ts'
import type { MemberSanctionCommand } from './memberTypes.ts'

export const adminMemberPrefix = ['admin', 'members'] as const
export const pendingMemberCommandPrefix = ['admin', 'member-command'] as const
export const pendingMemberCommandKey = (id: string) =>
  [...pendingMemberCommandPrefix, id] as const
export interface PendingMemberCommand {
  command: MemberSanctionCommand
  phase: 'sending' | 'uncertain'
  revision: number
  operationId: string
}

/** Account-owned in-memory commands survive route changes and are removed by accountCache cleanup. */
export async function submitMemberSanction(
  client: QueryClient,
  id: string,
  input?: MemberSanctionCommand,
  isCurrentAccount = captureAccountScope(client),
) {
  if (!isCurrentAccount())
    throw new DOMException('계정이 변경됐어요.', 'AbortError')
  const key = pendingMemberCommandKey(id)
  const previous = client.getQueryData<PendingMemberCommand | null>(key)
  if (previous?.phase === 'sending')
    throw new Error('이미 처리 중인 요청이 있어요.')
  const command = previous?.command ?? input
  if (!command) throw new Error('처리할 제재 요청을 확인해 주세요.')
  client.setQueryDefaults(pendingMemberCommandPrefix, { gcTime: Infinity })
  const pending: PendingMemberCommand = {
    command,
    phase: 'sending',
    revision: (previous?.revision ?? 0) + 1,
    operationId: crypto.randomUUID(),
  }
  client.setQueryData(key, pending)
  try {
    const result = await executeMemberSanction(id, command, isCurrentAccount)
    if (!isCurrentAccount()) return
    if (
      client.getQueryData<PendingMemberCommand | null>(key)?.operationId ===
      pending.operationId
    )
      client.setQueryData(key, null)
    await client.invalidateQueries({ queryKey: adminMemberPrefix })
    return result
  } catch (error) {
    if (isCurrentAccount()) {
      // Rejection of a retry does not establish whether the first unknown request committed.
      if (
        client.getQueryData<PendingMemberCommand | null>(key)?.operationId ===
        pending.operationId
      )
        client.setQueryData(
          key,
          isUnclearResult(error) || previous
            ? { ...pending, phase: 'uncertain' }
            : null,
        )
      await client.invalidateQueries({ queryKey: adminMemberPrefix })
    }
    throw error
  }
}
