import { useLocation, useParams } from 'react-router'
import { BackLink } from '../../../shared/components/BackLink'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { accessProblem } from '../access'
import { AdminMemberCommands } from '../components/AdminMemberCommands'
import { AdminMemberHistory } from '../components/AdminMemberHistory'
import { StatusBadge } from '../components/StatusBadge'
import { restrictionTones, toneOf } from '../statusTone'
import { useAdminMember } from '../memberQueries'
import { restrictionLabels } from '../memberValidation'
import { isValidProductId as validId } from '../validation'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { adminReturnTo, type AdminReturnOrigin } from '../navigation'
import styles from './Admin.module.css'

/** 회원 상세로 들어오는 화면: 회원 목록, 결제 목록, 주문 금융 상세, 문의 상세, 상품 상세, 자동 확정 타이머. */
const MEMBER_ORIGINS: readonly AdminReturnOrigin[] = [
  { path: '/admin/members', label: '회원 목록' },
  { path: '/admin/finance', label: '금융 조회' },
  { path: /\/admin\/finance\/orders\/[1-9]\d*/, label: '주문 금융' },
  { path: /\/admin\/inquiries\/[1-9]\d*/, label: '운영 문의' },
  { path: /\/admin\/products\/[1-9]\d*/, label: '상품 운영' },
  { path: '/admin/review-timers', label: '자동 확정 타이머' },
]

export function AdminMemberDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const back = adminReturnTo(location.state?.from, MEMBER_ORIGINS, {
    to: '/admin/members',
    label: '회원 목록',
  })
  const query = useAdminMember(id)
  const problem = accessProblem(query.error)
  const missing =
    !validId(id) ||
    (query.error instanceof ApiError && query.error.status === 404)
  return (
    <section className={styles.page}>
      <p className={styles.eyebrow}>ADMIN</p>
      <AdminNav />
      <BackLink to={back.to}>{back.label}</BackLink>
      {problem ? (
        <AdminAccessNotice problem={problem} />
      ) : missing ? (
        <AsyncState title="회원을 찾을 수 없어요" />
      ) : query.isError && !query.data ? (
        <AsyncState
          title="회원 정보를 불러오지 못했어요"
          error={query.error}
          onRetry={() => {
            void query.refetch()
          }}
        />
      ) : !query.data ? (
        <AsyncState title="회원 정보를 불러오는 중이에요" />
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
              최신 상태 조회에 실패했어요. 다시 조회한 뒤 처리해 주세요.
            </p>
          )}
          <section className={`${styles.block} ${styles.statusCard}`}>
            <div className={styles.statusHead}>
              <h2>
                현재 상태{' '}
                <StatusBadge tone={toneOf(restrictionTones, query.data.member.restriction)}>
                  {restrictionLabels[query.data.member.restriction]}
                </StatusBadge>
              </h2>
              <button
                type="button"
                className={`${styles.button} ${styles.secondary}`}
                disabled={query.isFetching}
                onClick={() => {
                  void query.refetch()
                }}
              >
                현재 상태 새로고침
              </button>
            </div>
            <dl className={styles.facts}>
              <dt>가입</dt>
              <dd>{formatDateTime(query.data.member.createdAt)}</dd>
              <dt>이메일 인증</dt>
              <dd>{query.data.emailVerifiedAt ? '인증됨' : '인증 기록 없음'}</dd>
              <dt>조회 시각</dt>
              <dd>{formatDateTime(query.data.serverTime)}</dd>
            </dl>
            <p className={styles.help}>
              30초마다 갱신해요. 실제 접근은 서버가 현재 시각으로 판단해요.
            </p>
            {!query.data.activeSanctions.length ? (
              <p>현재 적용 중인 제재가 없어요.</p>
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
                          타이머 중단 전달이 완료되지 않았어요. 진행 중 거래와
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
