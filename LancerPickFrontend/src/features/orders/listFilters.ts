import { orderStatuses } from './status.ts'

export type OrderSide = 'BUYER' | 'SELLER'
export type OrderSort = 'latest' | 'oldest'

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
const DATE = /^\d{4}-\d{2}-\d{2}$/

export function isOrderListDate(value: string) {
  if (!DATE.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  )
}

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

/** 한국 날짜 기준 오늘. */
export function koreanToday(now: number = Date.now()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(now))
}

/** 최근 n개월: 오늘(한국 날짜)부터 n개월 전 같은 날까지. 없는 날짜는 그 달 말일로 맞춘다. */
export function recentMonths(months: number, now: number = Date.now()) {
  const to = koreanToday(now)
  const [year, month, day] = to.split('-').map(Number)
  const target = new Date(Date.UTC(year, month - 1 - months, 1))
  const lastDay = new Date(
    Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0),
  ).getUTCDate()
  target.setUTCDate(Math.min(day, lastDay))
  return { from: target.toISOString().slice(0, 10), to }
}
