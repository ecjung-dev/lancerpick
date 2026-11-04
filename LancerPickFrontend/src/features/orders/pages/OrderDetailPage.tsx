import { useOrder } from '../queries'
import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
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
import styles from './Orders.module.css'
import { CancellationActions } from './CancellationActions'
import type { OrderDetail as OrderDetailData } from '../types'
import { OrderHistoryList } from './OrderHistoryList'
import { orderDeadlineView } from '../deadlines'
import { orderStatusLabel } from '../status'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  return <OrderDetail key={id} id={id} />
}
function OrderDetail({ id }: { id: string }) {
  const location = useLocation()
  const client = useQueryClient()
  const query = useOrder(id)
  const [agreed, setAgreed] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [reason, setReason] = useState('')
  const reasonLength = codePointLength(normalizeText(reason))
  const validCancellation = reasonLength > 0 && reasonLength <= 1000
  const [rejectionReason, setRejectionReason] = useState('')
  const rejectionLength = codePointLength(normalizeText(rejectionReason))
  const validRejection = rejectionLength > 0 && rejectionLength <= 1000
  const [description, setDescription] = useState('')
  const [resultUrl, setResultUrl] = useState('')
  const [revisionReason, setRevisionReason] = useState('')
  const descriptionLength = codePointLength(normalizeText(description))
  const revisionLength = codePointLength(normalizeText(revisionReason))
  const cleanUrl = normalizeText(resultUrl)
  const validDelivery =
    descriptionLength > 0 &&
    descriptionLength <= 5000 &&
    cleanUrl.length > 0 &&
    codePointLength(cleanUrl) <= 2048
  const validRevision = revisionLength > 0 && revisionLength <= 1000
  const sequence =
    query.data?.status === 'IN_PROGRESS'
      ? 1
      : query.data?.status === 'REVISION_IN_PROGRESS'
        ? 2
        : null
  const firstDelivery = query.data?.deliveries.find(
    (delivery) => delivery.sequenceNo === 1,
  )
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  const lock = useRef(false)
  async function act(
    action:
      | 'PAY'
      | 'CANCEL'
      | 'CONFIRM'
      | 'ACCEPT'
      | 'REJECT'
      | 'DELIVER'
      | 'REQUEST_REVISION',
  ) {
    if (lock.current || failure || !query.data?.allowedActions.includes(action))
      return
    if (
      (action === 'PAY' && !agreed) ||
      (action === 'CONFIRM' && !confirmed) ||
      (action === 'CANCEL' && !validCancellation)
    )
      return
    if (action === 'REJECT' && !validRejection) return
    if (action === 'DELIVER' && (!validDelivery || sequence === null)) return
    if (action === 'REQUEST_REVISION' && (!validRevision || !firstDelivery))
      return
    await run(async () =>
      action === 'PAY'
        ? await payOrder(id)
        : action === 'CANCEL'
          ? await cancelOrder(id, reason)
          : action === 'ACCEPT'
            ? await acceptOrder(id)
            : action === 'REJECT'
              ? await rejectOrder(id, rejectionReason)
              : action === 'DELIVER' && sequence !== null
                ? await deliverOrder(id, description, resultUrl, sequence)
                : action === 'REQUEST_REVISION' && firstDelivery
                  ? await requestOrderRevision(
                      id,
                      firstDelivery.id,
                      revisionReason,
                    )
                  : await confirmOrder(id),
    )
  }
  async function run(
    command: () => Promise<OrderDetailData>,
  ): Promise<boolean> {
    if (lock.current || failure || query.isFetching) return false
    lock.current = true
    setBusy(true)
    setMessage('')
    try {
      const result = await command()
      // 변경 응답을 개인 캐시에 직접 넣지 않고 현재 세션으로 재조회한다.
      setMessage(
        `주문 상태가 ${orderStatusLabel(result.status)}(으)로 확인됐습니다.`,
      )
      setAgreed(false)
      setConfirmed(false)
      setReason('')
      setRejectionReason('')
      setDescription('')
      setResultUrl('')
      setRevisionReason('')
      return true
    } catch (error) {
      // 결과 불명확 후 재조회가 다음 회차를 보여도 이전 납품 내용을 그대로 보내지 않는다.
      setDescription('')
      setResultUrl('')
      setFailure(
        error instanceof ApiError
          ? `${error.message} (${error.code})`
          : '처리 결과를 확인하지 못했습니다. 다시 요청하기 전에 주문 상태를 조회해 주세요.',
      )
      return false
    } finally {
      // 성공·실패 모두 조회로 금액/상태를 확인한다. 변경 요청은 자동 재전송하지 않는다.
      await client.invalidateQueries({ queryKey: ['wallet'] })
      lock.current = false
      setBusy(false)
    }
  }
  async function refresh() {
    const result = await query.refetch()
    if (!result.error) {
      setFailure(null)
      setAgreed(false)
      setConfirmed(false)
    }
  }
  if (query.error instanceof ApiError && query.error.status === 401)
    return (
      <section className={styles.orders}>
        <h1>주문 상세</h1>
        <p>로그인이 필요합니다.</p>
        <Link
          to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
        >
          로그인
        </Link>
        <p>
          <Link to={`/orders${location.search}`}>← 거래 내역</Link>
        </p>
      </section>
    )
  if (query.isError)
    return (
      <section className={styles.orders}>
        <h1>주문을 확인할 수 없습니다.</h1>
        <p role="alert">
          {query.error instanceof ApiError
            ? query.error.message
            : '주문을 불러오지 못했습니다.'}
        </p>
        <button
          onClick={() => {
            void refresh()
          }}
        >
          다시 조회
        </button>
        <p>
          <Link to={`/orders${location.search}`}>← 거래 내역</Link>
        </p>
      </section>
    )
  if (!query.data) return <p role="status">주문을 불러오는 중입니다.</p>
  const order = query.data
  const deadlines = orderDeadlineView(order, Date.now())
  const disabled = busy || !!failure || query.isFetching
  return (
    <section className={styles.orders}>
      <Link to={`/orders${location.search}`}>← 거래 내역</Link>
      <h1>{order.productSnapshot.title}</h1>
      <p>
        주문 #{order.id} · {orderStatusLabel(order.status)}
      </p>
      <h2>{formatWon(order.priceAmount)}</h2>
      <p>
        구매자 {order.buyer.displayName} · 판매자 {order.seller.displayName}
      </p>
      <h2>보관된 거래 조건</h2>
      <h3>상품 설명</h3>
      <p className={styles.text}>{order.productSnapshot.description}</p>
      <h3>작업 범위</h3>
      <p className={styles.text}>{order.productSnapshot.scopeDescription}</p>
      <h3>준비 사항</h3>
      <p className={styles.text}>
        {order.productSnapshot.preparationDescription}
      </p>
      <p>
        소요일 {order.durationDays}일
        {order.currentDueAt && ` · 납기 ${formatDateTime(order.currentDueAt)}`}
      </p>
      <h3>거래 규칙</h3>
      <p className={styles.text}>{order.termsSnapshot}</p>
      <h2>제출한 요구사항</h2>
      <p className={styles.text}>{order.requirements}</p>
      {deadlines.currentLate && (
        <p role="status">
          현재 작업 기한이 지났습니다. 주문 상태를 새로 조회해 확인해 주세요.
        </p>
      )}
      <h2>납품 내역</h2>
      {order.deliveries.length === 0 ? (
        <p>등록된 납품이 없습니다.</p>
      ) : (
        order.deliveries.map((delivery) => {
          const deadline = deadlines.deliveries.find(
            (item) => item.id === delivery.id,
          )
          return (
            <article key={delivery.id}>
              <h3>{delivery.sequenceNo}차 납품</h3>
              <p>
                제출 {formatDateTime(delivery.submittedAt)}
                {deadline?.dueAt &&
                  (deadline.late ? ' · 기한 후 제출' : ' · 기한 내 제출')}
              </p>
              <p className={styles.text}>{delivery.description}</p>
              {/^https:\/\//i.test(delivery.resultUrl) && (
                <a
                  href={delivery.resultUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  결과물 확인
                </a>
              )}
            </article>
          )
        })
      )}
      {order.payment && (
        <section>
          <h2>결제 기록</h2>
          <p>
            {order.payment.status === 'REFUNDED'
              ? '전액 환불됨'
              : order.payment.status === 'PAID'
                ? '결제 완료'
                : order.payment.status}{' '}
            · {formatWon(order.payment.amount)}
          </p>
          <p>
            결제 {formatDateTime(order.payment.paidAt)}
            {order.payment.refundedAt &&
              ` · 환불 ${formatDateTime(order.payment.refundedAt)}`}
          </p>
        </section>
      )}
      {order.review && (
        <section>
          <h2>구매 후기</h2>
          <p>
            {order.review.authorDisplayName} · 별점 {order.review.rating} / 5 ·{' '}
            {formatDateTime(order.review.createdAt)}
          </p>
          {order.review.content && (
            <p className={styles.text}>{order.review.content}</p>
          )}
        </section>
      )}
      {message && <p role="status">{message}</p>}
      {failure && <p role="alert">{failure}</p>}
      {order.revisionRequest && (
        <article>
          <h2>수정 요청</h2>
          <p className={styles.text}>{order.revisionRequest.reason}</p>
          <p>
            요청 시각 {formatDateTime(order.revisionRequest.requestedAt)} · 수정
            기한 {formatDateTime(order.revisionRequest.dueAt)}
          </p>
        </article>
      )}
      <button
        disabled={busy || query.isFetching}
        onClick={() => {
          void refresh()
        }}
      >
        현재 상태 다시 조회
      </button>
      {order.allowedActions.includes('DELIVER') && sequence !== null && (
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
          <button
            disabled={!validDelivery}
            onClick={() => {
              void act('DELIVER')
            }}
          >
            {sequence}차 납품 제출
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('REQUEST_REVISION') && firstDelivery && (
        <fieldset disabled={disabled}>
          <legend>최초 납품 수정 요청</legend>
          <p>보관된 거래 범위 안에서 한 번 요청할 수 있습니다.</p>
          <label>
            수정 사유
            <textarea
              value={revisionReason}
              onChange={(e) => setRevisionReason(e.target.value)}
            />
          </label>
          <p>{revisionLength} / 1,000자</p>
          <button
            disabled={!validRevision}
            onClick={() => {
              void act('REQUEST_REVISION')
            }}
          >
            수정 요청
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('ACCEPT') && (
        <fieldset disabled={disabled}>
          <legend>주문 수락</legend>
          <p>
            보관된 거래 조건과 구매자의 요구사항을 확인해 주세요. 수락하면
            구매자가 결제할 수 있습니다.
          </p>
          <button
            onClick={() => {
              void act('ACCEPT')
            }}
          >
            주문 수락
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('REJECT') && (
        <fieldset disabled={disabled}>
          <legend>주문 거절</legend>
          <label>
            거절 사유 (필수, 최대 1,000자)
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
            />
          </label>
          <p>{rejectionLength} / 1,000자</p>
          <p>
            거절하면 이 주문은 종료됩니다. 작성한 사유는 구매자에게 전달됩니다.
          </p>
          <button
            disabled={!validRejection}
            onClick={() => {
              void act('REJECT')
            }}
          >
            주문 거절
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('PAY') && (
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
          <button
            disabled={!agreed}
            onClick={() => {
              void act('PAY')
            }}
          >
            적립금으로 결제
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('CANCEL') && (
        <fieldset disabled={disabled}>
          <legend>직접 취소</legend>
          <p>
            {order.payment?.status === 'PAID'
              ? '결제된 주문은 전액 적립금 환불과 함께 취소됩니다.'
              : '결제 전 주문은 적립금 차감·환불 없이 취소됩니다.'}
          </p>
          <label>
            취소 사유 (필수, 최대 1,000자)
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <p>{reasonLength} / 1,000자</p>
          <button
            disabled={!validCancellation}
            onClick={() => {
              void act('CANCEL')
            }}
          >
            주문 취소
          </button>
        </fieldset>
      )}
      {order.allowedActions.includes('CONFIRM') && (
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
          <button
            disabled={!confirmed}
            onClick={() => {
              void act('CONFIRM')
            }}
          >
            구매 확정
          </button>
        </fieldset>
      )}
      <CancellationActions
        key={order.cancellationRequests
          .map((item) => `${item.id}:${item.status}`)
          .join('|')}
        order={order}
        disabled={disabled}
        run={run}
      />
      <OrderHistoryList order={order} />
      <Link to="/mypage/settlements">판매 수익 확인 →</Link>
    </section>
  )
}
