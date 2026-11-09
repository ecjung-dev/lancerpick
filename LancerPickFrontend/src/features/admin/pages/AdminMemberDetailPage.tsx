import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { accessProblem } from '../access'
import { AdminMemberCommands } from '../components/AdminMemberCommands'
import { AdminMemberHistory } from '../components/AdminMemberHistory'
import { useAdminMember } from '../memberQueries'
import { restrictionLabels } from '../memberValidation'
import { isValidProductId as validId } from '../validation'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import styles from './Admin.module.css'

export function AdminMemberDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const from: unknown = location.state?.from
  const back =
    typeof from === 'string' &&
    (from === '/admin/members' || from.startsWith('/admin/members?'))
      ? from
      : '/admin/members'
  const query = useAdminMember(id)
  const problem = accessProblem(query.error)
  const missing =
    !validId(id) ||
    (query.error instanceof ApiError && query.error.status === 404)
  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>ADMIN</p>
      <AdminNav />
      <Link to={back}>← 회원 목록</Link>
      {problem ? (
        <AdminAccessNotice problem={problem} />
      ) : missing ? (
        <AsyncState title="회원을 찾을 수 없습니다" />
      ) : query.isError && !query.data ? (
        <AsyncState
          title="회원 정보를 불러오지 못했어요"
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      ) : !query.data ? (
        <AsyncState title="회원 정보를 불러오는 중입니다" />
      ) : (
        <>
          <header className={styles.heading}>
            <h1>
              {query.data.member.displayName} · 회원 #{query.data.member.id}
            </h1>
            <p>
              {query.data.member.email} ·{' '}
              {query.data.member.role === 'ADMIN' ? '관리자' : '일반 회원'}
            </p>
          </header>
          {query.isError && (
            <p className={styles.notice} role="alert">
              최신 상태 조회에 실패했습니다. 재조회 후 처리해 주세요.
            </p>
          )}
          <button
            type="button"
            disabled={query.isFetching}
            onClick={() => {
              void query.refetch()
            }}
          >
            현재 상태 새로고침
          </button>
          <section className={styles.block}>
            <h2>
              현재 상태 · {restrictionLabels[query.data.member.restriction]}
            </h2>
            <p>
              가입 {formatDateTime(query.data.member.createdAt)} · 이메일{' '}
              {query.data.emailVerifiedAt ? '인증됨' : '인증 기록 없음'}
            </p>
            <p className={styles.help}>
              조회 시각 {formatDateTime(query.data.serverTime)} · 30초마다
              갱신합니다. 실제 접근은 서버가 현재 시각으로 판단합니다.
            </p>
            {!query.data.activeSanctions.length ? (
              <p>현재 적용 중인 제재가 없습니다.</p>
            ) : (
              <ul className={styles.list}>
                {query.data.activeSanctions.map((sanction) => (
                  <li key={sanction.id}>
                    <strong>
                      {restrictionLabels[sanction.kind]} · #{sanction.id}
                    </strong>
                    <p>
                      종료 {formatDateTime(sanction.endsAt)} · 적용 관리자 #
                      {sanction.appliedBy}
                    </p>
                    <p className={styles.text}>{sanction.reason}</p>
                    {sanction.kind === 'SECURITY_LOCK' &&
                      !sanction.timerStopDeliveredAt && (
                        <p className={styles.notice}>
                          타이머 중단 전달이 완료되지 않았습니다. 진행 중 거래와
                          타이머를 확인해 주세요.
                        </p>
                      )}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <AdminMemberCommands
            key={id}
            detail={query.data}
            stale={query.isError || query.isFetching}
          />
          <AdminMemberHistory key={`history-${id}`} memberId={id} />
        </>
      )}
    </section>
  )
}
