import styles from '../pages/Admin.module.css'

export function AdminPager({
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
  return (
    <nav className={styles.pager} aria-label={label}>
      <button
        type="button"
        disabled={busy || page === 0}
        onClick={() => onPage(page - 1)}
      >
        이전
      </button>
      <span>{page + 1}페이지</span>
      <button
        type="button"
        disabled={busy || page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        다음
      </button>
    </nav>
  )
}
