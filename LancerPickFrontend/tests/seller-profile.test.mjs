import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { getSellerSession, getMySellerProfile, saveSellerProfile } from '../src/features/seller/api.ts'
import { validateSellerProfile } from '../src/features/seller/validation.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

test('seller session and own profile use cookies and cancellation without client identity', async () => {
  const controller = new AbortController()
  const urls = []
  globalThis.fetch = async (url, options) => {
    urls.push(url)
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.signal, controller.signal)
    assert.equal(options.body, undefined)
    return json(url === '/api/me' ? { id: '9007199254740993', displayName: '판매자' } : { id: '9007199254740993' })
  }
  assert.equal((await getSellerSession(controller.signal)).id, '9007199254740993')
  assert.equal((await getMySellerProfile(controller.signal)).id, '9007199254740993')
  assert.deepEqual(urls, ['/api/me', '/api/me/seller-profile'])
})

test('only an explicitly missing profile becomes a registration form', async () => {
  globalThis.fetch = async () => json({ code: 'RESOURCE_NOT_FOUND', message: '없음', fieldErrors: [], traceId: 't' }, 404)
  assert.equal(await getMySellerProfile(), null)
  for (const status of [401, 403, 500]) {
    globalThis.fetch = async () => json({ code: 'ERROR', message: '실패', fieldErrors: [], traceId: 't' }, status)
    await assert.rejects(getMySellerProfile(), error => error.status === status)
  }
  globalThis.fetch = async () => { throw new TypeError('network failed') }
  await assert.rejects(getMySellerProfile(), error => error.code === 'NETWORK_ERROR')
})

test('save sends the full PUT with CSRF and nullable URL without replaying failures', async () => {
  let writes = 0
  const input = { introduction: '소개', specialty: '디자인', portfolioUrl: null }
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'test-csrf', headerName: 'X-CSRF-TOKEN' })
    writes++
    assert.equal(url, '/api/me/seller-profile')
    assert.equal(options.method, 'PUT')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test-csrf')
    assert.deepEqual(JSON.parse(options.body), input)
    return json({ code: 'VALIDATION_FAILED', message: '검사 실패', fieldErrors: [], traceId: 't' }, 400)
  }
  await assert.rejects(saveSellerProfile(input), error => error.status === 400)
  assert.equal(writes, 1)
})

test('normalization, required values and emoji limits match the backend contract', () => {
  const valid = validateSellerProfile({ introduction: '\u00a0 소개\r\n두 줄 ', specialty: '😀'.repeat(100), portfolioUrl: ' ' })
  assert.equal(valid.valid, true)
  assert.equal(valid.input.introduction, '소개\n두 줄')
  assert.equal(valid.input.portfolioUrl, null)
  assert.equal(validateSellerProfile({ introduction: '😀'.repeat(2000), specialty: '분야', portfolioUrl: '' }).valid, true)
  assert.equal(validateSellerProfile({ introduction: '😀'.repeat(2001), specialty: '분야', portfolioUrl: '' }).valid, false)
  assert.equal(validateSellerProfile({ introduction: '소개', specialty: '😀'.repeat(101), portfolioUrl: '' }).valid, false)
  assert.equal(validateSellerProfile({ introduction: ' ', specialty: '\u3000', portfolioUrl: '' }).valid, false)
})

test('portfolio validation rejects unsafe schemes, credentials and overlong URLs', () => {
  for (const url of ['http://example.com', 'javascript:alert(1)', 'https://user:pass@example.com', 'https://@example.com', 'https:///work', 'https://example.com/a b']) {
    assert.equal(validateSellerProfile({ introduction: '소개', specialty: '분야', portfolioUrl: url }).valid, false)
  }
  const prefix = 'https://example.com/'
  const boundary = prefix + 'a'.repeat(2048 - prefix.length)
  assert.equal(validateSellerProfile({ introduction: '소개', specialty: '분야', portfolioUrl: boundary }).valid, true)
  assert.equal(validateSellerProfile({ introduction: '소개', specialty: '분야', portfolioUrl: boundary + 'a' }).valid, false)
})
