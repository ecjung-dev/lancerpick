import type { OrderDetail } from './types'

function epochMicros(value: string): bigint | null {
  const milliseconds = Date.parse(value)
  if (!Number.isFinite(milliseconds)) return null
  const fraction = value.match(/\.(\d+)(?:Z|[+-]\d{2}:\d{2})$/)?.[1]?.padEnd(6, '0') ?? '000000'
  return BigInt(milliseconds) * 1000n + BigInt(fraction.slice(3, 6))
}

/** 기한과 같은 시각은 지각이 아니다. 서버의 상태·기한 원문만 사용한다. */
export function orderDeadlineView(order: Pick<OrderDetail, 'status' | 'currentDueAt' | 'originalDueAt' | 'revisionDueAt' | 'deliveries'>, now: number) {
  const active = order.status === 'IN_PROGRESS' || order.status === 'REVISION_IN_PROGRESS'
  const currentDueMicros = order.currentDueAt === null ? null : epochMicros(order.currentDueAt)
  const currentLate = active && currentDueMicros !== null && Number.isFinite(now) && BigInt(Math.trunc(now)) * 1000n > currentDueMicros
  const deliveries = order.deliveries.map(delivery => {
    const dueAt = delivery.sequenceNo === 1 ? order.originalDueAt : delivery.sequenceNo === 2 ? order.revisionDueAt : null
    const dueMicros = dueAt === null ? null : epochMicros(dueAt)
    const submittedMicros = epochMicros(delivery.submittedAt)
    return { id: delivery.id, dueAt, late: dueMicros !== null && submittedMicros !== null && submittedMicros > dueMicros }
  })
  return { currentLate, deliveries }
}
