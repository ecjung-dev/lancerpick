import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { LoginInput, SignupInput } from './types.ts'

/**
 * 서버(MemberService·API 10절)와 같은 규칙. 화면 검사는 편의용이고 최종 판단은 서버가 한다.
 * - 이메일: 공백 정리 + 소문자, 3~254자, 형식 검사
 * - 비밀번호: 정리하지 않음. 공백 없는 ASCII(U+0021~U+007E) 8~64자, 영문 1자 + 숫자 1자 이상
 * - 표시 이름: 공백 정리 후 1~30자
 */
const EMAIL_PATTERN = new RegExp(
  "^(?=[^@]{1,64}@)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
    + '@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$',
)
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*[0-9])[\x21-\x7E]{8,64}$/

export type SignupErrors = Partial<Record<'email' | 'password' | 'passwordConfirm' | 'displayName', string>>
export type LoginErrors = Partial<Record<'email' | 'password', string>>

export function normalizeEmail(value: string) {
  return normalizeText(value).toLowerCase()
}

export function validateSignup(draft: { email: string; password: string; passwordConfirm: string; displayName: string }) {
  const input: SignupInput = {
    email: normalizeEmail(draft.email),
    password: draft.password, // 비밀번호는 절대 정리하지 않는다
    displayName: normalizeText(draft.displayName),
  }
  const errors: SignupErrors = {}
  const emailLength = codePointLength(input.email)
  if (!input.email) errors.email = '이메일을 입력해 주세요.'
  else if (emailLength < 3 || emailLength > 254 || !EMAIL_PATTERN.test(input.email)) errors.email = '이메일 형식을 확인해 주세요.'

  if (!input.password) errors.password = '비밀번호를 입력해 주세요.'
  else if (!PASSWORD_PATTERN.test(input.password)) errors.password = '영문과 숫자를 포함해 8~64자로 입력해 주세요. 공백·한글은 쓸 수 없어요.'

  if (!draft.passwordConfirm) errors.passwordConfirm = '비밀번호를 한 번 더 입력해 주세요.'
  else if (draft.passwordConfirm !== draft.password) errors.passwordConfirm = '비밀번호가 서로 달라요.'

  const nameLength = codePointLength(input.displayName)
  if (nameLength < 1 || nameLength > 30) errors.displayName = '표시 이름은 공백 정리 후 1~30자여야 해요.'

  return { input, errors, valid: Object.keys(errors).length === 0 }
}

/** 로그인은 형식을 검사하지 않는다(API 10절). 빈 값만 막는다. */
export function validateLogin(draft: { email: string; password: string }) {
  const input: LoginInput = { email: normalizeEmail(draft.email), password: draft.password }
  const errors: LoginErrors = {}
  if (!input.email) errors.email = '이메일을 입력해 주세요.'
  if (!input.password) errors.password = '비밀번호를 입력해 주세요.'
  return { input, errors, valid: Object.keys(errors).length === 0 }
}
