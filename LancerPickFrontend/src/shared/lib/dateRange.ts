/** 한국 날짜 기준 오늘. 브라우저 기본 시간대에 의존하지 않는다. */
export function koreanToday(now: number = Date.now()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(now))
}

export function isCalendarDate(value: string) {
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

/** 달력 n개월 전 같은 날~오늘. 없는 날짜는 해당 월 말일로 맞춘다. */
export function recentMonths(months: number, now: number = Date.now()) {
  const to = koreanToday(now)
  const [year, month, day] = to.split('-').map(Number)
  const target = new Date(Date.UTC(year, month - 1 - months, 1))
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  target.setUTCDate(Math.min(day, lastDay))
  return { from: target.toISOString().slice(0, 10), to }
}
