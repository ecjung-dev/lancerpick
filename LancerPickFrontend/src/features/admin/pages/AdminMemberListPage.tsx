import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { normalizeText } from '../../../shared/lib/text'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { AdminPager } from '../components/AdminPager'
import { accessProblem } from '../access'
import { useAdminMembers } from '../memberQueries'
import { readMemberFilter, restrictionLabels } from '../memberValidation'
import type { AdminMemberFilter } from '../memberTypes'
import { keywordError } from '../validation'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { canResetFilter } from '../filterReset'
import styles from './Admin.module.css'

export function AdminMemberListPage() {
  const [params, setParams] = useSearchParams()
  const [page, setPage] = usePageSearchParam()
  const filter = readMemberFilter(params)
  const query = useAdminMembers(filter, page)
  const problem = accessProblem(query.error)
  function apply(next: AdminMemberFilter) {
    const search = new URLSearchParams()
    if (next.q) search.set('q', next.q)
    if (next.restriction) search.set('restriction', next.restriction)
    setParams(search)
  }
  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>ADMIN</p>
      <AdminNav />
      <h1>회원 관리</h1>
      <p className={styles.lead}>
        회원의 현재 제한 상태와 제재 이력을 확인하고 관리해요. 보안 잠금과
        일반 이용 제한이 겹치면 보안 잠금이 우선 표시돼요.
      </p>
      <MemberFilter key={params.toString()} filter={filter} onApply={apply} params={params} />
      {problem ? (
        <AdminAccessNotice problem={problem} />
      ) : query.isError ? (
        <AsyncState
          title="회원 목록을 불러오지 못했어요"
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      ) : !query.data ? (
        <AsyncState title="회원 목록을 불러오고 있어요" />
      ) : (
        <>
          <p className={styles.count}>
            총 {query.data.totalElements}명 · 최신 가입순 · 30초마다 상태 갱신
          </p>
          {!query.data.items.length ? (
            <p className={styles.empty}>조건에 맞는 회원이 없어요.</p>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">번호</th>
                    <th scope="col">이름</th>
                    <th scope="col">이메일</th>
                    <th scope="col">구분</th>
                    <th scope="col">제한 상태</th>
                    <th scope="col">가입일</th>
                  </tr>
                </thead>
                <tbody>
                  {query.data.items.map((member) => (
                    <tr key={member.id}>
                      <td className={styles.cellId} data-label="번호">
                        #{member.id}
                      </td>
                      <td data-label="">
                        <Link
                          className={styles.cellLink}
                          to={`/admin/members/${member.id}`}
                          state={{
                            from: `/admin/members${params.size ? `?${params}` : ''}`,
                          }}
                        >
                          {member.displayName}
                        </Link>
                      </td>
                      <td data-label="이메일">{member.email}</td>
                      <td data-label="구분">
                        {member.role === 'ADMIN' ? (
                          <span className={styles.hidden}>관리자</span>
                        ) : (
                          '일반 회원'
                        )}
                      </td>
                      {/* 제한이 있을 때만 배지. "제한 없음"은 보통 글자로 둬 확인할 회원이 눈에 띄게 한다. */}
                      <td data-label="제한 상태">
                        {member.restriction === 'NONE' ? (
                          <span className={styles.cellMuted}>
                            {restrictionLabels.NONE}
                          </span>
                        ) : (
                          <span
                            className={
                              member.restriction === 'SECURITY_LOCK'
                                ? styles.toneDanger
                                : styles.toneWarn
                            }
                          >
                            {restrictionLabels[member.restriction]}
                          </span>
                        )}
                      </td>
                      <td className={styles.cellDate} data-label="가입일">
                        {formatDateTime(member.createdAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <AdminPager
            page={page}
            totalPages={query.data.totalPages}
            busy={query.isFetching}
            onPage={setPage}
            label="회원 목록 페이지"
          />
        </>
      )}
    </section>
  )
}

function MemberFilter({
  filter,
  onApply,
  params,
}: {
  filter: AdminMemberFilter
  onApply: (next: AdminMemberFilter) => void
  params: URLSearchParams
}) {
  const [draft, setDraft] = useState(filter)
  const [error, setError] = useState<string>()
  function submit(event: FormEvent) {
    event.preventDefault()
    const q = normalizeText(draft.q),
      invalid = keywordError(q)
    setError(invalid)
    if (!invalid) onApply({ ...draft, q })
  }
  return (
    <>
      <form className={styles.filters} role="search" onSubmit={submit}>
        <label>
          회원 검색
          <input
            value={draft.q}
            placeholder="회원번호·이메일·표시 이름"
            onChange={(event) => setDraft({ ...draft, q: event.target.value })}
            aria-describedby="member-search-error"
          />
        </label>
        <label>
          제한 상태
          <select
            value={draft.restriction}
            onChange={(event) =>
              setDraft({
                ...draft,
                restriction: event.target
                  .value as AdminMemberFilter['restriction'],
              })
            }
          >
            <option value="">전체</option>
            {Object.entries(restrictionLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button className={styles.button} type="submit">조회</button>
        <button
          type="button"
          className={`${styles.button} ${styles.secondary}`}
          disabled={
            !canResetFilter(params, ['q', 'restriction'], [draft.q, draft.restriction])
          }
          onClick={() => {
            const empty = { q: '', restriction: '' } as const
            setDraft(empty)
            setError(undefined)
            onApply(empty)
          }}
        >
          초기화
        </button>
      </form>
      <p
        id="member-search-error"
        className={styles.fieldError}
        role={error ? 'alert' : undefined}
      >
        {error}
      </p>
    </>
  )
}
