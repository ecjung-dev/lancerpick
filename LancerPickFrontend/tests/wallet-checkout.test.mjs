import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { CHECKOUT_STORAGE_KEY, clearCheckoutState, confirmCheckoutOnce, isTopupId, matchesPendingTopup, parseTopupAmount, readPendingCheckout, readTopupCallback, savePendingCheckout } from '../src/features/wallet/checkoutFlow.ts'
import { isTossTestClientKey } from '../src/features/wallet/tossCheckout.ts'

afterEach(() => clearCheckoutState())
const now = 1800000000000
const pending = { id: '9007199254740993', orderId: 'topup_123456', amount: 50000, nonce: '12345678-1234-1234-1234-123456789abc', createdAt: now }
const callback = () => new URLSearchParams({ nonce: pending.nonce, orderId: pending.orderId, amount: '50000', paymentKey: 'test_payment', paymentType: 'NORMAL' })
function storage() {
  const values = new Map()
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}

test('amount and BIGINT identifiers are checked without rounding or truncation', () => {
  assert.equal(parseTopupAmount('1'), 1)
  assert.equal(parseTopupAmount('1000000'), 1000000)
  for (const amount of ['0', '-1', '1.5', '1000001', '1e3', ' 50000', '01', 'Infinity']) assert.equal(parseTopupAmount(amount), null)
  assert.equal(isTopupId('9007199254740993'), true)
  assert.equal(isTopupId('9223372036854775808'), false)
  assert.equal(isTopupId('../1'), false)
})

test('callback requires original session marker, nonce, id, order and amount', () => {
  assert.deepEqual(readTopupCallback(callback(), pending, pending.id), { paymentKey: 'test_payment', orderId: pending.orderId, amount: 50000 })
  assert.equal(readTopupCallback(callback(), null, pending.id), null)
  assert.equal(readTopupCallback(callback(), pending, '2'), null)
  for (const [key, value] of [['nonce', 'forged'], ['orderId', 'other_order'], ['amount', '1'], ['paymentKey', 'bad key'], ['paymentType', 'BRANDPAY']]) {
    const params = callback(); params.set(key, value)
    assert.equal(readTopupCallback(params, pending, pending.id), null)
  }
  const duplicate = callback(); duplicate.append('amount', '1')
  assert.equal(readTopupCallback(duplicate, pending, pending.id), null)
})

test('only matching test topup can be approved after server ownership lookup', () => {
  const topup = { id: pending.id, tossOrderId: pending.orderId, amount: 50000, currency: 'KRW', isTest: true, status: 'READY', createdAt: '2030-01-01T00:00:00Z' }
  assert.equal(matchesPendingTopup(topup, pending), true)
  assert.equal(matchesPendingTopup({ ...topup, isTest: false }, pending), false)
  assert.equal(matchesPendingTopup({ ...topup, amount: 1 }, pending), false)
})

test('session storage excludes paymentKey and rejects malformed, expired or blocked context', () => {
  const target = storage()
  savePendingCheckout({ ...pending, paymentKey: 'must-not-be-stored' }, target)
  assert.equal(target.getItem(CHECKOUT_STORAGE_KEY).includes('paymentKey'), false)
  assert.deepEqual(readPendingCheckout(target, now), pending)
  assert.equal(readPendingCheckout(target, now + 31 * 60 * 1000), null)
  target.setItem(CHECKOUT_STORAGE_KEY, '{broken')
  assert.equal(readPendingCheckout(target, now), null)
  assert.equal(readPendingCheckout({ getItem() { throw Error('blocked') } }, now), null)
})

test('StrictMode or repeated callback runs approval once, including after completion', async () => {
  let calls = 0
  const operation = async () => { calls++; return { status: 'COMPLETED' } }
  await Promise.all([confirmCheckoutOnce(pending.id, operation), confirmCheckoutOnce(pending.id, operation)])
  await confirmCheckoutOnce(pending.id, operation)
  assert.equal(calls, 1)
})

test('approval failure is not automatically retried and logout clears flow state', async () => {
  let calls = 0
  const operation = async () => { calls++; throw Error('network failure') }
  await assert.rejects(confirmCheckoutOnce(pending.id, operation))
  await assert.rejects(confirmCheckoutOnce(pending.id, operation))
  assert.equal(calls, 1)
  const target = storage(); savePendingCheckout({ ...pending, createdAt: Date.now() }, target)
  clearCheckoutState(target)
  assert.equal(target.getItem(CHECKOUT_STORAGE_KEY), null)
  await confirmCheckoutOnce(pending.id, async () => { calls++ })
  assert.equal(calls, 2)
})

test('SDK accepts only the test gck client key type', () => {
  assert.equal(isTossTestClientKey('test_gck_unit'), true)
  for (const key of ['live_gck_unit', 'test_gsk_unit', 'test_ck_unit', 'CHANGE_ME', '']) assert.equal(isTossTestClientKey(key), false)
})
