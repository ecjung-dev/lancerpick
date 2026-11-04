import assert from 'node:assert/strict'
import { test } from 'node:test'
import { orderStatusLabel, orderStatuses } from '../src/features/orders/status.ts'

test('order list and detail share a Korean label for every known status', () => {
  assert.equal(orderStatuses.length, 8)
  for (const status of orderStatuses) assert.notEqual(orderStatusLabel(status), status)
  assert.equal(orderStatusLabel('UNKNOWN'), 'UNKNOWN')
})
