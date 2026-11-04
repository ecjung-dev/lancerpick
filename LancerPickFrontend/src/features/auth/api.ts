import { ApiError, apiRequest } from '../../shared/api/client.ts'
import type { LoginInput, Member, SignupInput, UpdateMeInput } from './types.ts'

/** 로그인 안 한 상태(401)는 오류가 아니라 null로 돌려준다. 그 밖의 오류는 그대로 던진다. */
export async function getCurrentMember(
  signal?: AbortSignal,
): Promise<Member | null> {
  try {
    return await getMemberSession(signal)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

/** POST /api/auth/login. 성공 후 서버가 CSRF 토큰을 바꾸므로 호출한 쪽에서 resetAccountState를 실행한다. */
export function login(input: LoginInput) {
  return apiRequest<Member>('/auth/login', { method: 'POST', json: input })
}

/** POST /api/auth/logout. 204 본문 없음. */
export function logout() {
  return apiRequest<void>('/auth/logout', { method: 'POST' })
}

/**
 * PATCH /api/me. 표시 이름만 보낸다. 회원 ID는 본문에 넣지 않는다(서버가 세션에서 꺼낸다).
 * 실패해도 자동으로 다시 보내지 않는다. 호출 순서와 캐시 처리는 profile.ts의 saveDisplayName을 쓴다.
 */
export function updateMe(input: UpdateMeInput) {
  return apiRequest<Member>('/me', { method: 'PATCH', json: { displayName: input.displayName } })
}

/** POST /api/members. 가입 후 자동 로그인하지 않는다(API 2절). */
export function signup(input: SignupInput) {
  return apiRequest<Member>('/members', { method: 'POST', json: input })
}

/** 보호 기능의 현재 회원 확인. 401을 호출한 기능에 그대로 전달한다. */
export function getMemberSession(signal?: AbortSignal) {
  return apiRequest<Member>('/me', { signal })
}
