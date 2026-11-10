import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime } from '../../../shared/lib/format'
import { normalizedLength } from '../../../shared/lib/text'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { captureAccountScope, isUnclearResult } from '../../auth/session'
import { stopReviewTimer, useActiveReviewTimers, type ActiveReviewTimer } from '../timerApi'
import { REASON_MAX, validateReason } from '../validation'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import styles from './Admin.module.css'

const name = (party: { id: string; displayName: string | null }) => party.displayName ?? `회원 #${party.id}`

/** /admin/review-timers. 진행 중인 검수 무응답 자동 구매 확정 타이머를 만료가 이른 순으로 보고, 사유를 남겨 해제한다(K5). */
export function AdminReviewTimerPage() {
  const [page, setPage] = usePageSearchParam()
  const list = useActiveReviewTimers(page)
  const [selected, setSelected] = useState<string>()
  const problem = accessProblem(list.error)

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <h1>자동 구매 확정 타이머</h1>
    <p className={styles.lead}>판매자가 시작한 7일 타이머가 끝나면 구매가 자동 확정되고 판매자에게 대금이 지급돼요. 운영 확인이 필요하면 만료 전에 해제할 수 있어요. 해제한 타이머는 같은 납품으로 다시 시작할 수 없어요.</p>
    {problem ? <AdminAccessNotice problem={problem} />
      : list.isError ? <AsyncState title="타이머 목록을 불러오지 못했어요" error={list.error} onRetry={() => { void list.refetch() }} />
        : !list.data ? <AsyncState title="타이머 목록을 불러오고 있어요" />
          : <>
            <p className={styles.count}>진행 중 {list.data.totalElements}건</p>
            {list.data.items.length === 0 ? <p>진행 중인 타이머가 없어요.</p> : <ul className={styles.list}>
              {list.data.items.map((timer) => <li key={timer.timerId}>
                <strong>주문 #{timer.orderId}</strong>
                <p>구매자 {name(timer.buyer)} · 판매자 {name(timer.seller)} · {timer.cycleNo}회차</p>
                <small>시작 {formatDateTime(timer.startedAt)} · 자동 확정 예정 {formatDateTime(timer.expiresAt)}</small>
                {selected === timer.orderId
                  ? <StopForm key={timer.timerId} timer={timer} onDone={() => setSelected(undefined)} />
                  : <div className={styles.actions}><button type="button" onClick={() => setSelected(timer.orderId)}>해제하기</button></div>}
              </li>)}
            </ul>}
            {list.data.items.length === 0 && page > 0 && <button disabled={list.isFetching} onClick={() => setPage(0)}>첫 페이지로</button>}
            {list.data.totalPages > 1 && <nav className={styles.pager} aria-label="타이머 목록 페이지">
              <button disabled={list.isFetching || page === 0} onClick={() => setPage(page - 1)}>이전</button>
              <span>{page + 1}페이지</span>
              <button disabled={list.isFetching || page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>다음</button>
            </nav>}
          </>}
  </section>
}

/**
 * 해제. 실패·동시 처리·늦은 응답:
 * - 처리 중에는 막고 자동으로 다시 보내지 않는다(retry: false). 빠른 두 번 클릭은 ref로 렌더링 전에도 막는다.
 * - 409(이미 해제·자동 확정 시각 지남 AUTO_CONFIRM_DUE): 서버 안내를 보여 주고 목록을 다시 불러온다.
 * - 통신 오류·5xx: 서버가 처리했을 수 있어 실패로 단정하지 않고 목록을 다시 조회한다.
 */
function StopForm({ timer, onDone }: { timer: ActiveReviewTimer; onDone: () => void }) {
  const client = useQueryClient()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()

  const stop = useMutation({
    retry: false,
    mutationFn: (input: { reason: string; scope: () => boolean }) => stopReviewTimer(timer.orderId, input.reason, input.scope),
    onSettled: () => { sending.current = false },
    onSuccess: async (_status, input) => {
      // 기다리는 동안 계정이 바뀌었으면 이전 계정의 결과를 화면·캐시에 반영하지 않는다.
      if (!input.scope()) return
      await client.invalidateQueries({ queryKey: ['admin', 'review-timers'] })
      if (active.current) onDone()
    },
    onError: async (failure, input) => {
      if (!input.scope()) return
      if (isUnclearResult(failure) || (failure instanceof ApiError && failure.status === 409)) {
        await client.invalidateQueries({ queryKey: ['admin', 'review-timers'] })
        if (active.current) setNotice(isUnclearResult(failure)
          ? '처리 결과를 확인하지 못해 목록을 다시 불러왔어요. 목록에 남아 있으면 다시 시도해 주세요.'
          : (failure as ApiError).message)
        return
      }
      if (active.current) setError(failure instanceof ApiError ? failure.message : '해제하지 못했어요. 다시 시도해 주세요.')
    },
  })
  const problem = accessProblem(stop.error)
  if (problem) return <AdminAccessNotice problem={problem} />

  function submit(event: FormEvent) {
    event.preventDefault()
    if (stop.isPending || sending.current) return
    const result = validateReason(reason)
    setError(result.error)
    setNotice(undefined)
    if (result.error) return
    sending.current = true
    stop.mutate({ reason: result.reason, scope: captureAccountScope(client) })
  }

  const fieldId = `timer-reason-${timer.timerId}`
  return <form className={styles.form} onSubmit={submit} noValidate>
    <h2>주문 #{timer.orderId} 타이머 해제</h2>
    <p>해제 사유는 주문 이력에 남고 구매자·판매자에게 알림이 가요. 이미 완료된 구매 확정·지급은 되돌리지 않아요.</p>
    {notice && <p className={styles.notice} role="status">{notice}</p>}
    <fieldset disabled={stop.isPending}>
      <label htmlFor={fieldId}>해제 사유 <span>필수</span></label>
      <textarea id={fieldId} rows={3} value={reason} aria-invalid={!!error} aria-describedby={`${fieldId}-help ${fieldId}-error`}
        onChange={(e) => { setReason(e.target.value); setError(undefined) }} />
      <p id={`${fieldId}-help`} className={styles.help}>{normalizedLength(reason)} / {REASON_MAX.toLocaleString()}자</p>
      <p id={`${fieldId}-error`} className={styles.fieldError}>{error}</p>
      <div className={styles.actions}>
        <button type="submit">{stop.isPending ? '처리 중…' : '해제'}</button>
        <button type="button" onClick={onDone}>취소</button>
      </div>
    </fieldset>
  </form>
}
