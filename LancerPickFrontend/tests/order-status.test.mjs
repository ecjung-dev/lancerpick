import assert from 'node:assert/strict'
import { test } from 'node:test'
import { cancellationStatusLabel, orderStatusLabel, orderStatuses } from '../src/features/orders/status.ts'

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
