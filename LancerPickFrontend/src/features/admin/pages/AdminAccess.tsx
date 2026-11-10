import { Link, useLocation } from 'react-router'
import { loginPath } from '../../auth/session'
import styles from './Admin.module.css'

export function AdminAccessNotice({ problem }: { problem: 'login' | 'forbidden' }) {
  const location = useLocation()
  if (problem === 'login') {
    return <div className={styles.notice} role="alert">
      <p>관리자 화면은 로그인 후 이용할 수 있어요.</p>
      <Link to={loginPath(location.pathname + location.search)}>로그인하기</Link>
    </div>
  }
  return <div className={styles.notice} role="alert">
    <p>접근 권한이 없어요. 관리자 계정으로 로그인해 주세요.</p>
    <Link to="/">홈으로</Link>
  </div>
}
