import { ApiError } from '../api/client'
import { Button } from './Button'
import styles from './Common.module.css'

/** 모든 기능 화면에서 사용할 로딩·오류·빈 결과 표시. 거래 오류에는 별도 문맥을 더한다. */
export function AsyncState({ title, description, error, onRetry }: {
  title: string
  description?: string
  error?: unknown
  onRetry?: () => void
}) {
  return (
    <div className={styles.state} role={error ? 'alert' : 'status'}>
      <strong>{title}</strong>
      <p>{error instanceof ApiError ? error.message : description}</p>
      {error instanceof ApiError && error.traceId && <small>문의 코드: {error.traceId}</small>}
      {onRetry && <Button variant="secondary" onClick={onRetry}>다시 조회</Button>}
    </div>
  )
}
