import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { getWalletSession, getWallet, getWalletEntries } from '../src/features/wallet/api.ts'
import { ApiError } from '../src/shared/api/client.ts'
const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })
test('wallet session and reads use current cookie without sending member identifiers', async () => {
  const urls = []
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    urls.push(url)
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.signal, controller.signal)
    assert.equal(options.body, undefined)
    return new Response(JSON.stringify(url === '/api/me' ? { id: '9007199254740993' } : {}), { headers: { 'Content-Type': 'application/json' } })
  }
  assert.equal((await getWalletSession(controller.signal)).id, '9007199254740993')
  await getWallet(controller.signal); await getWalletEntries(0, 20, controller.signal)
  assert.deepEqual(urls, ['/api/me', '/api/me/wallet', '/api/me/wallet/entries?page=0&size=20'])
})
test('expired wallet session propagates 401 without falling back to preview data', async () => {
  let calls = 0
  globalThis.fetch = async () => {
    calls++
    return new Response(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED', message: 'login', fieldErrors: [], traceId: 'test' }), { status: 401, headers: { 'Content-Type': 'application/json' } })
  }
  await assert.rejects(getWalletSession(), error => error instanceof ApiError && error.status === 401)
  assert.equal(calls, 1)
})
