import type { ReactNode } from 'react'
import styles from './AuthPage.module.css'

/** 로그인·회원가입이 같이 쓰는 화면 틀 */
export function AuthLayout({ title, description, children }: { title: string; description?: string; children: ReactNode }) {
  return <section className={styles.page}>
    <div className={styles.heading}>
      <p>ACCOUNT</p>
      <h1>{title}</h1>
      {description && <span>{description}</span>}
    </div>
    <div className={styles.card}>{children}</div>
  </section>
}
