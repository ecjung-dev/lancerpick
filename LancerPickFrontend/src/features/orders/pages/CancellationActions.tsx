import { useRef, useState } from 'react'
import { createCancellationRequest, resolveCancellationRequest } from '../api'
import type { OrderDetail } from '../types'
import { normalizeText, codePointLength, normalizedLength } from '../../../shared/lib/text'
import { formatDateTime } from '../../../shared/lib/format'
import { ApiError } from '../../../shared/api/client'
import { cancellationStatusLabel } from '../status'

interface Props {
  order: OrderDetail
  disabled: boolean
  run: (command: () => Promise<OrderDetail>) => Promise<boolean>
}

export function CancellationActions({ order, disabled, run }: Props) {
  const [reason, setReason] = useState('')
  const [responseReason, setResponseReason] = useState('')
  const [approved, setApproved] = useState(false)
  const draft = useRef<{ reason: string; key: string } | null>(null)
  const [frozen, setFrozen] = useState(false)
  const length = normalizedLength(reason)
  const response = normalizeText(responseReason)
  const pending = order.cancellationRequests.filter(
    (item) => item.status === 'PENDING',
  )
  const request = pending.length === 1 ? pending[0] : undefined
  async function submit() {
    if (
      disabled ||
      !order.allowedActions.includes('REQUEST_CANCELLATION') ||
      length < 1 ||
      length > 1000
    )
      return
    const ok = await run(async () => {
      // 결과 불명확 시 같은 화면의 재시도는 동일 키와 사유를 유지한다.
      draft.current ??= {
        reason: normalizeText(reason),
        key: crypto.randomUUID(),
      }
      setFrozen(true)
      try {
        const result = await createCancellationRequest(
          order.id,
          draft.current.reason,
          draft.current.key,
        )
        return result.order
      } catch (error) {
        if (error instanceof ApiError && error.status === 400) {
          draft.current = null
          setFrozen(false)
        }
        throw error
      }
    })
    if (ok) {
      draft.current = null
      setFrozen(false)
      setReason('')
    }
  }
  async function resolve(action: 'approve' | 'reject' | 'withdraw') {
    const allowed = {
      approve: 'APPROVE_CANCELLATION',
      reject: 'REJECT_CANCELLATION',
      withdraw: 'WITHDRAW_CANCELLATION',
    }
    if (disabled || !request || !order.allowedActions.includes(allowed[action]))
      return
    if (action === 'approve' && !approved) return
    if (action === 'reject' && codePointLength(response) > 1000) return
    const ok = await run(
      async () =>
        (
          await resolveCancellationRequest(
            order.id,
            request.id,
            action,
            response || undefined,
          )
        ).order,
    )
    if (ok) {
      setApproved(false)
      setResponseReason('')
    }
  }
  return (
    <section>
      <h2>취소 요청 내역</h2>
      {order.cancellationRequests.length === 0 ? (
        <p>취소 요청이 없어요.</p>
      ) : (
        order.cancellationRequests.map((item) => (
          <article key={item.id}>
            <p>
              {cancellationStatusLabel(item.status)} ·{' '}
              {formatDateTime(item.requestedAt)}
            </p>
            <p style={{ whiteSpace: 'pre-wrap' }}>{item.reason}</p>
            {item.responseReason && (
              <p style={{ whiteSpace: 'pre-wrap' }}>
                처리 사유: {item.responseReason}
              </p>
            )}
            {item.resolvedAt && (
              <p>처리 시각 {formatDateTime(item.resolvedAt)}</p>
            )}
          </article>
        ))
      )}
      {order.allowedActions.includes('REQUEST_CANCELLATION') && (
        <fieldset disabled={disabled}>
          <legend>구매자 취소 요청</legend>
          <p>
            판매자가 승인하면 주문이 취소되고 전액 적립금으로 환불돼요. 대기
            중에는 납품·수정·구매 확정이 제한돼요.
          </p>
          <label>
            취소 요청 사유
            <textarea
              value={reason}
              readOnly={frozen}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <p>{length} / 1,000자</p>
          {frozen && (
            <p>이전 요청 결과를 다시 조회한 뒤 같은 사유로 재시도해 주세요.</p>
          )}
          <button
            disabled={length < 1 || length > 1000}
            onClick={() => {
              void submit()
            }}
          >
            취소 요청 제출
          </button>
        </fieldset>
      )}
      {request && (
        <fieldset key={request.id} disabled={disabled}>
          <legend>대기 중인 취소 요청 처리</legend>
          {order.allowedActions.includes('APPROVE_CANCELLATION') && (
            <>
              <label>
                <input
                  type="checkbox"
                  checked={approved}
                  onChange={(e) => setApproved(e.target.checked)}
                />{' '}
                주문 취소와 전액 적립금 환불에 동의합니다.
              </label>
              <button
                disabled={!approved}
                onClick={() => {
                  void resolve('approve')
                }}
              >
                승인 및 환불
              </button>
            </>
          )}
          {order.allowedActions.includes('REJECT_CANCELLATION') && (
            <>
              <label>
                거절 사유 (선택)
                <textarea
                  value={responseReason}
                  onChange={(e) => setResponseReason(e.target.value)}
                />
              </label>
              <p>
                {codePointLength(response)} / 1,000자 · 거절 시 환불되지
                않아요.
              </p>
              <button
                disabled={codePointLength(response) > 1000}
                onClick={() => {
                  void resolve('reject')
                }}
              >
                취소 요청 거절
              </button>
            </>
          )}
          {order.allowedActions.includes('WITHDRAW_CANCELLATION') && (
            <>
              <p>요청만 철회하며 주문과 결제는 유지돼요.</p>
              <button
                onClick={() => {
                  void resolve('withdraw')
                }}
              >
                취소 요청 철회
              </button>
            </>
          )}
        </fieldset>
      )}
    </section>
  )
}
