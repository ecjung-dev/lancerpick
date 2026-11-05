import type { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client.ts'
import { changePassword, getMemberSession } from './api.ts'
import { resetAccountState } from './session.ts'
import type { ChangePasswordInput } from './types.ts'

/** changed=바뀜(세션 끝남), signedOut=이미 로그인이 끝나 보내지 않음, accountChanged=다른 계정으로 바뀌어 보내지 않음 */
export type ChangePasswordOutcome =
  | { kind: 'changed' }
  | { kind: 'signedOut' }
  | { kind: 'accountChanged' }

/**
 * 비밀번호 변경(POST /api/me/password). profile.ts의 saveDisplayName과 같은 순서를 쓴다.
 * 1) 지금 세션이 이 화면을 연 계정인지 먼저 확인한다(getMemberSession). 다른 탭에서 계정이 바뀌었으면 보내지 않는다.
 * 2) POST는 한 번만 보낸다. 실패해도 여기서 다시 보내지 않는다(오류는 호출한 화면이 처리).
 * 3) 성공(204)하면 서버가 이 세션을 끝냈으므로 CSRF 토큰·개인 캐시를 정리하고 로그인 상태를 null로 바꾼다.
 *    다른 기기의 세션은 서버가 다음 요청에서 끊는다(auth_version).
 */
export async function submitPasswordChange(
  client: QueryClient,
  expectedMemberId: string,
  input: ChangePasswordInput,
): Promise<ChangePasswordOutcome> {
  try {
    const current = await getMemberSession()
    if (current.id !== expectedMemberId) {
      await resetAccountState(client, current)
      return { kind: 'accountChanged' }
    }
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error
    await resetAccountState(client, null)
    return { kind: 'signedOut' }
  }
  await changePassword(input)
  await resetAccountState(client, null)
  return { kind: 'changed' }
}
