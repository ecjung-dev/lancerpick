import { NavLink } from 'react-router'
import styles from './Admin.module.css'

/** 관리자 화면 사이 이동. 권한은 각 화면의 서버 응답(401·403)으로 판단한다. */
export function AdminNav() {
  return <nav className={styles.tabs} aria-label="관리자 메뉴">
    <NavLink to="/admin/products">상품 운영</NavLink>
    <NavLink to="/admin/inquiries?status=OPEN">운영 문의</NavLink>
    <NavLink to="/admin/review-timers">자동 확정 타이머</NavLink>
  </nav>
}
