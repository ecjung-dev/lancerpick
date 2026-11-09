import { useEffect, useState } from 'react'
import { isDisplayNameChangeLocked } from './nameChangePolicy'

/** 열린 화면에서도 변경 가능 시각에 버튼을 다시 활성화한다. 긴 타이머는 나누어 예약한다. */
export function useDisplayNameCooldown(availableAt: string | null) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!availableAt) return
    const deadline = Date.parse(availableAt)
    if (!Number.isFinite(deadline) || deadline <= now) return
    const timer = window.setTimeout(
      () => setNow(Date.now()),
      Math.min(Math.max(0, deadline - Date.now() + 10), 2_147_483_647),
    )
    return () => window.clearTimeout(timer)
  }, [availableAt, now])
  return isDisplayNameChangeLocked(availableAt, now)
}
