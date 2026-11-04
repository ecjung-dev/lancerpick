import type { QueryClient } from '@tanstack/react-query'
import { ApiError, clearCsrfToken } from '../../shared/api/client.ts'
import { clearWalletCache } from '../wallet/cache.ts'
import { clearAccountQueries } from '../../shared/api/accountCache.ts'
import { getCurrentMember } from './api.ts'
import type { Member } from './types.ts'

/** 현재 로그인 회원 조회 결과의 캐시 이름표. 헤더·화면이 같이 쓴다. */
export const memberQueryKey = ['auth', 'me'] as const

/**
 * 로그인·로그아웃·계정 전환 직후 호출한다(개발 규칙 4절, 프런트 공통 가이드).
 * 1) 서버가 바꾼 CSRF 토큰을 다시 받도록 기존 토큰을 버린다.
 * 2) 이전 계정의 지갑·주문 등 조회를 중단하고 캐시를 지운다.
 * 3) 진행 중이던 로그인 상태 조회는 취소하고, 새 로그인 상태(member 또는 null)를 바로 반영한다.
 * ['auth'] 캐시는 지우지 않고 값만 바꾼다. 지우면 헤더가 보고 있던 캐시와 연결이 끊긴다.
 */
export async function resetAccountState(queryClient: QueryClient, member: Member | null) {
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

/** recheckSession 결과. same=같은 계정 그대로, signedOut=로그인 안 함, changed=다른 계정, unknown=확인 실패 */
export type RecheckOutcome = 'same' | 'signedOut' | 'changed' | 'unknown'

/**
 * 로그인·로그아웃 결과가 불명확할 때 호출한다. 요청 자체는 다시 보내지 않는다.
 * 1) 서버가 CSRF 토큰을 이미 바꿨을 수 있으니 버린다.
 * 2) 진행 중인 /api/me를 멈추고 서버에 직접 다시 물어본다.
 * 3) 로그인 안 함·다른 계정이면 resetAccountState로 이전 계정의 개인 캐시를 전부 정리한다.
 * 4) 다시 묻는 것도 실패하면 개인 캐시를 정리하고 로그인 상태는 다음 화면 표시 때 확인한다(이전 정보를 남기지 않는 쪽).
 * 호출한 화면은 signedOut·changed·unknown이면 보고 있던 보호 화면에서 벗어나야 한다(떠 있는 화면은 캐시 삭제만으로 다시 그려지지 않는다).
 */
export async function recheckSession(queryClient: QueryClient): Promise<RecheckOutcome> {
  const previous = queryClient.getQueryData<Member | null>(memberQueryKey)
  clearCsrfToken()
  await queryClient.cancelQueries({ queryKey: memberQueryKey })
  let current: Member | null
  try {
    current = await getCurrentMember()
  } catch {
    await clearPersonalCaches(queryClient)
    await queryClient.invalidateQueries({ queryKey: memberQueryKey })
    return 'unknown'
  }
  if (current && previous && current.id === previous.id) {
    queryClient.setQueryData(memberQueryKey, current)
    return 'same'
  }
  await resetAccountState(queryClient, current)
  return current ? 'changed' : 'signedOut'
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
