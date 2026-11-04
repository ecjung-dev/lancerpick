import { ApiError } from '../../shared/api/client.ts'

/** 401(로그인 필요)·403(관리자 아님)은 화면이 아니라 서버 응답으로 판단한다. CSRF 403은 권한 문제가 아니다. */
export function accessProblem(error: unknown): 'login' | 'forbidden' | undefined {
  if (!(error instanceof ApiError)) return undefined
  if (error.status === 401) return 'login'
  if (error.status === 403 && error.code === 'FORBIDDEN') return 'forbidden'
  return undefined
}
