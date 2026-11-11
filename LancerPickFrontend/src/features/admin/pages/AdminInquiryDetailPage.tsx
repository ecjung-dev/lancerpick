import { adminBackPath } from '../navigation'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { normalizedLength } from '../../../shared/lib/text'
import { captureAccountScope, isUnclearResult } from '../../auth/session'
import { inquiryStatusLabel } from '../../inquiries/validation'
import { orderStatusLabel } from '../../orders/status'
import { closeInquiry, decideInquiry } from '../inquiryApi'
import { adminInquiryKeys, inquiryTypeLabel, useAdminInquiry } from '../inquiryQueries'
import type { AdminInquiryDetail, Party } from '../inquiryTypes'
import { REASON_MAX, isValidProductId as isValidId, validateReason } from '../validation'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { MemberLink } from '../components/MemberLink'
import styles from './Admin.module.css'
import { StatusBadge } from '../components/StatusBadge'
import { cancellationTones, inquiryTones, toneOf } from '../statusTone'

const FACTS = `${styles.facts} ${styles.factsAligned}`

/** 목록에서 넘겨준 돌아갈 주소. 관리자 문의 목록 경로만 허용한다. */
const name = (party: Party) => party.displayName ?? `회원 #${party.id}`

const cancellationLabel: Record<string, string> = {
  PENDING: '대기 중', APPROVED: '승인됨', REJECTED: '거절됨', WITHDRAWN: '구매자 철회',
}

export function AdminInquiryDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const back = adminBackPath(location.state?.from, '/admin/inquiries', '/admin/inquiries?status=OPEN')
  const inquiry = useAdminInquiry(id)
  const problem = accessProblem(inquiry.error)
  const missing = !isValidId(id) || (inquiry.error instanceof ApiError && inquiry.error.status === 404)

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <Link to={back}>← 운영 문의 목록</Link>
    {problem ? <AdminAccessNotice problem={problem} />
      : missing ? <AsyncState title="문의를 찾을 수 없어요" description="문의 번호를 다시 확인해 주세요." />
        : !inquiry.data && inquiry.isError ? <AsyncState title="문의를 불러오지 못했어요" error={inquiry.error} onRetry={() => { void inquiry.refetch() }} />
          : !inquiry.data ? <AsyncState title="문의를 불러오고 있어요" />
            : <>
              {inquiry.isError && <div className={styles.notice} role="alert">
                <p>최신 상태를 불러오지 못했어요. 아래 내용은 마지막으로 확인한 상태예요.</p>
                <button type="button" className={`${styles.button} ${styles.secondary}`} disabled={inquiry.isFetching} onClick={() => { void inquiry.refetch() }}>다시 불러오기</button>
              </div>}
              <InquirySummary inquiry={inquiry.data} refreshing={inquiry.isFetching} />
              {inquiry.data.allowedActions.includes('APPROVE') && <DecisionForm key={`${inquiry.data.id}:${inquiry.data.status}`} inquiry={inquiry.data} />}
              {inquiry.data.allowedActions.includes('CLOSE') && <CloseForm key={`${inquiry.data.id}:${inquiry.data.status}`} inquiry={inquiry.data} />}
            </>}
  </section>
}

function InquirySummary({ inquiry, refreshing }: { inquiry: AdminInquiryDetail; refreshing: boolean }) {
  const { order, cancellationRequest: request } = inquiry
  const here = `/admin/inquiries/${inquiry.id}`
  return <>
    <header className={styles.heading}>
      <p>{inquiryTypeLabel(inquiry.type)} · 신청 {name(inquiry.requester)}</p>
      <h1>{inquiry.subject}</h1>
      <p className={styles.statusLine}>
        <StatusBadge tone={toneOf(inquiryTones, inquiry.status)}>{inquiryStatusLabel(inquiry.status)}</StatusBadge>
        <span>접수 {formatDateTime(inquiry.createdAt)}
          {inquiry.resolvedAt && <> · 처리 {formatDateTime(inquiry.resolvedAt)}{inquiry.assignedAdminId && <> (<MemberLink id={inquiry.assignedAdminId} role="관리자" from={here} />)</>}</>}</span>
      </p>
      {refreshing && <p role="status">최신 상태를 확인하고 있어요.</p>}
    </header>
    {/* 주문·취소 요청을 카드 두 개로. 각각 있을 때만 보여 준다. */}
    {(order || request) && <div className={styles.financeGrid}>
    {order && <section className={styles.card}>
      <h2>주문</h2>
      <dl className={FACTS}>
        <dt>주문 번호</dt><dd><Link className={styles.inlineLink} to={`/admin/finance/orders/${order.id}`} state={{ from: here }}>#{order.id}</Link></dd>
        <dt>상태</dt><dd>{orderStatusLabel(order.status)}</dd>
        <dt>결제 금액</dt><dd><span className={styles.amount}>{formatWon(order.priceAmount)}</span>{order.paidAt && <> · 결제 {formatDateTime(order.paidAt)}</>}</dd>
        <dt>구매자</dt><dd><MemberLink id={order.buyer.id} from={here}>{name(order.buyer)}</MemberLink></dd>
        <dt>판매자</dt><dd><MemberLink id={order.seller.id} from={here}>{name(order.seller)}</MemberLink></dd>
      </dl>
    </section>}
    {request && <section className={styles.card}>
      <h2>구매자 취소 요청</h2>
      <dl className={FACTS}>
        <dt>상태</dt><dd><StatusBadge tone={toneOf(cancellationTones, request.status)}>{cancellationLabel[request.status] ?? request.status}</StatusBadge></dd>
        <dt>요청</dt><dd>{formatDateTime(request.requestedAt)}</dd>
        {request.resolvedAt && <><dt>처리</dt><dd>{formatDateTime(request.resolvedAt)}{request.resolutionSource === 'ADMIN' ? ' (관리자)' : ''}</dd></>}
        <dt>취소 사유</dt><dd className={styles.text}>{request.reason}</dd>
        {request.responseReason && <><dt>처리 사유</dt><dd className={styles.text}>{request.responseReason}</dd></>}
      </dl>
    </section>}
    </div>}
  </>
}

/**
 * 승인(보관금 전액 반환·주문 취소)·거절(대금 계속 보관). 실패·동시 처리·늦은 응답:
 * - 자동으로 다시 보내지 않는다(retry: false). 같은 결정을 다시 보내도 서버가 같은 결과를 돌려준다.
 * - 409(판매자가 먼저 처리·다른 관리자가 다른 결정 등): 서버 안내를 보여 주고 최신 문의를 다시 불러온다.
 * - 통신 오류·5xx: 서버가 처리했을 수 있어 실패로 단정하지 않고 다시 조회한다.
 * - 계정 경계: 클릭 때 계정을 잡아 두고, 전송 직전·응답 후에 같은 계정일 때만 보내고 반영한다(#131 공통 방식).
 */
function DecisionForm({ inquiry }: { inquiry: AdminInquiryDetail }) {
  const client = useQueryClient()
  // 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않고, 빠른 두 번 클릭은 렌더링 전에도 막는다.
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [reason, setReason] = useState('')
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  async function refresh() {
    await Promise.all([
      client.invalidateQueries({ queryKey: adminInquiryKeys.detail(inquiry.id) }),
      client.invalidateQueries({ queryKey: ['admin', 'inquiries'] }),
    ])
  }

  const decide = useMutation({
    retry: false,
    mutationFn: (input: { decision: 'approve' | 'reject'; reason: string; scope: () => boolean }) =>
      decideInquiry(inquiry.id, input.decision, input.reason, input.scope),
    onSettled: () => { sending.current = false },
    onSuccess: async (result, input) => {
      // 기다리는 동안 로그아웃·다른 계정 로그인이 끝났으면 이전 계정의 결과를 화면·캐시에 넣지 않는다.
      if (!input.scope()) return
      if (active.current) setNotice(result.decision === 'APPROVED' ? '승인했어요. 결제 금액이 구매자에게 전액 반환됐어요.' : '거절했어요. 대금은 계속 보관돼요.')
      await refresh()
    },
    onError: async (failure, input) => {
      // 계정이 바뀌어 보내지 않았거나(AbortError) 늦게 도착한 실패는 새 계정 화면에 안내하지 않는다.
      if (!input.scope()) return
      if (isUnclearResult(failure)) {
        await refresh()
        if (active.current) setNotice('처리 결과를 확인하지 못해 최신 상태를 다시 불러왔어요. 상태를 확인한 뒤 필요하면 다시 시도해 주세요.')
        return
      }
      if (failure instanceof ApiError && failure.status === 409) {
        await refresh()
        if (active.current) setNotice(failure.message)
        return
      }
      if (active.current) setError(failure instanceof ApiError ? failure.message : '처리하지 못했어요. 다시 시도해 주세요.')
    },
  })

  const problem = accessProblem(decide.error)
  if (problem) return <AdminAccessNotice problem={problem} />

  function submit(decision: 'approve' | 'reject') {
    if (decide.isPending || sending.current) return
    if (decision === 'approve' && !agree) return
    const result = validateReason(reason)
    setError(result.error)
    setNotice(undefined)
    if (result.error) return
    sending.current = true
    decide.mutate({ decision, reason: result.reason, scope: captureAccountScope(client) })
  }

  return <form className={styles.form} onSubmit={(e: FormEvent) => e.preventDefault()} noValidate>
    <h2>취소 요청 검토</h2>
    <p>처리 사유는 양측 주문 상세의 "처리 사유"와 주문 이력에 남아요. 승인하면 주문이 취소되고 결제 금액이 구매자 적립금으로 전액 반환돼요. 거절하면 대금은 계속 보관되고 거절 시각부터 다시 검수 기간이 시작돼요.</p>
    {notice && <p className={styles.notice} role="status">{notice}</p>}
    <fieldset disabled={decide.isPending}>
      <label htmlFor="inquiry-reason">처리 사유 <span>필수</span></label>
      <textarea id="inquiry-reason" rows={4} value={reason} aria-invalid={!!error} aria-describedby="inquiry-reason-help inquiry-reason-error"
        onChange={(e) => { setReason(e.target.value); setError(undefined) }} />
      <p id="inquiry-reason-help" className={styles.help}>{normalizedLength(reason)} / {REASON_MAX.toLocaleString()}자</p>
      <p id="inquiry-reason-error" className={styles.fieldError}>{error}</p>
      <label><input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} /> 승인 시 주문 취소와 전액 반환이 되돌릴 수 없음을 확인했어요.</label>
      <div className={styles.actions}>
        <button className={styles.button} type="button" disabled={!agree} onClick={() => submit('approve')}>{decide.isPending ? '처리 중…' : '승인 및 전액 반환'}</button>
        <button className={styles.button} type="button" onClick={() => submit('reject')}>{decide.isPending ? '처리 중…' : '거절'}</button>
      </div>
    </fieldset>
  </form>
}

/** 판매자가 먼저 처리했거나 구매자가 철회해 승인·거절할 수 없게 된 문의를 닫는다. 주문·대금은 바뀌지 않는다. */
function CloseForm({ inquiry }: { inquiry: AdminInquiryDetail }) {
  const client = useQueryClient()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [notice, setNotice] = useState<string>()
  const close = useMutation({
    retry: false,
    mutationFn: (scope: () => boolean) => closeInquiry(inquiry.id, scope),
    onSettled: () => { sending.current = false },
    onSuccess: (updated, scope) => {
      if (!scope()) return
      client.setQueryData(adminInquiryKeys.detail(inquiry.id), updated)
      void client.invalidateQueries({ queryKey: ['admin', 'inquiries'] })
    },
    onError: async (failure, scope) => {
      if (!scope()) return
      await client.invalidateQueries({ queryKey: adminInquiryKeys.detail(inquiry.id) })
      if (active.current) setNotice(isUnclearResult(failure)
        ? '처리 결과를 확인하지 못해 최신 상태를 다시 불러왔어요.'
        : failure instanceof ApiError ? failure.message : '처리하지 못했어요. 다시 시도해 주세요.')
    },
  })
  const problem = accessProblem(close.error)
  if (problem) return <AdminAccessNotice problem={problem} />
  return <form className={styles.form} onSubmit={(e: FormEvent) => {
    e.preventDefault()
    if (close.isPending || sending.current) return
    sending.current = true
    setNotice(undefined)
    close.mutate(captureAccountScope(client))
  }}>
    <h2>문의 닫기</h2>
    <p>연결된 취소 요청이 이미 처리돼 승인·거절할 수 없어요. 문의만 처리 완료로 닫고 주문·대금은 바꾸지 않아요.</p>
    {notice && <p className={styles.notice} role="status">{notice}</p>}
    <button className={styles.button} type="submit" disabled={close.isPending}>{close.isPending ? '처리 중…' : '문의 닫기'}</button>
  </form>
}
