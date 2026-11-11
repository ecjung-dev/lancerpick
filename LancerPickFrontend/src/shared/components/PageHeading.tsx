import type { ReactNode } from 'react'
import styles from './PageHeading.module.css'

/**
 * 목록·관리 화면의 제목 영역(docs/architecture.md#ui-consistency).
 * 마이페이지 안에서는 레이아웃이 이미 홈·MY ACCOUNT를 보여 주므로 경로 표시·되돌아가기 링크를 함께 두지 않는다.
 */
export function PageHeading({ eyebrow, title, children }: { eyebrow: string; title: ReactNode; children?: ReactNode }) {
  return <header className={styles.heading}>
    <p className={styles.eyebrow}>{eyebrow}</p>
    <h1>{title}</h1>
    {children && <div className={styles.description}>{children}</div>}
  </header>
}
