import { NavLink } from 'react-router'
import styles from './Admin.module.css'

/** 관리자 화면 사이 이동. 권한은 각 화면의 서버 응답(401·403)으로 판단한다. */
export function AdminNav() {
  return <nav className={styles.tabs} aria-label="관리자 메뉴">
    {/* end: /admin/products 등 하위 화면에서는 "운영 현황"을 현재 위치로 표시하지 않는다. */}
    <NavLink to="/admin" end>운영 현황</NavLink>
    <NavLink to="/admin/products">상품 운영</NavLink>
    <NavLink to="/admin/members">회원 관리</NavLink>
    <NavLink to="/admin/inquiries?status=OPEN">운영 문의</NavLink>
    <NavLink to="/admin/review-timers">자동 확정 타이머</NavLink>
    <NavLink to="/admin/finance">금융 조회</NavLink>
    <NavLink to="/admin/outbox">알림 처리</NavLink>
  </nav>
}
