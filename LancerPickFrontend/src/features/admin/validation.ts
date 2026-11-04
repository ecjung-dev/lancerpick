import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { AdminProductFilter, SaleStatus } from './types.ts'

export const KEYWORD_MAX = 100
export const REASON_MAX = 1000

/**
 * URL 쿼리에서 목록 필터를 읽는다. 허용하지 않는 값은 "조건 없음"으로 바꿔
 * 잘못된 주소로 들어와도 서버에 400을 일으키는 요청을 보내지 않는다(API 9절 허용값).
 */
export function readFilter(params: URLSearchParams): AdminProductFilter {
  const hidden = params.get('isHidden')
  const sale = params.get('saleStatus')
  return {
    keyword: normalizeText(params.get('keyword') ?? ''),
    isHidden: hidden === 'true' || hidden === 'false' ? hidden : '',
    saleStatus: sale === 'ON_SALE' || sale === 'STOPPED' ? (sale as SaleStatus) : '',
  }
}

/** 검색어는 정리 후 최대 100자. 넘으면 서버에 보내지 않고 화면에서 안내한다. */
export function keywordError(keyword: string) {
  return codePointLength(keyword) > KEYWORD_MAX ? `검색어는 최대 ${KEYWORD_MAX}자예요.` : undefined
}

/** 운영 사유는 공백 정리 후 1~1000자(API 10절). 서버도 같은 규칙으로 다시 검사한다. */
export function validateReason(value: string) {
  const reason = normalizeText(value)
  const length = codePointLength(reason)
  if (length < 1) return { reason, error: '사유를 입력해 주세요.' }
  if (length > REASON_MAX) return { reason, error: `사유는 최대 ${REASON_MAX}자예요.` }
  return { reason, error: undefined }
}

export function isValidProductId(id: string) {
  return /^[0-9]{1,19}$/.test(id) && BigInt(id) > 0n && BigInt(id) <= 9223372036854775807n
}

export function saleStatusLabel(status: SaleStatus) {
  return status === 'ON_SALE' ? '판매 중' : '판매 중지'
}
