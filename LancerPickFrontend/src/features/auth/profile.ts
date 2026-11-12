import type { QueryClient } from '@tanstack/react-query'
import { isUnauthorized } from '../../shared/api/client.ts'
import { getMemberSession, updateMe } from './api.ts'
import { memberQueryKey, resetAccountState } from './session.ts'
import type { Member } from './types.ts'

/** saved=저장됨, signedOut=세션이 끝나 보내지 않음, accountChanged=다른 계정으로 바뀌어 보내지 않음(또는 다른 계정 응답) */
type SaveNameOutcome =
  | { kind: 'saved'; member: Member }
  | { kind: 'signedOut' }
  | { kind: 'accountChanged' }

/**
 * 표시 이름 저장(PATCH /api/me). 순서가 중요하다.
 * 1) 지금 세션이 이 화면을 연 계정인지 먼저 확인한다(공통 회원 확인 API getMemberSession 재사용). 같은 브라우저의 다른 탭에서 다른 계정으로
 *    로그인했으면 쿠키(세션)가 바뀌어, 그대로 보내면 "그 계정"의 이름이 바뀐다. 그래서 보내지 않는다.
 *    로그인이 끝났거나 계정이 바뀌었으면 이전 계정 캐시를 resetAccountState로 정리한다.
 * 2) PATCH는 한 번만 보낸다. 실패해도 여기서 다시 보내지 않는다(오류는 호출한 화면이 처리).
 * 3) 응답의 회원 ID도 확인한다. 확인과 저장 사이에 계정이 바뀐 드문 경우를 잡기 위해서다.
 * 4) 진행 중인 GET /api/me를 멈춘 뒤 새 회원 정보를 넣는다. 저장 전에 출발한 늦은 조회가
 *    도착해서 헤더의 이름을 옛 이름으로 되돌리는 것을 막는다.
 * 5) 판매자 이름 등이 보이는 다른 조회는 오래된 것으로 표시만 한다(지금 보고 있는 화면만 다시 조회).
 */
export async function saveDisplayName(client: QueryClient, expectedMemberId: string, displayName: string): Promise<SaveNameOutcome> {
  let current: Member
  try {
    current = await getMemberSession()
  } catch (error) {
    if (!isUnauthorized(error)) throw error
    await resetAccountState(client, null)
    return { kind: 'signedOut' }
  }
  if (current.id !== expectedMemberId) {
    await resetAccountState(client, current)
    return { kind: 'accountChanged' }
  }
  const updated = await updateMe({ displayName })
  if (updated.id !== expectedMemberId) {
    await resetAccountState(client, updated)
    return { kind: 'accountChanged' }
  }
  await client.cancelQueries({ queryKey: memberQueryKey })
  client.setQueryData(memberQueryKey, updated)
  void client.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
  return { kind: 'saved', member: updated }
}
