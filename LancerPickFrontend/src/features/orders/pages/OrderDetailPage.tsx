import { loginPath } from '../../auth/session'
import { useOrder } from '../queries'
import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import styles from './Orders.module.css'
import { CancellationActions } from './CancellationActions'
import { CancellationReviewRequest } from '../../inquiries/components/CancellationReviewRequest'
import {
  AcceptForm,
  CancelForm,
  ConfirmForm,
  DeliveryForm,
  PayForm,
  RejectForm,
  RevisionForm,
} from './OrderCommandForms'
import type { OrderDetail as OrderDetailData } from '../types'
import { OrderHistoryList } from './OrderHistoryList'
import { orderDeadlineView } from '../deadlines'
import { orderStatusLabel } from '../status'
import { orderCommandFailureMessage } from '../failure'
import { ReviewForm } from '../../reviews/components/ReviewForm'
import { ContractPanel } from '../../contracts/components/ContractPanel'
import { OrderFundsPanel } from '../components/OrderFundsPanel'
import { ChatEntry } from '../../order-chat/components/ChatEntry'
import { ReviewTimerPanel } from '../components/ReviewTimerPanel'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  return <OrderDetail key={id} id={id} />
}
function OrderDetail({ id }: { id: string }) {
  const location = useLocation()
  const client = useQueryClient()
  const query = useOrder(id)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  // 폼 입력 초기화 시점. 성공하면 모든 폼, 실패하면 납품 입력, 재조회 성공 시 동의 체크를 비운다.
  const [resets, setResets] = useState({ success: 0, failure: 0, refresh: 0 })
  const lock = useRef(false)
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
      setResets((value) => ({ ...value, success: value.success + 1 }))
      return true
    } catch (error) {
      // 결과 불명확 후 재조회가 다음 회차를 보여도 이전 납품 내용을 그대로 보내지 않는다.
      setResets((value) => ({ ...value, failure: value.failure + 1 }))
      setFailure(orderCommandFailureMessage(error))
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
      setResets((value) => ({ ...value, refresh: value.refresh + 1 }))
    }
  }
  if (isUnauthorized(query.error))
    return (
      <section className={styles.orders}>
        <h1>주문 상세</h1>
        <p>로그인이 필요합니다.</p>
        <Link
          to={loginPath(location.pathname + location.search)}
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
  const deadlines = orderDeadlineView(order)
  const disabled = busy || !!failure || query.isFetching
  const forms = { orderId: id, order, disabled, run }
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
      {order.policyVersion === 'CONTRACT_V3' && (
        <OrderFundsPanel orderId={order.id} orderStatus={order.status} />
      )}
      {order.policyVersion === 'CONTRACT_V3' && (
        <ReviewTimerPanel orderId={order.id} sellerId={order.seller.id} />
      )}
      {['CONTRACT_V2', 'CONTRACT_V3'].includes(order.policyVersion) && (
        <ContractPanel
          key={order.id}
          orderId={order.id}
          sellerId={order.seller.id}
          orderStatus={order.status}
          policyVersion={order.policyVersion}
          hasContractHistory={order.histories.some(history =>
            ['CONTRACT_CREATED', 'CONTRACT_SIGNED'].includes(history.action),
          )}
        />
      )}
      <h3>상품 설명</h3>
      <h2>보관된 거래 조건</h2>
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
      {!order.review && order.allowedActions.includes('WRITE_REVIEW') && (
        <ReviewForm orderId={order.id} productId={order.productId} />
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
      <DeliveryForm
        key={`deliver-${resets.success}-${resets.failure}`}
        {...forms}
      />
      <RevisionForm key={`revision-${resets.success}`} {...forms} />
      <AcceptForm {...forms} />
      <RejectForm key={`reject-${resets.success}`} {...forms} />
      <PayForm key={`pay-${resets.success}-${resets.refresh}`} {...forms} />
      <CancelForm key={`cancel-${resets.success}`} {...forms} />
      <ConfirmForm
        key={`confirm-${resets.success}-${resets.refresh}`}
        {...forms}
      />
      <CancellationActions
        key={order.cancellationRequests
          .map((item) => `${item.id}:${item.status}`)
          .join('|')}
        order={order}
        disabled={disabled}
        run={run}
      />
      <CancellationReviewRequest order={order} disabled={disabled} />
      <OrderHistoryList order={order} />
      <ChatEntry orderId={order.id} />
      <Link to="/mypage/settlements">판매 수익 확인 →</Link>
    </section>
  )
}
