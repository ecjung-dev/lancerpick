import { Link, type LinkProps } from 'react-router'
import styles from './BackLink.module.css'

/**
 * 화면 맨 위 되돌아가기 링크(docs/architecture.md#ui-consistency "뒤로가기").
 * 보조 버튼 모양이지만 페이지 이동이므로 링크 의미를 유지한다(role="button"을 붙이지 않는다).
 * 화살표는 장식이라 접근 가능한 이름은 children(목적지 이름)만 된다.
 */
export function BackLink({ className = '', children, ...props }: Omit<LinkProps, 'children'> & { children: string }) {
  return <Link className={`${styles.back} ${className}`.trim()} {...props}>
    <span aria-hidden="true">←</span>{children}
  </Link>
}
