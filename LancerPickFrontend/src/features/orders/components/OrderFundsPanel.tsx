import { useOrderFunds } from '../queries'
import type { OrderFunds } from '../types'
import { formatWon, formatDateTime } from '../../../shared/lib/format'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'

interface OrderFundsPanelProps {
  orderId: string
  orderStatus: string
}

const statusLabels: Record<OrderFunds['status'], string> = {
  NOT_PAID: '전액 결제 대기',
  HELD: '주문 대금 보관 중',
  RELEASED: '판매자 지급 완료',
  REFUNDED: '구매자 전액 반환 완료',
}

export function OrderFundsPanel({ orderId, orderStatus }: OrderFundsPanelProps) {
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
