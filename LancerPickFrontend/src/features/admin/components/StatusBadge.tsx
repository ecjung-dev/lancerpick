import type { ReactNode } from 'react'
import type { StatusTone } from '../statusTone'
import styles from '../pages/Admin.module.css'

const TONE_CLASS: Record<StatusTone, string | undefined> = {
  ok: styles.toneOk,
  neutral: undefined,
  warn: styles.toneWarn,
  danger: styles.toneDanger,
  strong: styles.toneStrong,
}

/** 관리자 화면 상태 배지. 라벨·코드는 호출한 쪽이 정하고 여기서는 톤에 맞는 모양만 입힌다. */
export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  const toneClass = TONE_CLASS[tone]
  return <span className={toneClass ? `${styles.badge} ${toneClass}` : styles.badge} data-tone={tone}>{children}</span>
}
