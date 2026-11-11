import { useOrderFunds } from '../queries'
import type { OrderFunds } from '../types'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'

interface OrderFundsPanelProps {
  orderId: string
  orderStatus: string
  compact?: boolean
  sellerView?: boolean
  feeRateBps?: number
}

const statusLabels: Record<OrderFunds['status'], string> = {
  NOT_PAID: '전액 결제 대기',
  HELD: '주문 대금 보관 중',
  RELEASED: '판매자 지급 완료',
  REFUNDED: '구매자 전액 반환 완료',
}

export function OrderFundsPanel({ orderId, orderStatus, compact = false, sellerView = false, feeRateBps }: OrderFundsPanelProps) {
  const query = useOrderFunds(orderId)

  if (query.isError) {
    return (
      <section>
        <h2>거래 대금</h2>
        <AsyncState
          title="거래 대금을 확인하지 못했어요"
          description={query.error.message}
          error={query.error}
        />
        <Button
          variant="secondary"
          disabled={query.isFetching}
          onClick={() => {
            void query.refetch()
          }}
        >
          대금 상태 다시 조회
        </Button>
      </section>
    )
  }

  if (!query.data) {
    return <AsyncState title="거래 대금을 확인하고 있습니다." />
  }

  const funds = query.data
  const stateLabel = funds.status === 'NOT_PAID' && ['CANCELLED', 'REJECTED'].includes(orderStatus)
    ? '결제 없이 종료된 거래입니다.' : statusLabels[funds.status]

  if (compact) {
    // 예상 표시는 결제 응답에 보관된 수수료율만 사용한다. 실제 지급액은 funds API가 기준이다.
    const estimatedFee = sellerView && funds.status === 'HELD' && feeRateBps !== undefined
      && Number.isInteger(feeRateBps) && feeRateBps >= 0 && feeRateBps <= 10000
      ? Math.floor(funds.amount * feeRateBps / 10000) : null
    return <section aria-label="거래 대금">
      <h3>대금 상태</h3>
      <p>{stateLabel}</p>
      <dl>
        <div><dt>현재 보관금</dt><dd>{formatWon(funds.heldAmount)}</dd></div>
        {estimatedFee !== null && <>
          <div><dt>예상 수수료</dt><dd>{formatWon(estimatedFee)}</dd></div>
          <div><dt>예상 판매 수익</dt><dd>{formatWon(funds.amount - estimatedFee)}</dd></div>
        </>}
        {funds.feeAmount !== null && <div><dt>확정 수수료</dt><dd>{formatWon(funds.feeAmount)}</dd></div>}
        {funds.sellerPayout !== null && <div><dt>판매자 적립금 지급</dt><dd>{formatWon(funds.sellerPayout)}</dd></div>}
        {funds.refundedAmount > 0 && <div><dt>구매자 반환</dt><dd>{formatWon(funds.refundedAmount)}</dd></div>}
      </dl>
      {funds.status === 'HELD' && <p><small>구매 확정 전까지 판매자에게 지급되지 않습니다. {estimatedFee !== null && '예상 금액은 실제 확정 결과와 구분됩니다.'}</small></p>}
      {funds.resolvedAt && <p><small>처리 시각 {formatDateTime(funds.resolvedAt)}</small></p>}
    </section>
  }

  return (
    <section aria-label="거래 대금">
      <h2>거래 대금</h2>
      <p>{funds.status === 'NOT_PAID' && ['CANCELLED', 'REJECTED'].includes(orderStatus)
        ? '결제 없이 종료된 거래입니다.' : statusLabels[funds.status]}</p>
      <p>
        계약 총액 {formatWon(funds.amount)} · 현재 보관금{' '}
        {formatWon(funds.heldAmount)}
      </p>
      {funds.status === 'HELD' && (
        <p>
          구매 확정 전까지 판매자에게 지급되지 않습니다. 확정 시 수수료 10%를
          공제한 금액이 판매자의 구매용 적립금으로 지급됩니다.
        </p>
      )}
      {funds.feeAmount !== null && (
        <p>확정 수수료 {formatWon(funds.feeAmount)}</p>
      )}
      {funds.sellerPayout !== null && (
        <p>판매자 적립금 지급 {formatWon(funds.sellerPayout)}</p>
      )}
      {funds.refundedAmount > 0 && (
        <p>구매자 반환 {formatWon(funds.refundedAmount)}</p>
      )}
      {funds.resolvedAt && <p>처리 시각 {formatDateTime(funds.resolvedAt)}</p>}
    </section>
  )
}
