import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import { isValidProductId as validId } from './validation.ts'
import type {
  AdminMemberDetail,
  AdminMemberFilter,
  AdminMemberSummary,
  MemberSanction,
  MemberSanctionCommand,
} from './memberTypes.ts'

function memberPath(id: string) {
  if (!validId(id)) throw new Error('회원 번호를 확인해 주세요.')
  return `/admin/members/${id}`
}

function paging(page: number) {
  if (!Number.isInteger(page) || page < 0 || page > 2_147_483_647)
    throw new Error('페이지를 확인해 주세요.')
  return new URLSearchParams({ page: String(page), size: '20' })
}

export function getAdminMembers(
  filter: AdminMemberFilter,
  page: number,
  signal?: AbortSignal,
) {
  const params = paging(page)
  if (filter.q) params.set('q', filter.q)
  if (filter.restriction) params.set('restriction', filter.restriction)
  return apiRequest<PageResponse<AdminMemberSummary>>(
    `/admin/members?${params}`,
    { signal, cache: 'no-store' },
  )
}

export function getAdminMember(id: string, signal?: AbortSignal) {
  return apiRequest<AdminMemberDetail>(memberPath(id), {
    signal,
    cache: 'no-store',
  })
}

export function getMemberSanctions(
  id: string,
  page: number,
  signal?: AbortSignal,
) {
  return apiRequest<PageResponse<MemberSanction>>(
    `${memberPath(id)}/sanctions?${paging(page)}`,
    { signal, cache: 'no-store' },
  )
}

/** actor ID/role are never sent. Repeating an apply must retain the entire body and requestKey. */
export function executeMemberSanction(
  id: string,
  command: MemberSanctionCommand,
  isCurrentAccount?: () => boolean,
) {
  const base = `${memberPath(id)}/sanctions`
  if (command.action === 'apply') {
    return apiRequest<{ memberId: string; sanctionId: string }>(base, {
      method: 'POST',
      json: {
        kind: command.kind,
        reason: command.reason,
        endsAt: command.endsAt,
        requestKey: command.requestKey,
      },
      cache: 'no-store',
      isCurrentAccount,
    })
  }
  if (!validId(command.sanctionId))
    throw new Error('제재 번호를 확인해 주세요.')
  return apiRequest<void>(`${base}/${command.sanctionId}/revoke`, {
    method: 'POST',
    json: { reason: command.reason },
    cache: 'no-store',
    isCurrentAccount,
  })
}
