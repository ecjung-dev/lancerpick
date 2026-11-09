import { isCalendarDate } from '../../shared/lib/dateRange.ts'

export interface SettlementFilters { from: string; to: string; sort: 'latest' | 'oldest'; page: number }
export type SettlementPeriod = Pick<SettlementFilters, 'from' | 'to'>

export function validSettlementDate(value: string) {
  return isCalendarDate(value) && Number(value.slice(0, 4)) >= 1000
}

export function readSettlementFilters(params: URLSearchParams): SettlementFilters {
  let from = params.get('from') ?? ''
  let to = params.get('to') ?? ''
  if (!validSettlementDate(from)) from = ''
  if (!validSettlementDate(to)) to = ''
  if (from && to && from > to) { from = ''; to = '' }
  const requested = Number(params.get('page') ?? 0)
  return { from, to, sort: params.get('sort') === 'oldest' ? 'oldest' : 'latest',
    page: Number.isInteger(requested) && requested >= 0 && requested <= 2147483647 ? requested : 0 }
}

export function settlementSearch(filters: SettlementFilters) {
  const params = new URLSearchParams()
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.sort === 'oldest') params.set('sort', filters.sort)
  if (filters.page > 0) params.set('page', String(filters.page))
  return params
}
