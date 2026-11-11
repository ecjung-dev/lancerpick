/**
 * 상세 화면의 "목록으로" 주소. 목록에서 넘겨준 state.from이 같은 목록 경로(검색 조건 포함)일 때만 쓰고,
 * 그 밖의 값은 fallback으로 바꾼다. 다른 관리자 화면이나 외부 주소로 돌아가지 않게 목록 경로를 정확히 비교한다.
 */
export function adminBackPath(from: unknown, listPath: string, fallback: string = listPath) {
  return typeof from === 'string' && (from === listPath || from.startsWith(`${listPath}?`)) ? from : fallback
}

/** 돌아갈 수 있는 출처 하나. 문자열은 adminBackPath와 같이 "정확한 경로 또는 경로?…", 정규식은 경로 전체(쿼리 없음)와 비교한다. */
export interface AdminReturnOrigin { path: string | RegExp; label: string }

/**
 * 여러 관리자 화면에서 들어올 수 있는 상세 화면의 "← 이전 화면". 호출하는 화면이 허용할 출처를 직접 적고,
 * 넓은 접두사(/admin 등)는 쓰지 않는다. 맞는 출처가 없으면 fallback으로 간다.
 */
export function adminReturnTo(from: unknown, origins: readonly AdminReturnOrigin[], fallback: { to: string; label: string }) {
  if (typeof from !== 'string') return fallback
  for (const origin of origins) {
    const matched = typeof origin.path === 'string'
      ? from === origin.path || from.startsWith(`${origin.path}?`)
      : new RegExp(`^(?:${origin.path.source})$`).test(from)
    if (matched) return { to: from, label: origin.label }
  }
  return fallback
}
