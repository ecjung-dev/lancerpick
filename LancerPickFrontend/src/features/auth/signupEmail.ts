import { ApiError } from '../../shared/api/client.ts'
import { isUnclearResult } from './session.ts'

/**
 * 가입 인증(POST /api/auth/signup-email-verifications) 실패를 화면 처리 단위로 나눈다(code 기준, 개발 규칙 5절).
 * invalidLink: 링크가 틀림·만료·이미 사용됨 → 인증 메일 다시 요청 안내
 * csrf: 요청 정보 만료 → 다시 시도 안내(링크는 아직 쓸 수 있음)
 * unclear: 통신 오류·5xx → 서버에서 인증됐을 수 있다. 같은 링크를 다시 보내지 않고 새 인증 메일을 받도록 안내
 */
export type VerifyFailureKind = 'invalidLink' | 'csrf' | 'unclear' | 'other'

export function verifyFailureKind(error: unknown): VerifyFailureKind {
  if (isUnclearResult(error)) return 'unclear'
  if (!(error instanceof ApiError)) return 'other'
  if (error.code === 'SIGNUP_EMAIL_TOKEN_INVALID') return 'invalidLink'
  if (error.code === 'CSRF_INVALID') return 'csrf'
  return 'other'
}

/**
 * 최종 가입(POST /api/members) 실패 분류.
 * proofInvalid: 인증이 만료·이미 사용됨 → 인증 메일 다시 요청
 * alreadyRegistered: 이미 가입된 이메일(409) → 로그인·비밀번호 재설정 안내
 * fields: 비밀번호·표시 이름 규칙 위반 → 입력칸 안내(증명은 그대로 쓸 수 있음)
 * unclear: 통신 오류·5xx → 가입됐을 수 있다. 다시 보내지 않고 로그인부터 시도하도록 안내
 */
export type SignupFailureKind = 'proofInvalid' | 'alreadyRegistered' | 'fields' | 'csrf' | 'unclear' | 'other'

export function signupFailureKind(error: unknown): SignupFailureKind {
  if (isUnclearResult(error)) return 'unclear'
  if (!(error instanceof ApiError)) return 'other'
  if (error.code === 'EMAIL_VERIFICATION_INVALID') return 'proofInvalid'
  if (error.code === 'EMAIL_ALREADY_EXISTS') return 'alreadyRegistered'
  if (error.code === 'VALIDATION_FAILED') return 'fields'
  if (error.code === 'CSRF_INVALID') return 'csrf'
  return 'other'
}
