import { useState } from 'react'
import { formatWon } from '../../../shared/lib/format'
import { normalizeText, codePointLength } from '../../../shared/lib/text'
import {
  payOrder,
  cancelOrder,
  confirmOrder,
  acceptOrder,
  rejectOrder,
  deliverOrder,
  requestOrderRevision,
} from '../api'
import type { OrderDetail } from '../types'

// 잠금·실패·재조회 수명주기는 페이지의 run이 소유한다. 각 폼은 입력값과 제출 조건만 가진다.
// 입력 초기화는 페이지가 key를 바꿔 다시 마운트하는 방식으로 한다.
interface FormProps {
  // 명령 요청 경로는 기존과 같이 화면 주소의 주문 ID를 그대로 쓴다.
  orderId: string
  order: OrderDetail
  disabled: boolean
  run: (command: () => Promise<OrderDetail>) => Promise<boolean>
}

function textLength(value: string) {
  return codePointLength(normalizeText(value))
}

function deliverySequence(order: OrderDetail): 1 | 2 | null {
  return order.status === 'IN_PROGRESS'
    ? 1
    : order.status === 'REVISION_IN_PROGRESS'
      ? 2
      : null
}

export function DeliveryForm({ orderId, order, disabled, run }: FormProps) {
  const [description, setDescription] = useState('')
  const [resultUrl, setResultUrl] = useState('')
  const sequence = deliverySequence(order)
  const descriptionLength = textLength(description)
  const cleanUrl = normalizeText(resultUrl)
  const valid =
    descriptionLength > 0 &&
    descriptionLength <= 5000 &&
    cleanUrl.length > 0 &&
    codePointLength(cleanUrl) <= 2048
  if (!order.allowedActions.includes('DELIVER') || sequence === null) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('DELIVER') || !valid || sequence === null)
      return
    void run(() => deliverOrder(orderId, description, resultUrl, sequence))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>{sequence === 1 ? '최초 납품' : '재납품'}</legend>
      <label>
        납품 설명
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>
      <p>{descriptionLength} / 5,000자</p>
      <label>
        결과물 HTTPS 링크
        <input
          type="url"
          value={resultUrl}
          onChange={(e) => setResultUrl(e.target.value)}
        />
      </label>
      <p>
        구매자가 열 수 있는 링크를 입력해 주세요. 제출 후 해당 회차를 덮어쓸
        수 없습니다.
      </p>
      <button disabled={!valid} onClick={submit}>
        {sequence}차 납품 제출
      </button>
    </fieldset>
  )
}

export function RevisionForm({ orderId, order, disabled, run }: FormProps) {
  const [reason, setReason] = useState('')
  const length = textLength(reason)
  const valid = length > 0 && length <= 1000
  const firstDelivery = order.deliveries.find((delivery) => delivery.sequenceNo === 1)
  if (!order.allowedActions.includes('REQUEST_REVISION') || !firstDelivery) return null
  function submit() {
    if (
      disabled ||
      !order.allowedActions.includes('REQUEST_REVISION') ||
      !valid ||
      !firstDelivery
    )
      return
    void run(() => requestOrderRevision(orderId, firstDelivery.id, reason))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>최초 납품 수정 요청</legend>
      <p>보관된 거래 범위 안에서 한 번 요청할 수 있습니다.</p>
      <label>
        수정 사유
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <p>{length} / 1,000자</p>
      <button disabled={!valid} onClick={submit}>
        수정 요청
      </button>
    </fieldset>
  )
}

export function AcceptForm({ orderId, order, disabled, run }: FormProps) {
  if (!order.allowedActions.includes('ACCEPT')) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('ACCEPT')) return
    void run(() => acceptOrder(orderId))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>주문 수락</legend>
      <p>
        보관된 거래 조건과 구매자의 요구사항을 확인해 주세요. 수락하면{' '}
        {order.policyVersion === 'LEGACY_V1'
          ? '구매자가 결제할 수 있습니다.'
          : '계약 작성·양측 서명 후 전액 결제를 진행합니다.'}
      </p>
      <button onClick={submit}>주문 수락</button>
    </fieldset>
  )
}

export function RejectForm({ orderId, order, disabled, run }: FormProps) {
  const [reason, setReason] = useState('')
  const length = textLength(reason)
  const valid = length > 0 && length <= 1000
  if (!order.allowedActions.includes('REJECT')) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('REJECT') || !valid) return
    void run(() => rejectOrder(orderId, reason))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>주문 거절</legend>
      <label>
        거절 사유 (필수, 최대 1,000자)
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <p>{length} / 1,000자</p>
      <p>
        거절하면 이 주문은 종료됩니다. 작성한 사유는 구매자에게 전달됩니다.
      </p>
      <button disabled={!valid} onClick={submit}>
        주문 거절
      </button>
    </fieldset>
  )
}

export function PayForm({ orderId, order, disabled, run }: FormProps) {
  const [agreed, setAgreed] = useState(false)
  if (!order.allowedActions.includes('PAY')) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('PAY') || !agreed) return
    void run(() => payOrder(orderId))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>적립금 결제</legend>
      <label>
        <input
          type="checkbox"
          checked={agreed}
          onChange={(e) => setAgreed(e.target.checked)}
        />{' '}
        위 거래 조건과 {formatWon(order.priceAmount)} 결제에 동의합니다.
      </label>
      <button disabled={!agreed} onClick={submit}>
        적립금으로 결제
      </button>
    </fieldset>
  )
}

export function CancelForm({ orderId, order, disabled, run }: FormProps) {
  const [reason, setReason] = useState('')
  const length = textLength(reason)
  const valid = length > 0 && length <= 1000
  if (!order.allowedActions.includes('CANCEL')) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('CANCEL') || !valid) return
    void run(() => cancelOrder(orderId, reason))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>직접 취소</legend>
      <p>
        {order.payment?.status === 'PAID'
          ? '결제된 주문은 전액 적립금 환불과 함께 취소됩니다.'
          : '결제 전 주문은 적립금 차감·환불 없이 취소됩니다.'}
      </p>
      <label>
        취소 사유 (필수, 최대 1,000자)
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <p>{length} / 1,000자</p>
      <button disabled={!valid} onClick={submit}>
        주문 취소
      </button>
    </fieldset>
  )
}

export function ConfirmForm({ orderId, order, disabled, run }: FormProps) {
  const [confirmed, setConfirmed] = useState(false)
  if (!order.allowedActions.includes('CONFIRM')) return null
  function submit() {
    if (disabled || !order.allowedActions.includes('CONFIRM') || !confirmed) return
    void run(() => confirmOrder(orderId))
  }
  return (
    <fieldset disabled={disabled}>
      <legend>구매 확정</legend>
      <p>
        납품을 확인한 후 확정해 주세요. 확정 후에는 취소·환불할 수 없습니다.
      </p>
      <label>
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
        />{' '}
        결과물을 확인했고 구매 확정에 동의합니다.
      </label>
      <button disabled={!confirmed} onClick={submit}>
        구매 확정
      </button>
    </fieldset>
  )
}
