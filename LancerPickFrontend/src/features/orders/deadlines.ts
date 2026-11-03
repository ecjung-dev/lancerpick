import type { OrderDetail } from './types'

/** 기한과 같은 시각은 지각이 아니다. 서버의 상태·기한 원문만 사용한다. */
export function orderDeadlineView(order: Pick<OrderDetail, 'status' | 'currentDueAt' | 'originalDueAt' | 'revisionDueAt' | 'deliveries'>, now: number) {
  const active = order.status === 'IN_PROGRESS' || order.status === 'REVISION_IN_PROGRESS'
  const currentLate = active && order.currentDueAt !== null && now > Date.parse(order.currentDueAt)
  const deliveries = order.deliveries.map(delivery => {
    const dueAt = delivery.sequenceNo === 1 ? order.originalDueAt : delivery.sequenceNo === 2 ? order.revisionDueAt : null
    return { id: delivery.id, dueAt, late: dueAt !== null && Date.parse(delivery.submittedAt) > Date.parse(dueAt) }
  })
  return { currentLate, deliveries }
}
