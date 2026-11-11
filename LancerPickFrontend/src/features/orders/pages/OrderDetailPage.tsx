import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Icon } from '../../../shared/components/Icon'
import { formatDateTime } from '../../../shared/lib/format'
import { loginPath } from '../../auth/session'
import { useCurrentMember } from '../../auth/queries'
import { ContractPanel } from '../../contracts/components/ContractPanel'
import { CancellationReviewRequest } from '../../inquiries/components/CancellationReviewRequest'
import { ReviewForm } from '../../reviews/components/ReviewForm'
import { OrderDetailSummary } from '../components/OrderDetailSummary'
import { ReviewTimerPanel } from '../components/ReviewTimerPanel'
import { orderDeadlineView } from '../deadlines'
import { orderDetailPresentation, orderDetailTabs } from '../detailPresentation'
import type { OrderDetailTab } from '../detailPresentation'
import { orderCommandFailureMessage } from '../failure'
import { useOrder } from '../queries'
import { orderStatusLabel } from '../status'
import type { OrderDetail as OrderDetailData } from '../types'
import { CancellationActions } from './CancellationActions'
import { AcceptForm, CancelForm, ConfirmForm, DeliveryForm, PayForm, RejectForm, RevisionForm } from './OrderCommandForms'
import { OrderConditions, OrderDeliveries, OrderRecords } from './OrderDetailSections'
import styles from './OrderDetailPage.module.css'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  return <OrderDetail key={id} id={id} />
}

function OrderDetail({ id }: { id: string }) {
  const location = useLocation()
  const client = useQueryClient()
  const query = useOrder(id)
  const member = useCurrentMember()
  const [tab, setTab] = useState<OrderDetailTab>('progress')
  const tabButtons = useRef<(HTMLButtonElement | null)[]>([])
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  // 기존 입력 초기화 경계를 유지하고 탭 전환으로 폼을 폐기하지 않는다.
  const [resets, setResets] = useState({ success: 0, failure: 0, refresh: 0 })
  const lock = useRef(false)

  async function run(command: () => Promise<OrderDetailData>): Promise<boolean> {
    if (lock.current || failure || query.isFetching) return false
    lock.current = true
    setBusy(true)
    setMessage('')
    try {
      const result = await command()
      // 변경 응답을 개인 캐시에 직접 넣지 않고 현재 세션으로 재조회한다.
      setMessage(`주문 상태가 ${orderStatusLabel(result.status)}(으)로 확인됐습니다.`)
      setResets(value => ({ ...value, success: value.success + 1 }))
      return true
    } catch (error) {
      setResets(value => ({ ...value, failure: value.failure + 1 }))
      setFailure(orderCommandFailureMessage(error))
      return false
    } finally {
      // 성공·실패 모두 상태를 다시 조회하며 변경 요청은 자동 재전송하지 않는다.
      await client.invalidateQueries({ queryKey: ['wallet'] })
      lock.current = false
      setBusy(false)
    }
  }

  async function refresh() {
    const result = await query.refetch()
    if (!result.error) {
      setFailure(null)
      setResets(value => ({ ...value, refresh: value.refresh + 1 }))
    }
  }

  if (isUnauthorized(query.error)) return <section className={styles.page}>
    <h1>주문 상세</h1><p>로그인이 필요합니다.</p>
    <Link to={loginPath(location.pathname + location.search)}>로그인</Link>
    <p><Link to={`/orders${location.search}`}>← 거래 내역</Link></p>
  </section>
  if (query.isError) return <section className={styles.page}>
    <h1>주문을 확인할 수 없습니다.</h1>
    <p role="alert">{query.error instanceof ApiError ? query.error.message : '주문을 불러오지 못했습니다.'}</p>
    <Button variant="secondary" onClick={() => void refresh()}>다시 조회</Button>
    <p><Link to={`/orders${location.search}`}>← 거래 내역</Link></p>
  </section>
  if (!query.data) return <p className={styles.page} role="status">주문을 불러오는 중입니다.</p>

  const order = query.data
  const isSeller = member.isSuccess && member.data?.id === order.seller.id
  const view = orderDetailPresentation(order, isSeller)
  const deadlines = orderDeadlineView(order)
  const disabled = busy || !!failure || query.isFetching
  const forms = { orderId: id, order, disabled, run }
  const hasContract = order.policyVersion !== 'LEGACY_V1'
  const hasPrimaryAction = ['DELIVER', 'REQUEST_REVISION', 'ACCEPT', 'PAY', 'CONFIRM']
    .some(action => order.allowedActions.includes(action))

  return <section className={styles.page}>
    <Link className={styles.backLink} to={`/orders${location.search}`}>← 거래 내역</Link>
    <header className={styles.header}>
      <div><p className={styles.eyebrow}>거래 상세</p><h1>{order.productSnapshot.title}</h1>
        <p className={styles.meta}>주문 #{order.id} · 구매자 {order.buyer.displayName} · 판매자 {order.seller.displayName}</p>
      </div>
      <span className={styles.status}>{orderStatusLabel(order.status)}</span>
    </header>
    {view.stage !== null && <ol className={styles.steps} aria-label="거래 진행 단계">
      {view.steps.map((step, index) => <li key={step}
        className={index <= (view.stage ?? -1) ? styles.stepReached : undefined}
        aria-current={index === view.stage ? 'step' : undefined}>
        <span className={styles.stepDot} aria-hidden="true" /><span>{step}</span>
      </li>)}
    </ol>}
    {message && <p className={styles.feedback} role="status">{message}</p>}
    {failure && <div className={styles.feedback} role="alert"><p>{failure}</p>
      <Button variant="secondary" disabled={busy || query.isFetching} onClick={() => void refresh()}>상태 다시 확인</Button>
    </div>}

    <div className={styles.layout}>
      <div className={styles.main}>
        <section className={styles.banner} aria-labelledby="order-current-title">
          <p className={styles.eyebrow}>현재 진행 상황</p>
          <h2 id="order-current-title">{view.title}</h2><p>{view.description}</p>
          {order.currentDueAt && <p className={styles.meta}>현재 납기 {formatDateTime(order.currentDueAt)}</p>}
          {deadlines.currentLate && <p className={styles.late} role="status">현재 작업 기한이 지났습니다. 최신 거래 상태를 확인해 주세요.</p>}
          {order.status === 'AWAITING_CONTRACT' && <Button variant="secondary" onClick={() => setTab('conditions')}>계약 작성·서명 확인</Button>}
          {view.pendingCancellation && <a className={styles.inlineLink} href="#order-secondary-actions" onClick={() => setTab('progress')}>취소 요청 확인 →</a>}
        </section>
        <div role="tablist" aria-label="거래 상세 보기" className={styles.tabs}>
          {orderDetailTabs.map((item, index) => <button key={item.id} type="button" role="tab"
            id={`order-tab-${item.id}`} aria-controls={`order-panel-${item.id}`}
            aria-selected={tab === item.id} tabIndex={tab === item.id ? 0 : -1}
            ref={element => { tabButtons.current[index] = element }}
            onClick={() => setTab(item.id)} onKeyDown={event => {
              const count = orderDetailTabs.length
              const next = event.key === 'ArrowRight' ? (index + 1) % count
                : event.key === 'ArrowLeft' ? (index + count - 1) % count
                : event.key === 'Home' ? 0 : event.key === 'End' ? count - 1 : null
              if (next === null) return
              event.preventDefault()
              setTab(orderDetailTabs[next].id)
              tabButtons.current[next]?.focus()
            }}>{item.label}</button>)}
        </div>
        {/* hidden으로 유지해 동의·입력값·취소 요청 UUID를 탭 전환 시 잃지 않는다. */}
        <div id="order-panel-progress" role="tabpanel" aria-labelledby="order-tab-progress" hidden={tab !== 'progress'} className={styles.panel}>
          <OrderDeliveries order={order} />
          {order.revisionRequest && <section className={styles.card}><h2>수정 요청</h2>
            <p className={styles.text}>{order.revisionRequest.reason}</p>
            <p className={styles.meta}>요청 {formatDateTime(order.revisionRequest.requestedAt)} · 수정 기한 {formatDateTime(order.revisionRequest.dueAt)}</p>
          </section>}
          {hasPrimaryAction && <section className={`${styles.card} ${styles.commands}`} aria-label="거래 처리">
            <h2>거래 처리</h2>
            <DeliveryForm key={`deliver-${resets.success}-${resets.failure}`} {...forms} />
            <RevisionForm key={`revision-${resets.success}`} {...forms} />
            <AcceptForm {...forms} />
            <PayForm key={`pay-${resets.success}-${resets.refresh}`} {...forms} />
            <ConfirmForm key={`confirm-${resets.success}-${resets.refresh}`} {...forms} />
          </section>}
          {order.policyVersion === 'CONTRACT_V3' && <div className={styles.timer}><ReviewTimerPanel orderId={order.id} sellerId={order.seller.id} /></div>}
          {!order.review && order.allowedActions.includes('WRITE_REVIEW') && <div className={styles.card}><ReviewForm orderId={order.id} productId={order.productId} /></div>}
          {order.review && <section className={styles.card}><h2>구매 후기</h2>
            <p>{order.review.authorDisplayName} · 별점 {order.review.rating} / 5</p>
            <p className={styles.meta}>{formatDateTime(order.review.createdAt)}</p>
            {order.review.content && <p className={styles.text}>{order.review.content}</p>}
          </section>}
          <details className={styles.secondaryActions} id="order-secondary-actions" open={view.pendingCancellation || undefined}>
            <summary>취소·문의 <Icon name="chevron" size={18} /></summary>
            <div>
              <RejectForm key={`reject-${resets.success}`} {...forms} />
              <CancelForm key={`cancel-${resets.success}`} {...forms} />
              <CancellationActions key={order.cancellationRequests.map(item => `${item.id}:${item.status}`).join('|')} order={order} disabled={disabled} run={run} />
              <CancellationReviewRequest order={order} disabled={disabled} />
            </div>
          </details>
        </div>
        <div id="order-panel-conditions" role="tabpanel" aria-labelledby="order-tab-conditions" hidden={tab !== 'conditions'} className={styles.panel}>
          {hasContract && <div className={`${styles.card} ${styles.contract}`}><ContractPanel key={order.id} orderId={order.id}
            sellerId={order.seller.id} orderStatus={order.status} policyVersion={order.policyVersion}
            hasContractHistory={order.histories.some(history => ['CONTRACT_CREATED', 'CONTRACT_SIGNED'].includes(history.action))} /></div>}
          <OrderConditions order={order} />
        </div>
        <div id="order-panel-records" role="tabpanel" aria-labelledby="order-tab-records" hidden={tab !== 'records'} className={styles.panel}>
          <OrderRecords order={order} />
        </div>
        <div className={styles.refreshRow}><Button variant="secondary" disabled={busy || query.isFetching} onClick={() => void refresh()}>현재 상태 다시 조회</Button></div>
      </div>
      <OrderDetailSummary order={order} isSeller={isSeller} />
    </div>
  </section>
}
