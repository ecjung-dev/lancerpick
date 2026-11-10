import { ApiError, apiRequest } from '../../shared/api/client.ts'
import type {
  ChangePasswordInput, LoginInput, Member, PasswordResetAccepted, PasswordResetInput, SignupEmailVerification,
  SignupInput, UpdateMeInput,
} from './types.ts'

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

/**
 * POST /api/me/password. 성공하면 204이고 서버가 지금 세션을 끝낸다(다시 로그인 필요).
 * 비밀번호는 정리하지 않고 그대로 보낸다. 실패해도 자동으로 다시 보내지 않는다.
 * 호출 순서와 캐시 처리는 password.ts의 submitPasswordChange를 쓴다.
 */
export function changePassword(input: ChangePasswordInput) {
  return apiRequest<void>('/me/password', {
    method: 'POST',
    json: { currentPassword: input.currentPassword, newPassword: input.newPassword },
  })
}

/**
 * POST /api/auth/password-reset-requests. 가입 여부와 상관없이 같은 202가 온다(메일 도착을 뜻하지 않음).
 * 실패해도 자동으로 다시 보내지 않는다. 다시 보내기 간격은 화면이 막는다.
 */
export function requestPasswordReset(email: string) {
  return apiRequest<PasswordResetAccepted>('/auth/password-reset-requests', { method: 'POST', json: { email } })
}

/**
 * POST /api/auth/account-guide-requests(아이디 안내). 가입한 이메일이면 그 주소로 "이 주소가 아이디"라는 안내를 보낸다.
 * 가입 여부와 상관없이 같은 202가 온다(응답 본문은 재설정과 같은 {message} 형식). 자동으로 다시 보내지 않는다.
 */
export function requestAccountGuide(email: string) {
  return apiRequest<PasswordResetAccepted>('/auth/account-guide-requests', { method: 'POST', json: { email } })
}

/**
 * POST /api/auth/password-resets. 성공하면 204. 토큰은 한 번만 쓸 수 있으므로 절대 자동으로 다시 보내지 않는다.
 * 호출 순서와 캐시 처리는 passwordReset.ts의 submitPasswordReset을 쓴다.
 */
export function resetPassword(input: PasswordResetInput) {
  return apiRequest<void>('/auth/password-resets', {
    method: 'POST',
    json: { token: input.token, newPassword: input.newPassword },
  })
}

/**
 * POST /api/members. 이메일 인증 증명(emailVerificationProof)이 필수다. 가입 후 자동 로그인하지 않는다(API 2절).
 * 증명은 일회용이라 실패해도 자동으로 다시 보내지 않는다.
 */
export function signup(input: SignupInput) {
  return apiRequest<Member>('/members', {
    method: 'POST',
    json: {
      email: input.email,
      password: input.password,
      displayName: input.displayName,
      emailVerificationProof: input.emailVerificationProof,
    },
  })
}

/**
 * POST /api/auth/signup-email-requests. 가입 여부와 상관없이 같은 202가 온다(메일 도착을 뜻하지 않음).
 * 실패해도 자동으로 다시 보내지 않는다. 다시 보내기 간격은 화면이 막는다.
 */
export function requestSignupEmail(email: string) {
  return apiRequest<PasswordResetAccepted>('/auth/signup-email-requests', { method: 'POST', json: { email } })
}

/**
 * POST /api/auth/signup-email-verifications. 메일 링크 토큰으로 인증하고 가입 증명을 받는다.
 * 링크 토큰은 한 번만 쓸 수 있으므로 절대 자동으로 다시 보내지 않는다.
 */
export function verifySignupEmail(token: string) {
  return apiRequest<SignupEmailVerification>('/auth/signup-email-verifications', { method: 'POST', json: { token } })
}

/** 보호 기능의 현재 회원 확인. 401을 호출한 기능에 그대로 전달한다. */
export function getMemberSession(signal?: AbortSignal) {
  return apiRequest<Member>('/me', { signal })
}
