import { Link } from 'react-router'
import styles from './NotFoundPage.module.css'

/** 정의되지 않은 주소. 기준안의 빈 결과 상자와 주/보조 이동 링크를 쓴다. */
export function NotFoundPage() {
  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>PAGE NOT FOUND</p>
      <h1>페이지를 찾을 수 없어요.</h1>
      <p className={styles.description}>주소가 바뀌었거나 삭제된 페이지예요. 주소를 다시 확인하거나 아래에서 이동해 주세요.</p>
      <div className={styles.actions}>
        <Link className={styles.primary} to="/products">서비스 둘러보기</Link>
        <Link className={styles.secondary} to="/">홈으로</Link>
      </div>
    </section>
  )
}
