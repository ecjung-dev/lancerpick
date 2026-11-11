export interface FieldError {
  field: string
  code: string
  message: string
}

interface ErrorResponse {
  code: string
  message: string
  fieldErrors: FieldError[]
  traceId: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly traceId?: string

  constructor(status: number, error: Omit<ErrorResponse, 'traceId'> & { traceId?: string }) {
    super(error.message)
    this.name = 'ApiError'
    this.status = status
    this.code = error.code
    this.fieldErrors = error.fieldErrors
    this.traceId = error.traceId
  }
}

/**
 * 서버가 401(로그인 필요·세션 만료)로 응답한 오류인지 판별한다. 세션 재확인·캐시 정리·안내는 호출한 쪽이 맡는다.
 * 거짓 분기에서 ApiError 자체가 제외되지 않도록 status까지 포함한 타입으로 좁힌다.
 */
export function isUnauthorized(error: unknown): error is ApiError & { readonly status: 401 } {
  return error instanceof ApiError && error.status === 401
}

interface CsrfToken { token: string; headerName: string }
let csrf: CsrfToken | undefined
let pendingCsrf: Promise<CsrfToken> | undefined
let csrfGeneration = 0

/** 로그인·로그아웃 성공 후 호출한다. 이전 계정의 Query 캐시도 별도로 비운다. */
export function clearCsrfToken() {
  csrfGeneration += 1
  csrf = undefined
  pendingCsrf = undefined
}

async function getCsrfToken(): Promise<CsrfToken> {
  if (csrf) return csrf
  if (pendingCsrf) return pendingCsrf
  const generation = csrfGeneration
  const request = apiRequest<CsrfToken>('/auth/csrf').then((value) => {
    if (generation !== csrfGeneration) return getCsrfToken()
    csrf = value
    return value
  }).finally(() => {
    if (pendingCsrf === request) pendingCsrf = undefined
  })
  pendingCsrf = request
  return request
}

type RequestOptions = Omit<RequestInit, 'body' | 'credentials'> & {
  json?: unknown
  responseType?: 'pdf'
  /** 클릭 시 계정 경계. CSRF 대기 후에도 전송 전에 검사한다. */
  isCurrentAccount?: () => boolean
}

/** 비공개 PDF도 세션·공통 오류 처리를 통과시킨다. 바이너리는 Query 캐시에 넣지 않는다. */
export const apiPdfRequest = (path: string, signal?: AbortSignal) =>
  apiRequest<Blob>(path, { signal, responseType: 'pdf', cache: 'no-store' })

/** 기능 api.ts에서만 사용한다. 변경 요청은 이 함수에서도 자동 재전송하지 않는다. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('API 상대 경로를 사용해 주세요.')
  const { json, responseType, isCurrentAccount, ...init } = options
  const assertCurrentAccount = () => {
    if (isCurrentAccount && !isCurrentAccount()) throw new DOMException('계정이 변경되어 요청을 중단했어요.', 'AbortError')
  }
  assertCurrentAccount()
  const requestGeneration = csrfGeneration
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', responseType === 'pdf' ? 'application/pdf, application/json' : 'application/json')
  if (json !== undefined) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await getCsrfToken()
    headers.set(token.headerName, token.token)
  }

  assertCurrentAccount()
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      ...init, method, headers, credentials: 'same-origin',
      body: json === undefined ? undefined : JSON.stringify(json),
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.', fieldErrors: [] })
  }

  if (response.ok && responseType === 'pdf') {
    if (response.headers.get('Content-Type')?.split(';')[0].trim() !== 'application/pdf')
      throw new ApiError(response.status, { code: 'INVALID_RESPONSE', message: 'PDF 응답 형식을 확인해 주세요.', fieldErrors: [] })
    return await response.blob() as T
  }
  const body: unknown = response.status === 204 ? undefined : await response.json().catch(() => undefined)
  if (!response.ok) {
    // Vite/Nginx의 HTML 오류나 빈 응답도 화면에 원문을 노출하지 않는다.
    const error = isErrorResponse(body) ? body : {
      code: 'HTTP_ERROR', message: '요청을 처리하지 못했어요. 다시 시도해 주세요.', fieldErrors: [],
      traceId: response.headers.get('X-Trace-Id') ?? undefined,
    }
    if ((response.status === 401 || error.code === 'CSRF_INVALID')
      && requestGeneration === csrfGeneration && (!isCurrentAccount || isCurrentAccount())) clearCsrfToken()
    throw new ApiError(response.status, error)
  }
  if (response.status !== 204 && body === undefined) {
    throw new ApiError(response.status, { code: 'INVALID_RESPONSE', message: '서버 응답 형식을 확인해 주세요.', fieldErrors: [] })
  }
  return body as T
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return typeof value === 'object' && value !== null
    && 'code' in value && typeof value.code === 'string'
    && 'message' in value && typeof value.message === 'string'
    && 'fieldErrors' in value && Array.isArray(value.fieldErrors)
    && 'traceId' in value && typeof value.traceId === 'string'
}
