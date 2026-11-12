import type { ReactNode } from 'react'
import type { StatusTone } from '../lib/statusTone'
import styles from './StatusBadge.module.css'

const TONE_CLASS: Record<StatusTone, string | undefined> = {
  ok: styles.toneOk,
  neutral: undefined,
  warn: styles.toneWarn,
  danger: styles.toneDanger,
  strong: styles.toneStrong,
}

/** 라벨은 호출한 쪽이 정하고 여기서는 톤에 맞는 모양만 입힌다. role은 상태 변화를 알려야 하는 곳(계약 상태)만 넘긴다. */
export function StatusBadge({ tone, children, className, role }: {
  tone: StatusTone
  children: ReactNode
  className?: string
  role?: 'status'
}) {
  const classes = [styles.badge, TONE_CLASS[tone], className].filter(Boolean).join(' ')
  return <span className={classes} data-tone={tone} role={role}>{children}</span>
}
