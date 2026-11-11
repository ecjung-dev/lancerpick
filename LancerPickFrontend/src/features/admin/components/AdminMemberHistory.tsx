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
        <p role="status">이력을 불러오고 있어요.</p>
      ) : (
        <>
          <p className={styles.count}>총 {query.data.totalElements}건 · 만료·해제 기록도 보존돼요.</p>
          {!query.data.items.length ? (
            <p className={styles.empty}>제재 이력이 없어요.</p>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">번호</th>
                    <th scope="col">종류</th>
                    <th scope="col">상태</th>
                    <th scope="col">사유</th>
                    <th scope="col">적용 관리자</th>
                    <th scope="col">시작</th>
                    <th scope="col">종료</th>
                  </tr>
                </thead>
                {/* 제재 한 건 = 기본 행 + (해제된 경우) 해제 정보 보조 행. */}
                {query.data.items.map((sanction) => (
                  <tbody key={sanction.id}>
                    <tr className={sanction.revokedAt ? styles.hasDetail : undefined}>
                      <td className={styles.cellId} data-label="번호">
                        #{sanction.id}
                      </td>
                      <td data-label="종류">{restrictionLabels[sanction.kind]}</td>
                      <td data-label="상태">
                        <span
                          className={
                            sanction.state === 'ACTIVE'
                              ? styles.toneWarn
                              : styles.badge
                          }
                        >
                          {sanctionStateLabels[sanction.state]}
                        </span>
                      </td>
                      <td className={styles.cellText} data-label="사유">
                        {sanction.reason}
                      </td>
                      <td className={styles.cellId} data-label="적용 관리자">
                        #{sanction.appliedBy}
                      </td>
                      <td className={styles.cellDate} data-label="시작">
                        {formatDateTime(sanction.startsAt)}
                      </td>
                      <td className={styles.cellDate} data-label="종료">
                        {formatDateTime(sanction.endsAt)}
                      </td>
                    </tr>
                    {sanction.revokedAt && (
                      <tr className={styles.detailRow}>
                        <td colSpan={7} data-label="">
                          <p className={styles.text}>
                            해제 관리자 #{sanction.revokedBy} ·{' '}
                            {formatDateTime(sanction.revokedAt)} ·{' '}
                            {sanction.revokeReason}
                          </p>
                        </td>
                      </tr>
                    )}
                  </tbody>
                ))}
              </table>
            </div>
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
