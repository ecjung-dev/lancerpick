import type { ReactNode } from 'react'
import { StatusBadge as SharedStatusBadge } from '../../../shared/components/StatusBadge'
import type { StatusTone } from '../../../shared/lib/statusTone'
import styles from '../pages/Admin.module.css'

/** 관리자 화면 상태 배지. 공통 배지에 관리자 표의 오른쪽 여백만 더한다. */
export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <SharedStatusBadge tone={tone} className={styles.badgeGap}>{children}</SharedStatusBadge>
}
