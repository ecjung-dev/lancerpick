/** API 2절 Member. ID는 JSON 문자열이다(큰 숫자 정밀도 손실 방지). */
export type MemberRole = 'USER' | 'ADMIN'

export interface Member {
  id: string
  email: string
  displayName: string
  role: MemberRole
  hasSellerProfile: boolean
  /** null이면 가입 후 아직 표시 이름을 변경하지 않아 즉시 변경할 수 있다. */
  displayNameChangeAvailableAt: string | null
  /** 이메일 수신을 확인했는지(가입 인증 또는 기존 회원 이메일 인증). 예전 응답에 없으면 미인증으로 본다. */
  emailVerified?: boolean
}

export interface LoginInput {
  email: string
  password: string
}

/** PATCH /api/me 본문. 표시 이름만 바꿀 수 있다(API 2절). */
export interface UpdateMeInput {
  displayName: string
}

/** POST /api/me/password 본문. 두 값 모두 원문 그대로 보낸다(정리하지 않음). */
export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

/** POST /api/auth/password-resets 본문. 토큰·새 비밀번호 모두 원문 그대로 보낸다(정리하지 않음). */
export interface PasswordResetInput {
  token: string
  newPassword: string
}

/** POST /api/auth/password-reset-requests의 202 본문. 가입 여부와 상관없이 항상 같다. */
export interface PasswordResetAccepted {
  message: string
}

/**
 * POST /api/members 본문. emailVerificationProof는 가입 이메일 인증에서 받은 증명(필수, 2026-11-06부터).
 * 비밀번호는 정리하지 않고 그대로 보낸다.
 */
export interface SignupInput {
  email: string
  password: string
  displayName: string
  emailVerificationProof: string
}

/** POST /api/auth/signup-email-verifications 응답. proof는 화면 메모리에만 두고 가입 요청에 한 번 보낸다. */
export interface SignupEmailVerification {
  email: string
  proof: string
  proofExpiresAt: string
  /** 이미 가입된 이메일. 메일을 받은 본인이 인증한 뒤에만 알려 준다. */
  alreadyRegistered: boolean
}
