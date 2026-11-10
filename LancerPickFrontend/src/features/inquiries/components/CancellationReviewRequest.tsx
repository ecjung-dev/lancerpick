import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { formatDateTime } from '../../../shared/lib/format'
import { normalizedLength } from '../../../shared/lib/text'
import { captureAccountScope, isUnclearResult } from '../../auth/session'
import type { OrderDetail } from '../../orders/types'
import { requestCancellationReview } from '../api'
import { inquiryKeys, useOrderInquiries } from '../queries'
import { SUBJECT_MAX, canAskForReview, inquiryStatusLabel, validateSubject } from '../validation'

interface Props {
  order: Pick<OrderDetail, 'id' | 'policyVersion' | 'status' | 'allowedActions' | 'cancellationRequests'>
  disabled: boolean
}

/**
 * 판매자가 대기 중인 취소 요청에 응답하지 않을 때 구매자가 관리자 검토를 요청한다(K4).
 * - 같은 요청에 열린 문의가 이미 있으면 서버가 그 문의를 돌려주므로, 두 번 눌러도 문의는 하나다.
 * - 처리 중에는 막고 자동으로 다시 보내지 않는다. 통신 오류·5xx는 서버가 처리했을 수 있어 실패로 단정하지 않고 다시 조회한다.
 * - 409(판매자가 먼저 처리 등)는 주문 상세를 다시 불러와 최신 상태를 보여 준다.
 */
export function CancellationReviewRequest({ order, disabled }: Props) {
  const client = useQueryClient()
  const isV3 = order.policyVersion === 'CONTRACT_V3'
  const inquiries = useOrderInquiries(order.id, isV3)
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [subject, setSubject] = useState('')
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const pending = order.cancellationRequests.filter((item) => item.status === 'PENDING')
  const request = pending.length === 1 ? pending[0] : undefined

  const send = useMutation({
    retry: false,
    mutationFn: (input: { requestId: string; subject: string; scope: () => boolean }) =>
      requestCancellationReview(order.id, input.requestId, input.subject, input.scope),
    onSettled: () => { sending.current = false },
    onSuccess: async (_inquiry, input) => {
      // 기다리는 동안 계정이 바뀌었으면 이전 계정의 결과를 화면·캐시에 반영하지 않는다.
      if (!input.scope()) return
      if (active.current) { setSubject(''); setNotice('관리자 검토를 요청했어요. 처리 결과는 주문 상세와 알림으로 알려 드려요.') }
      await client.invalidateQueries({ queryKey: inquiryKeys.forOrder(order.id) })
    },
    onError: async (failure, input) => {
      if (!input.scope()) return
      if (isUnclearResult(failure)) {
        await client.invalidateQueries({ queryKey: ['wallet'] })
        if (active.current) setNotice('요청 결과를 확인하지 못해 최신 상태를 다시 불러왔어요. 요청이 보이지 않으면 다시 시도해 주세요.')
        return
      }
      if (failure instanceof ApiError && failure.status === 409) {
        await client.invalidateQueries({ queryKey: ['wallet'] })
        if (active.current) setNotice(failure.message)
        return
      }
      if (active.current) setError(failure instanceof ApiError ? failure.message : '요청하지 못했어요. 다시 시도해 주세요.')
    },
  })

  if (!isV3) return null
  const items = inquiries.data?.items ?? []
  const openForRequest = request && items.some((item) => item.cancellationRequestId === request.id && item.status !== 'CLOSED')
  const showForm = canAskForReview(order) && request && !openForRequest && inquiries.isSuccess
  if (items.length === 0 && !showForm && !notice && !inquiries.isError) return null

  function submit(event: FormEvent) {
    event.preventDefault()
    if (disabled || !request || send.isPending || sending.current) return
    const result = validateSubject(subject)
    setError(result.error)
    setNotice(undefined)
    if (result.error) return
    sending.current = true
    send.mutate({ requestId: request.id, subject: result.subject, scope: captureAccountScope(client) })
  }

  return (
    <section>
      <h2>관리자 검토 요청</h2>
      {inquiries.isError && (
        <p role="alert">
          검토 요청 내역을 불러오지 못했어요.{' '}
          <button type="button" onClick={() => { void inquiries.refetch() }}>다시 불러오기</button>
        </p>
      )}
      {items.map((item) => (
        <article key={item.id}>
          <p>{inquiryStatusLabel(item.status)} · 접수 {formatDateTime(item.createdAt)}</p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{item.subject}</p>
          {item.resolvedAt && <p>처리 시각 {formatDateTime(item.resolvedAt)}</p>}
        </article>
      ))}
      {notice && <p role="status">{notice}</p>}
      {showForm && (
        <form onSubmit={submit} noValidate>
          <fieldset disabled={disabled || send.isPending}>
            <legend>판매자가 취소 요청에 응답하지 않나요?</legend>
            <p>관리자가 취소 요청을 검토해 승인(전액 적립금 환불) 또는 거절해요. 검토 중에도 판매자는 요청을 처리할 수 있어요.</p>
            <label>
              검토 요청 내용
              <input
                value={subject}
                aria-invalid={!!error}
                onChange={(e) => { setSubject(e.target.value); setError(undefined) }}
                placeholder="예: 판매자가 사흘째 응답이 없습니다"
              />
            </label>
            <p>{normalizedLength(subject)} / {SUBJECT_MAX}자</p>
            {error && <p role="alert">{error}</p>}
            <button type="submit">{send.isPending ? '요청 중…' : '관리자 검토 요청'}</button>
          </fieldset>
        </form>
      )}
    </section>
  )
}
