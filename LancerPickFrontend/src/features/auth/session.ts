import type { QueryClient } from '@tanstack/react-query'
import { ApiError, clearCsrfToken } from '../../shared/api/client.ts'
import { clearWalletCache } from '../wallet/cache.ts'
import { clearAccountQueries } from '../../shared/api/accountCache.ts'
import { getCurrentMember } from './api.ts'
import type { Member } from './types.ts'

/** 현재 로그인 회원 조회 결과의 캐시 이름표. 헤더·화면이 같이 쓴다. */
export const memberQueryKey = ['auth', 'me'] as const

/**
 * 계정 전환 세대. resetAccountState가 로그인 상태를 바꿀 때마다 올린다.
 * recheckSession의 /api/me는 Query 캐시 밖 요청이라 cancelQueries로 멈추지 않으므로,
 * 그 사이 로그인·로그아웃이 끝났으면 늦은 응답을 반영하지 않는 데 쓴다.
 */
let accountGeneration = 0

/**
 * 로그인·로그아웃·계정 전환 직후 호출한다(개발 규칙 4절, 프런트 공통 가이드).
 * 1) 서버가 바꾼 CSRF 토큰을 다시 받도록 기존 토큰을 버린다.
 * 2) 이전 계정의 지갑·주문 등 조회를 중단하고 캐시를 지운다.
 * 3) 진행 중이던 로그인 상태 조회는 취소하고, 새 로그인 상태(member 또는 null)를 바로 반영한다.
 * ['auth'] 캐시는 지우지 않고 값만 바꾼다. 지우면 헤더가 보고 있던 캐시와 연결이 끊긴다.
 */
export async function resetAccountState(queryClient: QueryClient, member: Member | null) {
  accountGeneration += 1
  clearCsrfToken()
  // 진행 중인 GET /api/me를 먼저 멈춘다. 멈추지 않으면 그 늦은 응답이 아래에서 넣은 새 상태를 덮어쓴다.
  await queryClient.cancelQueries({ queryKey: memberQueryKey })
  await clearPersonalCaches(queryClient)
  queryClient.setQueryData(memberQueryKey, member)
}

/**
 * 이전 계정의 개인 정보(지갑·충전 진행 정보·주문·판매자 프로필 등)를 지운다.
 * 진행 중인 조회는 먼저 멈춰서 늦은 응답이 다시 캐시를 채우지 않게 한다. ['auth']는 호출한 쪽이 관리한다.
 */
async function clearPersonalCaches(queryClient: QueryClient) {
  await clearWalletCache(queryClient) // 지갑 캐시 + 토스 충전 진행 정보(clearCheckoutState)
  await clearAccountQueries(queryClient)
}

/**
 * 서버가 처리했는지 알 수 없는 실패: 통신 오류(status 0)·5xx.
 * 예) 서버는 로그인을 끝냈는데 응답만 못 받은 경우. 실패로 단정하지 않는다(개발 규칙 4절).
 */
export function isUnclearResult(error: unknown) {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500
}

/**
 * recheckSession 결과. same=같은 계정 그대로, signedOut=로그인 안 함, changed=다른 계정, unknown=확인 실패,
 * superseded=확인하는 동안 다른 곳에서 로그인·로그아웃이 끝나 결과를 반영하지 않음(그쪽 상태가 최신).
 */
export type RecheckOutcome = 'same' | 'signedOut' | 'changed' | 'unknown' | 'superseded'

/**
 * 로그인·로그아웃 결과가 불명확할 때 호출한다. 요청 자체는 다시 보내지 않는다.
 * 1) 서버가 CSRF 토큰을 이미 바꿨을 수 있으니 버린다.
 * 2) 진행 중인 /api/me를 멈추고 서버에 직접 다시 물어본다.
 * 3) 로그인 안 함·다른 계정이면 resetAccountState로 이전 계정의 개인 캐시를 전부 정리한다.
 * 4) 다시 묻는 것도 실패하면 개인 캐시를 정리하고 로그인 상태는 다음 화면 표시 때 확인한다(이전 정보를 남기지 않는 쪽).
 * 5) 응답을 기다리는 동안 resetAccountState(로그인·로그아웃)가 실행됐으면 늦은 결과로 새 상태를 덮지 않고 superseded를 돌려준다.
 * 호출한 화면은 signedOut·changed·unknown이면 보고 있던 보호 화면에서 벗어나야 한다(떠 있는 화면은 캐시 삭제만으로 다시 그려지지 않는다).
 */
export async function recheckSession(queryClient: QueryClient): Promise<RecheckOutcome> {
  const generation = accountGeneration
  const previous = queryClient.getQueryData<Member | null>(memberQueryKey)
  clearCsrfToken()
  await queryClient.cancelQueries({ queryKey: memberQueryKey })
  let current: Member | null
  try {
    current = await getCurrentMember()
  } catch {
    if (generation !== accountGeneration) return 'superseded'
    await clearPersonalCaches(queryClient)
    await queryClient.invalidateQueries({ queryKey: memberQueryKey })
    return 'unknown'
  }
  if (generation !== accountGeneration) return 'superseded'
  if (current && previous && current.id === previous.id) {
    queryClient.setQueryData(memberQueryKey, current)
    return 'same'
  }
  await resetAccountState(queryClient, current)
  return current ? 'changed' : 'signedOut'
}

const pendingRechecks = new WeakMap<QueryClient, Promise<RecheckOutcome>>()
/** 마지막으로 재확인한 로그인 상태 캐시의 갱신 시각. 재확인이 실패(unknown)해 값이 그대로면 다시 묻지 않는다. */
const lastRecheckedAt = new WeakMap<QueryClient, number>()

/**
 * 조회가 401을 받았는데 헤더는 아직 로그인 회원을 보이고 있을 때 호출한다(QueryCache 공통 onError).
 * 다른 곳에서 로그아웃·비밀번호 변경으로 세션이 끝난 탭이 새로고침 전까지 이전 이름·알림 수를 보이지 않게 한다.
 * - 401이 아니거나 이미 로그인 안 함(null)·확인 전이면 아무것도 하지 않는다(반복 조회 방지).
 * - 동시에 받은 401은 재확인 한 번으로 묶는다.
 * - 재확인이 실패해 로그인 상태가 그대로면 같은 상태로는 다시 묻지 않는다(캐시 정리 → 재조회 → 401 반복 방지).
 * 변경 요청(mutation)의 401은 각 화면이 결과를 안내하며 정리하므로 여기서 다루지 않는다.
 */
export function recheckAfterUnauthorized(queryClient: QueryClient, error: unknown): Promise<RecheckOutcome | undefined> {
  if (!(error instanceof ApiError) || error.status !== 401) return Promise.resolve(undefined)
  const pending = pendingRechecks.get(queryClient)
  if (pending) return pending
  if (!queryClient.getQueryData<Member | null>(memberQueryKey)) return Promise.resolve(undefined)
  const updatedAt = queryClient.getQueryState(memberQueryKey)?.dataUpdatedAt ?? 0
  if (lastRecheckedAt.get(queryClient) === updatedAt) return Promise.resolve(undefined)
  lastRecheckedAt.set(queryClient, updatedAt)
  const recheck = recheckSession(queryClient).finally(() => pendingRechecks.delete(queryClient))
  pendingRechecks.set(queryClient, recheck)
  return recheck
}

/**
 * 로그인 후 돌아갈 주소. 같은 사이트 안의 경로만 허용한다.
 * "//evil.com" 이나 "https://..." 를 넣어 다른 사이트로 보내는 공격(오픈 리다이렉트)을 막는다.
 */
export function safeNextPath(next: string | null | undefined) {
  if (!next || !next.startsWith('/') || next.startsWith('//')) return '/'
  // 브라우저는 주소의 탭·줄바꿈 같은 제어 문자를 지우고 역슬래시를 /로 읽는다.
  // 그래서 "/(탭)/evil.com"이 "//evil.com"(다른 사이트)이 될 수 있어, 이런 글자가 있으면 홈으로 보낸다.
  for (const character of next) {
    const code = character.codePointAt(0) ?? 0
    if (code <= 0x1f || code === 0x7f || character === '\\') return '/'
  }
  if (next.startsWith('/login') || next.startsWith('/signup')) return '/'
  return next
}
