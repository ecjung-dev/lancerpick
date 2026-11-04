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

export interface SignupInput {
  email: string
  password: string
  displayName: string
}
