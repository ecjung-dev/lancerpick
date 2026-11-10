import { useState } from 'react'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { accessProblem } from '../access'
import { useMemberSanctions } from '../memberQueries'
import { restrictionLabels, sanctionStateLabels } from '../memberValidation'
import { AdminAccessNotice } from '../pages/AdminAccess'
import { AdminPager } from './AdminPager'
import styles from '../pages/Admin.module.css'

export function AdminMemberHistory({ memberId }: { memberId: string }) {
  const [page, setPage] = useState(0)
  const query = useMemberSanctions(memberId, page)
  const problem = accessProblem(query.error)
  return (
    <section className={styles.block} aria-label="제재 이력">
      <h2>제재 이력</h2>
      {problem ? (
        <AdminAccessNotice problem={problem} />
      ) : query.isError ? (
        <AsyncState
          title="제재 이력을 불러오지 못했어요"
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      ) : !query.data ? (
        <p role="status">이력을 불러오는 중입니다.</p>
      ) : (
        <>
          <p>총 {query.data.totalElements}건 · 만료·해제 기록도 보존됩니다.</p>
          {!query.data.items.length ? (
            <p>제재 이력이 없습니다.</p>
          ) : (
            <ul className={styles.list}>
              {query.data.items.map((sanction) => (
                <li key={sanction.id}>
                  <strong>
                    #{sanction.id} · {restrictionLabels[sanction.kind]} ·{' '}
                    {sanctionStateLabels[sanction.state]}
                  </strong>
                  <p className={styles.text}>{sanction.reason}</p>
                  <p>
                    적용 관리자 #{sanction.appliedBy} ·{' '}
                    {formatDateTime(sanction.startsAt)} ~{' '}
                    {formatDateTime(sanction.endsAt)}
                  </p>
                  {sanction.revokedAt && (
                    <p className={styles.text}>
                      해제 관리자 #{sanction.revokedBy} ·{' '}
                      {formatDateTime(sanction.revokedAt)} ·{' '}
                      {sanction.revokeReason}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
          <AdminPager
            page={page}
            totalPages={query.data.totalPages}
            busy={query.isFetching}
            onPage={setPage}
            label="제재 이력 페이지"
          />
        </>
      )}
    </section>
  )
}
