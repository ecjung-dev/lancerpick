import { useState, type FormEvent } from 'react'
import { koreanToday, recentMonths } from '../../../shared/lib/dateRange'
import { validSettlementDate, type SettlementFilters } from '../filters'
import styles from './SettlementFiltersForm.module.css'
import { codePointLength, normalizeText } from '../../../shared/lib/text'

export function SettlementFiltersForm({ filters, onApply }: {
  filters: SettlementFilters; onApply: (filters: SettlementFilters) => void
}) {
  const [from, setFrom] = useState(filters.from)
  const [to, setTo] = useState(filters.to)
  const [sort, setSort] = useState(filters.sort)
  const [error, setError] = useState('')
  const [q, setQ] = useState(filters.q ?? '')
  function apply(range: { from: string; to: string }, nextSort = sort) {
    const keyword = normalizeText(q)
    if (codePointLength(keyword) > 100) {
      setError('검색어는 100자 이하로 입력해 주세요.')
      return
    }
    if ((range.from && !validSettlementDate(range.from)) || (range.to && !validSettlementDate(range.to))) {
      setError('정산 기록일은 1000~9999년의 올바른 날짜로 입력해 주세요.')
      return
    }
    if (range.from && range.to && range.from > range.to) {
      setError('시작일이 종료일보다 늦습니다.')
      return
    }
    setError('')
    onApply({ ...range, sort: nextSort, page: 0, ...(keyword ? { q: keyword } : {}) })
  }
  function submit(event: FormEvent) { event.preventDefault(); apply({ from, to }) }
  function period(value: 'month' | 1 | 3 | 'all') {
    const today = koreanToday()
    const range = value === 'all' ? { from: '', to: '' }
      : value === 'month' ? { from: `${today.slice(0, 7)}-01`, to: today } : recentMonths(value)
    setFrom(range.from); setTo(range.to); apply(range)
  }
  function reset() {
    setFrom(''); setTo(''); setSort('latest'); setError(''); setQ('')
    onApply({ from: '', to: '', sort: 'latest', page: 0 })
  }
  return <form aria-label="판매 수익 조회 조건" className={styles.filters} onSubmit={submit}>
    <label className={styles.keyword}>상품명·주문번호<input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder="상품명 또는 주문번호" /></label>
    <div className={styles.period}>
      <label>시작일<input type="date" min="1000-01-01" max="9999-12-31" value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label>종료일<input type="date" min="1000-01-01" max="9999-12-31" value={to} onChange={e => setTo(e.target.value)} /></label>
      <div className={styles.presets} role="group" aria-label="판매 수익 조회 기간">
        <button type="button" onClick={() => period('month')}>이번 달</button>
        <button type="button" onClick={() => period(1)}>최근 1개월</button>
        <button type="button" onClick={() => period(3)}>최근 3개월</button>
        <button type="button" onClick={() => period('all')}>전체</button>
      </div>
    </div>
    <p className={styles.hint}>정산 기록일 · 한국 시간 기준, 종료일 당일까지 포함</p>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    <div className={styles.actions}>
      <label>정렬<select value={sort} onChange={e => setSort(e.target.value === 'oldest' ? 'oldest' : 'latest')}>
        <option value="latest">최신순</option><option value="oldest">오래된순</option>
      </select></label>
      <button type="submit" className={styles.submit}>조회</button>
      <button type="button" onClick={reset}>초기화</button>
    </div>
  </form>
}
