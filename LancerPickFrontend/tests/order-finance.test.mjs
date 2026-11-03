import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { getOrder, payOrder, cancelOrder, confirmOrder, acceptOrder, rejectOrder } from '../src/features/orders/api.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'
const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

test('seller decisions send CSRF and reason without client identity and preserve large IDs', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    calls.push([url, options.body])
    return json({ id: '9007199254740993', status: 'REJECTED' })
  }
  await acceptOrder('9007199254740993')
  await rejectOrder('9007199254740993', '일정상 어렵습니다.')
  assert.deepEqual(calls, [['/api/orders/9007199254740993/accept', undefined], ['/api/orders/9007199254740993/reject', JSON.stringify({ reason: '일정상 어렵습니다.' })]])
})

test('seller decisions propagate errors without replay and reject invalid IDs before network', async () => {
  for (const status of [400, 401, 403, 404, 409, 500]) {
    clearCsrfToken()
    let writes = 0
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
      writes++
      return json({ code: 'DECISION_FAILED', message: 'failed', fieldErrors: [] }, status)
    }
    await assert.rejects(acceptOrder('1'), error => error.status === status)
    await assert.rejects(rejectOrder('1', '사유'), error => error.status === status)
    assert.equal(writes, 2)
  }
  let calls = 0
  globalThis.fetch = async () => { calls++; throw new Error('network') }
  for (const id of ['0', '-1', '../me', '9223372036854775808']) {
    assert.throws(() => acceptOrder(id))
    assert.throws(() => rejectOrder(id, '사유'))
  }
  assert.equal(calls, 0)
})
test('order financial commands use CSRF and send no client amount or member id', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test')
    calls.push([url, options.body])
    return json({ id: '9007199254740993', status: 'COMPLETED' })
  }
  await payOrder('9007199254740993'); await cancelOrder('9007199254740993', 'reason'); await confirmOrder('9007199254740993')
  assert.deepEqual(calls, [['/api/orders/9007199254740993/payment', '{"agreedToTerms":true}'], ['/api/orders/9007199254740993/cancel', '{"reason":"reason"}'], ['/api/orders/9007199254740993/confirm', undefined]])
})
test('invalid identifiers never reach network and failed payment is not replayed', async () => {
  let writes = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' })
    writes++; return json({ code: 'INSUFFICIENT_BALANCE', message: 'insufficient', fieldErrors: [], traceId: 'test' }, 409)
  }
  for (const id of ['0', '-1', '../me', '9223372036854775808']) assert.throws(() => getOrder(id))
  assert.equal(writes, 0)
  await assert.rejects(payOrder('1'), error => error.code === 'INSUFFICIENT_BALANCE')
  assert.equal(writes, 1)
})
