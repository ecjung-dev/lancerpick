import assert from 'node:assert/strict'
import { test } from 'node:test'
import { cancellationStatusLabel, orderStatusLabel, orderStatusTones, orderStatuses } from '../src/features/orders/status.ts'
import { toneOf } from '../src/shared/lib/statusTone.ts'

test('order list and detail share a Korean label for every known status', () => {
  assert.equal(orderStatuses.length, 8)
  for (const status of orderStatuses) assert.notEqual(orderStatusLabel(status), status)
  assert.equal(orderStatusLabel('UNKNOWN'), 'UNKNOWN')
})

test('cancellation request states use understandable labels', () => {
  assert.equal(cancellationStatusLabel('PENDING'), '처리 대기')
  assert.equal(cancellationStatusLabel('APPROVED'), '승인됨')
  assert.equal(cancellationStatusLabel('REJECTED'), '거절됨')
  assert.equal(cancellationStatusLabel('WITHDRAWN'), '철회됨')
  assert.equal(cancellationStatusLabel('UNKNOWN'), 'UNKNOWN')
})

test('주문 상태 배지는 구매 확정만 완료 색이고 거절·취소·종료와 진행 상태는 기본 색이다', () => {
  assert.equal(toneOf(orderStatusTones, 'COMPLETED'), 'ok')
  for (const status of ['REJECTED', 'CANCELLED', 'TERMINATED', 'REQUESTED', 'IN_PROGRESS', 'AWAITING_CONTRACT', 'NEW_STATUS'])
    assert.equal(toneOf(orderStatusTones, status), 'neutral', status)
})
