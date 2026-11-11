import styles from './Pager.module.css'

/**
 * 사용자 목록 공통 이전/다음 페이지(docs/architecture.md#ui-consistency).
 * - 첫 페이지 하나뿐이면 숨긴다.
 * - 주소의 page가 전체 페이지를 넘으면 페이지 번호 대신 "첫 페이지로"만 보여 준다.
 */
export function Pager({
  page,
  totalPages,
  busy,
  onPage,
  label,
}: {
  page: number
  totalPages: number
  busy: boolean
  onPage: (page: number) => void
  label: string
}) {
  if (page === 0 && totalPages <= 1) return null
  if (page >= totalPages) {
    return (
      <nav className={styles.pager} aria-label={label}>
        <button type="button" className={styles.button} disabled={busy} onClick={() => onPage(0)}>첫 페이지로</button>
      </nav>
    )
  }
  return (
    <nav className={styles.pager} aria-label={label}>
      <button type="button" className={styles.button} disabled={busy || page === 0} onClick={() => onPage(page - 1)}>이전</button>
      <span aria-live="polite">{page + 1}페이지 / 총 {totalPages}페이지</span>
      <button type="button" className={styles.button} disabled={busy || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</button>
    </nav>
  )
}
