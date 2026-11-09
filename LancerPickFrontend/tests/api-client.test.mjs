import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { ApiError, apiRequest, clearCsrfToken } from '../src/shared/api/client.ts'
import { normalizeText, codePointLength } from '../src/shared/lib/text.ts'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

test('GET keeps same-origin cookies and propagates abort signal', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/demo/products')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.signal, controller.signal)
    return json({ items: [] })
  }
  assert.deepEqual(await apiRequest('/demo/products', { signal: controller.signal }), { items: [] })
})

test('mutations share CSRF fetch, send header and accept 204', async () => {
  let csrfCalls = 0
  let writes = 0
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') { csrfCalls++; return json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' }) }
    writes++
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test-token')
    assert.equal(options.body, '{"name":"예시"}')
    return new Response(null, { status: 204 })
  }
  assert.deepEqual(await Promise.all([apiRequest('/example', { method: 'POST', json: { name: '예시' } }), apiRequest('/example', { method: 'POST', json: { name: '예시' } })]), [undefined, undefined])
  assert.equal(csrfCalls, 1)
  assert.equal(writes, 2)
})

test('CSRF failure never replays mutation and next request gets fresh token', async () => {
  let csrfCalls = 0
  let writes = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') { csrfCalls++; return json({ token: 'new-token', headerName: 'X-CSRF-TOKEN' }) }
    writes++
    return json({ code: 'CSRF_INVALID', message: '갱신 필요', fieldErrors: [], traceId: 'trace-1' }, 403)
  }
  for (let i = 0; i < 2; i++) await assert.rejects(apiRequest('/example', { method: 'POST' }), (error) => error instanceof ApiError && error.code === 'CSRF_INVALID' && error.traceId === 'trace-1')
  assert.equal(writes, 2)
  assert.equal(csrfCalls, 2)
})

test('HTML proxy failures are safe errors and are not retried', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('<html>internal details</html>', { status: 502 }) }
  await assert.rejects(apiRequest('/example'), (error) => error instanceof ApiError && error.status === 502 && !error.message.includes('internal details'))
  assert.equal(calls, 1)
})

test('network error and abort remain distinguishable', async () => {
  globalThis.fetch = async () => { throw new TypeError('fetch failed') }
  await assert.rejects(apiRequest('/example'), (error) => error.code === 'NETWORK_ERROR')
  globalThis.fetch = async () => { throw new DOMException('cancelled', 'AbortError') }
  await assert.rejects(apiRequest('/example'), (error) => error.name === 'AbortError')
})

test('API credentials cannot be sent to an external URL', async () => {
  globalThis.fetch = async () => { assert.fail('fetch must not run') }
  await assert.rejects(apiRequest('//external.example'))
  await assert.rejects(apiRequest('https://external.example'))
})

test('normalization counts emoji as code points and preserves non-whitespace', () => {
  assert.equal(normalizeText('\u0085\u00A0  한글 😀\r\n 두 번째\r줄 \u3000'), '한글 😀\n 두 번째\n줄')
  assert.equal(normalizeText('\uFEFF가\u200B'), '\uFEFF가\u200B')
  assert.equal(codePointLength('한😀'), 2)
})


test('account guard prevents POST after CSRF wait when the account changes', async () => {
  let finishCsrf
  let started
  const ready = new Promise((resolve) => { started = resolve })
  let current = true
  let writes = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') {
      started()
      return await new Promise((resolve) => { finishCsrf = () => resolve(json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })) })
    }
    writes++
    return new Response(null, { status: 204 })
  }
  const request = apiRequest('/notifications/read-all', { method: 'POST', isCurrentAccount: () => current })
  const rejected = assert.rejects(request, (error) => error.name === 'AbortError')
  await ready
  current = false
  finishCsrf()
  await rejected
  assert.equal(writes, 0)
})

test('late unauthorized and CSRF errors cannot discard a newer CSRF token', async () => {
  for (const [status, code] of [[401, 'AUTHENTICATION_REQUIRED'], [403, 'CSRF_INVALID']]) {
    clearCsrfToken()
    let csrfCalls = 0
    let finishOld
    let started
    const sent = new Promise((resolve) => { started = resolve })
    globalThis.fetch = async (url) => {
      if (url === '/api/auth/csrf') return json({ token: `csrf-${++csrfCalls}`, headerName: 'X-CSRF-TOKEN' })
      if (url === '/api/old') {
        started()
        return await new Promise((resolve) => { finishOld = () => resolve(json({ code, message: 'old', fieldErrors: [], traceId: 'old' }, status)) })
      }
      return new Response(null, { status: 204 })
    }
    const old = apiRequest('/old', { method: 'POST' })
    const rejected = assert.rejects(old, (error) => error.status === status)
    await sent
    clearCsrfToken() // 계정 전환
    await apiRequest('/new', { method: 'POST' })
    finishOld()
    await rejected
    await apiRequest('/new', { method: 'POST' })
    assert.equal(csrfCalls, 2)
  }
})
