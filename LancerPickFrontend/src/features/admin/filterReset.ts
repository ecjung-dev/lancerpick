/**
 * 관리자 목록 "초기화" 버튼을 켤지 정한다. 주소의 해당 필터 키·page(잘못된 값 포함)나
 * 아직 적용하지 않은 입력 초안이 하나라도 있으면 지울 것이 있다고 본다.
 * 금융 조회의 tab처럼 keys에 넣지 않은 값은 보지 않는다(초기화해도 유지).
 */
export function canResetFilter(params: URLSearchParams, keys: readonly string[], drafts: readonly string[] = []) {
  return [...keys, 'page'].some((key) => (params.get(key) ?? '') !== '')
    || drafts.some((value) => value.trim() !== '')
}
