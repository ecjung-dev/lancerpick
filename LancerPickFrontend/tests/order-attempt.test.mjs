import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readOrderAttempt, saveOrderAttempt, clearOrderAttempt, isDefinitiveOrderRejection } from '../src/features/orders/attempt.ts'

const input = { productId: '9007199254740993', expectedProductVersion: 2, requirements: '  요청  ', requestKey: 'b89eb754-8052-489f-ac52-e5d4a367d12f' }
function storage() {
  const map = new Map()
  return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key) }
}
test('reload restores an identical normalized request scoped to account and product', () => {
  const store = storage()
  saveOrderAttempt(store, '1', input)
  assert.deepEqual(readOrderAttempt(store, '1', input.productId), { ...input, requirements: '요청' })
  assert.equal(readOrderAttempt(store, '2', input.productId), null)
  assert.equal(readOrderAttempt(store, '1', '3'), null)
  clearOrderAttempt(store, '1', input.productId)
  assert.equal(readOrderAttempt(store, '1', input.productId), null)
})
test('only definitive rejections, including member restriction, allow clearing the saved request', () => {
  for (const status of [400, 404, 409]) assert.equal(isDefinitiveOrderRejection(status, 'ANY'), true)
  assert.equal(isDefinitiveOrderRejection(403, 'MEMBER_RESTRICTED'), true)
  assert.equal(isDefinitiveOrderRejection(403, 'CSRF_INVALID'), false)
  assert.equal(isDefinitiveOrderRejection(403, 'FORBIDDEN'), false)
  assert.equal(isDefinitiveOrderRejection(401, 'AUTHENTICATION_REQUIRED'), false)
  assert.equal(isDefinitiveOrderRejection(500, 'INTERNAL_ERROR'), false)
})
test('corrupt or unavailable storage fails closed rather than silently generating a new key', () => {
  assert.throws(() => readOrderAttempt({ getItem: () => '{bad' }, '1', input.productId))
  assert.throws(() => saveOrderAttempt({ setItem: () => { throw new Error('blocked') } }, '1', input))
})
