/**
 * 상세 화면의 "목록으로" 주소. 목록에서 넘겨준 state.from이 같은 목록 경로(검색 조건 포함)일 때만 쓰고,
 * 그 밖의 값은 fallback으로 바꾼다. 다른 관리자 화면이나 외부 주소로 돌아가지 않게 목록 경로를 정확히 비교한다.
 */
export function adminBackPath(from: unknown, listPath: string, fallback: string = listPath) {
  return typeof from === 'string' && (from === listPath || from.startsWith(`${listPath}?`)) ? from : fallback
}
