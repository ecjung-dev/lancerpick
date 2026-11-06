import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { ChangePasswordInput, LoginInput, SignupInput } from './types.ts'

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
export type PasswordChangeErrors = Partial<Record<'currentPassword' | 'newPassword' | 'newPasswordConfirm', string>>
export type PasswordResetErrors = Partial<Record<'newPassword' | 'newPasswordConfirm', string>>

const PASSWORD_RULE_MESSAGE = '영문과 숫자를 포함해 8~64자로 입력해 주세요. 공백·한글은 쓸 수 없어요.'

export const DISPLAY_NAME_MAX = 30

/** 표시 이름: 공백 정리 후 1~30자(코드 포인트). 가입과 내 정보 수정이 같은 규칙을 쓴다. 중복은 허용한다. */
export function validateDisplayName(value: string) {
  const displayName = normalizeText(value)
  const length = codePointLength(displayName)
  const error = length < 1 || length > DISPLAY_NAME_MAX ? '표시 이름은 공백 정리 후 1~30자여야 해요.' : undefined
  return { displayName, error }
}

export function normalizeEmail(value: string) {
  return normalizeText(value).toLowerCase()
}

/** 이메일: 정리 후 비어 있음·형식 오류 안내. 가입과 비밀번호 재설정 요청이 같은 규칙을 쓴다(서버 EmailRules). */
function emailError(email: string) {
  const length = codePointLength(email)
  if (!email) return '이메일을 입력해 주세요.'
  if (length < 3 || length > 254 || !EMAIL_PATTERN.test(email)) return '이메일 형식을 확인해 주세요.'
  return undefined
}

/**
 * 가입 입력. 이메일은 인증 응답의 값을 그대로 쓰지만 같은 규칙으로 한 번 더 검사한다.
 * emailVerificationProof는 인증 응답의 증명이다(화면이 입력받지 않음, 검사하지 않고 그대로 보냄).
 */
export function validateSignup(draft: {
  email: string; password: string; passwordConfirm: string; displayName: string; emailVerificationProof?: string
}) {
  const input: SignupInput = {
    email: normalizeEmail(draft.email),
    password: draft.password, // 비밀번호는 절대 정리하지 않는다
    displayName: validateDisplayName(draft.displayName).displayName,
    emailVerificationProof: draft.emailVerificationProof ?? '',
  }
  const errors: SignupErrors = {}
  const emailProblem = emailError(input.email)
  if (emailProblem) errors.email = emailProblem

  if (!input.password) errors.password = '비밀번호를 입력해 주세요.'
  else if (!PASSWORD_PATTERN.test(input.password)) errors.password = PASSWORD_RULE_MESSAGE

  if (!draft.passwordConfirm) errors.passwordConfirm = '비밀번호를 한 번 더 입력해 주세요.'
  else if (draft.passwordConfirm !== draft.password) errors.passwordConfirm = '비밀번호가 서로 달라요.'

  const nameError = validateDisplayName(draft.displayName).error
  if (nameError) errors.displayName = nameError

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

/** 비밀번호 재설정 메일 요청. 이메일은 가입과 같이 정리·검사한다. */
export function validatePasswordResetRequest(draft: { email: string }) {
  const email = normalizeEmail(draft.email)
  const error = emailError(email)
  return { email, error, valid: error === undefined }
}

/**
 * 비밀번호 재설정의 새 비밀번호(POST /api/auth/password-resets). 정리하지 않는다.
 * 새 비밀번호는 가입과 같은 규칙, 확인 칸은 같은 값이어야 한다(확인 칸은 서버로 보내지 않음).
 * 예전 비밀번호를 모르는 상황이라 "현재와 같은지"는 검사하지 않는다(서버도 검사하지 않음).
 */
export function validatePasswordReset(draft: { newPassword: string; newPasswordConfirm: string }) {
  const errors: PasswordResetErrors = {}
  if (!draft.newPassword) errors.newPassword = '새 비밀번호를 입력해 주세요.'
  else if (!PASSWORD_PATTERN.test(draft.newPassword)) errors.newPassword = PASSWORD_RULE_MESSAGE

  if (!draft.newPasswordConfirm) errors.newPasswordConfirm = '새 비밀번호를 한 번 더 입력해 주세요.'
  else if (draft.newPasswordConfirm !== draft.newPassword) errors.newPasswordConfirm = '새 비밀번호가 서로 달라요.'

  return { newPassword: draft.newPassword, errors, valid: Object.keys(errors).length === 0 }
}

/**
 * 비밀번호 변경(POST /api/me/password). 세 값 모두 정리하지 않는다.
 * - 현재 비밀번호: 비어 있는지만 본다(로그인과 같은 기준). 맞는지는 서버가 확인한다.
 * - 새 비밀번호: 가입과 같은 규칙. 현재 비밀번호와 같으면 안 된다(서버 PASSWORD_UNCHANGED와 같은 기준).
 * - 새 비밀번호 확인: 새 비밀번호와 같아야 한다. 서버로 보내지 않는다.
 */
export function validatePasswordChange(draft: { currentPassword: string; newPassword: string; newPasswordConfirm: string }) {
  const input: ChangePasswordInput = { currentPassword: draft.currentPassword, newPassword: draft.newPassword }
  const errors: PasswordChangeErrors = {}
  if (!input.currentPassword) errors.currentPassword = '현재 비밀번호를 입력해 주세요.'

  if (!input.newPassword) errors.newPassword = '새 비밀번호를 입력해 주세요.'
  else if (!PASSWORD_PATTERN.test(input.newPassword)) errors.newPassword = PASSWORD_RULE_MESSAGE
  else if (input.newPassword === input.currentPassword) errors.newPassword = '현재 비밀번호와 다른 비밀번호를 입력해 주세요.'

  if (!draft.newPasswordConfirm) errors.newPasswordConfirm = '새 비밀번호를 한 번 더 입력해 주세요.'
  else if (draft.newPasswordConfirm !== draft.newPassword) errors.newPasswordConfirm = '새 비밀번호가 서로 달라요.'

  return { input, errors, valid: Object.keys(errors).length === 0 }
}
