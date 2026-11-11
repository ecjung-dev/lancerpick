import styles from '../pages/Admin.module.css'

/**
 * 관리자 목록·이력 공통 이전/다음 페이지.
 * - 한 페이지뿐이고 첫 페이지에 있으면 숨긴다.
 * - 뒤 페이지에서 결과가 줄어 지금 페이지가 범위를 벗어났으면(주소의 page가 크거나 항목이 사라짐)
 *   "첫 페이지로"를 함께 보여 바로 돌아갈 수 있게 한다.
 */
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
  if (page === 0 && totalPages <= 1) return null
  const outOfRange = page > 0 && page >= totalPages
  return (
    <nav className={styles.pager} aria-label={label}>
      {outOfRange && (
        <button type="button" disabled={busy} onClick={() => onPage(0)}>
          첫 페이지로
        </button>
      )}
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
