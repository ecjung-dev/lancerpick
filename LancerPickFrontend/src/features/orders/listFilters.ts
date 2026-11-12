import { orderStatuses } from './status.ts'
import { isCalendarDate as isOrderListDate } from '../../shared/lib/dateRange.ts'

type OrderSide = 'BUYER' | 'SELLER'
type OrderSort = 'latest' | 'oldest'

/** 거래 내역 목록 조건. 빈 문자열은 조건 없음이다. 날짜는 한국 날짜(yyyy-MM-dd)다. */
export interface OrderListFilters {
  side: OrderSide
  status: string
  q: string
  from: string
  to: string
  sort: OrderSort
  page: number
}

export const MAX_ORDER_KEYWORD_LENGTH = 100
export { koreanToday, recentMonths } from '../../shared/lib/dateRange.ts'
export { isOrderListDate }

export function keywordLength(value: string) {
  return [...value].length
}

/** URL 값을 서버가 받는 조건으로 정리한다. 잘못된 값은 조건 없음으로 바꿔 400 대신 전체 조회가 되게 한다. */
export function readOrderListFilters(params: URLSearchParams): OrderListFilters {
  const side = params.get('side') === 'SELLER' ? 'SELLER' : 'BUYER'
  const status = orderStatuses.find((value) => value === params.get('status')) ?? ''
  const rawQ = (params.get('q') ?? '').trim()
  const q = keywordLength(rawQ) <= MAX_ORDER_KEYWORD_LENGTH ? rawQ : ''
  let from = params.get('from') ?? ''
  let to = params.get('to') ?? ''
  if (!isOrderListDate(from)) from = ''
  if (!isOrderListDate(to)) to = ''
  if (from && to && from > to) {
    from = ''
    to = ''
  }
  const sort = params.get('sort') === 'oldest' ? 'oldest' : 'latest'
  const requestedPage = Number(params.get('page') ?? 0)
  const page =
    Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 2147483647
      ? requestedPage
      : 0
  return { side, status, q, from, to, sort, page }
}

/** 기본값(첫 페이지·최신순·빈 조건)은 URL에서 뺀다. */
export function orderListSearch(filters: OrderListFilters): Record<string, string> {
  const result: Record<string, string> = { side: filters.side }
  if (filters.status) result.status = filters.status
  if (filters.q) result.q = filters.q
  if (filters.from) result.from = filters.from
  if (filters.to) result.to = filters.to
  if (filters.sort === 'oldest') result.sort = 'oldest'
  if (filters.page > 0) result.page = String(filters.page)
  return result
}
