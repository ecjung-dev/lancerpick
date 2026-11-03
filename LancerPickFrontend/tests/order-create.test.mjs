import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { createOrder, prepareOrderInput } from '../src/features/orders/create.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })
const key = 'b89eb754-8052-489f-ac52-e5d4a367d12f'
const input = { productId: '9007199254740993', expectedProductVersion: 2, requirements: '  작업 요청  ', requestKey: key }

test('a new order and an explicit identical retry keep the original key and normalized body', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    calls.push({ url, method: options.method, credentials: options.credentials, csrf: options.headers.get('X-CSRF-TOKEN'), body: JSON.parse(options.body) })
    return Response.json({ id: '42', status: 'REQUESTED' }, { status: calls.length === 1 ? 201 : 200 })
  }
  assert.equal((await createOrder(input)).id, '42')
  assert.equal((await createOrder(input)).id, '42')
  assert.equal(calls.length, 2)
  assert.deepEqual(calls[0], { url: '/api/orders', method: 'POST', credentials: 'same-origin', csrf: 'csrf', body: { ...input, requirements: '작업 요청' } })
  assert.deepEqual(calls[0], calls[1])
})

test('unknown outcome never retries automatically and invalid values do not reach the API', async () => {
  let calls = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    calls++
    throw new Error('connection lost')
  }
  await assert.rejects(createOrder(input))
  assert.equal(calls, 1)
  for (const patch of [{ productId: '9223372036854775808' }, { expectedProductVersion: 1.5 }, { requirements: '   ' }, { requirements: '😃'.repeat(5001) }, { requestKey: 'not-uuid' }]) {
    assert.throws(() => prepareOrderInput({ ...input, ...patch }))
  }
  assert.equal(calls, 1)
})
