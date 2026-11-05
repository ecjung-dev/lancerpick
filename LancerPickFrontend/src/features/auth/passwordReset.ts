import type { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client.ts'
import { resetPassword } from './api.ts'
import { isUnclearResult, recheckSession } from './session.ts'
import type { PasswordResetInput } from './types.ts'

/** 다시 요청하기 버튼을 막는 시간(초). 서버 제한(이메일당 최소 60초)과 같다. 화면 편의용이고 실제 제한은 서버가 한다. */
export const RESET_REQUEST_COOLDOWN_SECONDS = 60

/** 서버 토큰 형식: URL-safe Base64 43자(32바이트). 다른 값은 서버로 보내지 않고 바로 "유효하지 않은 링크"로 안내한다. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/

/**
 * 메일 링크의 주소 뒤쪽(#token=...)에서 토큰을 꺼낸다. 없거나 형식이 틀리면 null.
 * 토큰을 # 뒤(fragment)에 두는 이유: 브라우저가 서버로 보내지 않아 접속 로그·Referer에 남지 않는다(3인 설계 7.1).
 * 꺼낸 토큰은 화면 상태(메모리)에만 두고 localStorage·주소·로그에 남기지 않는다.
 */
export function readResetToken(hash: string): string | null {
  const text = hash.startsWith('#') ? hash.slice(1) : hash
  const token = new URLSearchParams(text).get('token')
  return token !== null && TOKEN_PATTERN.test(token) ? token : null
}

/**
 * 비밀번호 재설정 실행. POST는 한 번만 보낸다(토큰은 일회용이라 재전송하면 안 됨).
 * 성공하면 이 브라우저의 로그인 상태를 서버에 다시 확인한다(요청을 다시 보내지는 않음).
 * - 같은 계정으로 로그인돼 있었다면 서버가 auth_version 변경으로 세션을 폐기하므로 로그아웃 상태가 되고 개인 캐시가 지워진다.
 * - 다른 계정으로 로그인돼 있었다면 그 계정은 그대로다(남의 세션을 지우지 않는다).
 * 실패는 그대로 던진다. 화면이 resetFailureKind로 나눠 처리한다.
 */
export async function submitPasswordReset(client: QueryClient, input: PasswordResetInput) {
  await resetPassword(input)
  await recheckSession(client)
}

/**
 * 재설정 실패를 화면 처리 단위로 나눈다(문구가 아니라 code 기준, 개발 규칙 5절).
 * invalidLink: 링크가 틀림·만료·이미 사용됨 → 새 링크 요청 안내
 * fields: 새 비밀번호 규칙 위반 → 입력칸 안내(토큰은 그대로 쓸 수 있음)
 * csrf: 요청 정보 만료 → 다시 시도 안내
 * unclear: 통신 오류·5xx → 바뀌었을 수 있다. 실패로 단정하지 않고 새 비밀번호 로그인부터 시도하도록 안내
 */
export type ResetFailureKind = 'invalidLink' | 'fields' | 'csrf' | 'unclear' | 'other'

export function resetFailureKind(error: unknown): ResetFailureKind {
  if (isUnclearResult(error)) return 'unclear'
  if (!(error instanceof ApiError)) return 'other'
  if (error.code === 'PASSWORD_RESET_TOKEN_INVALID') return 'invalidLink'
  if (error.code === 'VALIDATION_FAILED') return 'fields'
  if (error.code === 'CSRF_INVALID') return 'csrf'
  return 'other'
}
