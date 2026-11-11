import type { ReactNode } from 'react'
import { Link } from 'react-router'
import styles from '../pages/Admin.module.css'

/**
 * "구매자 #14"처럼 역할·번호 문구 전체를 회원 상세 링크로 감싼다(문구를 쪼개지 않는다).
 * from은 회원 상세의 "← 이전 화면"이 돌아올 주소다. 회원 상세가 허용 목록으로 다시 검사한다.
 */
export function MemberLink({ id, role, from, children }: { id: string; role?: string; from: string; children?: ReactNode }) {
  return <Link className={styles.inlineLink} to={`/admin/members/${id}`} state={{ from }}>
    {children ?? `${role ?? '회원'} #${id}`}
  </Link>
}
