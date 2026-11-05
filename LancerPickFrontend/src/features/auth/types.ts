/** API 2절 Member. ID는 JSON 문자열이다(큰 숫자 정밀도 손실 방지). */
export type MemberRole = 'USER' | 'ADMIN'

export interface Member {
  id: string
  email: string
  displayName: string
  role: MemberRole
  hasSellerProfile: boolean
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

export interface SignupInput {
  email: string
  password: string
  displayName: string
}
