import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import {
  apiPdfRequest,
  ApiError,
  clearCsrfToken,
} from '../src/shared/api/client.ts'
import {
  getContract,
  generateContract,
  downloadContract,
  verifyContractPdf,
  prepareContract,
  signContract,
} from '../src/features/contracts/api.ts'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  clearCsrfToken()
})
const server = await createServer({
  configFile: false,
  cacheDir: 'node_modules/.vite-test-contracts',
  server: { middlewareMode: true, ws: false, watch: null },
  appType: 'custom',
})
const { ContractPanel } = await server.ssrLoadModule(
  '/src/features/contracts/components/ContractPanel.tsx',
)
after(async () => {
  await server.close()
})

test('private PDF sends credentials, bypasses cache and pins the exact version', async () => {
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/orders/9007199254740993/contract/file?version=2')
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    assert.match(init.headers.get('Accept'), /application\/pdf/)
    return new Response('%PDF fixture', {
      headers: { 'Content-Type': 'application/pdf' },
    })
  }
  const file = await downloadContract('9007199254740993', 2)
  assert.equal(await file.text(), '%PDF fixture')
})
test('PDF errors stay structured and an HTML success cannot become a download', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: 'AUTHENTICATION_REQUIRED',
        message: '로그인이 필요합니다.',
        fieldErrors: [],
        traceId: 'test',
      }),
      { status: 401 },
    )
  await assert.rejects(
    apiPdfRequest('/orders/1/contract/file'),
    (e) => e instanceof ApiError && e.status === 401,
  )
  globalThis.fetch = async () =>
    new Response('<html>proxy</html>', {
      headers: { 'Content-Type': 'text/html' },
    })
  await assert.rejects(
    apiPdfRequest('/orders/1/contract/file'),
    (e) => e.code === 'INVALID_RESPONSE',
  )
})
test('generation sends only version with CSRF and never retries an uncertain mutation', async () => {
  const calls = []
  globalThis.fetch = async (url, init) => {
    calls.push(url)
    if (url === '/api/auth/csrf')
      return Response.json({
        token: 'csrf-fixture',
        headerName: 'X-CSRF-TOKEN',
      })
    assert.deepEqual(JSON.parse(init.body), { version: 3 })
    assert.equal(init.headers.get('X-CSRF-TOKEN'), 'csrf-fixture')
    throw new TypeError('network')
  }
  await assert.rejects(
    generateContract('11', 3),
    (e) => e.code === 'NETWORK_ERROR',
  )
  assert.equal(calls.filter((url) => url.endsWith('/generate')).length, 1)
})
test('bad IDs and fractional or overflowing versions are rejected before fetch', async () => {
  globalThis.fetch = async () => {
    throw new Error('must not fetch')
  }
  for (const id of ['0', '../1', '9223372036854775808'])
    assert.throws(() => getContract(id))
  for (const version of [0, 1.5, 2147483648])
    assert.throws(() => downloadContract('1', version))
})
test('download hash must equal the fixed server metadata', async () => {
  const file = new Blob(['%PDF fixture'])
  const hash = Buffer.from(
    await crypto.subtle.digest('SHA-256', await file.arrayBuffer()),
  ).toString('hex')
  await verifyContractPdf(file, hash)
  await assert.rejects(verifyContractPdf(new Blob(['tampered']), hash), /변경/)
  await assert.rejects(verifyContractPdf(file, ''), /해시/)
})

function render(status, memberId = '7', fileHash = null, denied = false) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['wallet', 'contract-member'], { id: memberId })
  client.setQueryData(['wallet', 'contract', memberId, '11', 'current'], {
    id: '3',
    version: 1,
    status,
    fileHash,
    canonicalContent: JSON.stringify({
      standardBody: '<script>unsafe</script>',
      total: 100001,
    }),
    signatures: [],
    specialTerms: '',
  })
  if (denied) client.getQueryCache().find({ queryKey: ['wallet', 'contract', memberId, '11', 'current'] }).setState({ status: 'error', error: new Error('접근할 수 없습니다.') })
  try {
    return renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(ContractPanel, {
          orderId: '11',
          sellerId: '7',
          orderStatus: 'AWAITING_CONTRACT',
        }),
      ),
    )
  } finally {
    client.clear()
  }
}
test('seller sees draft generation and retry while buyer cannot generate', () => {
  assert.match(render('DRAFT'), /PDF 생성<\/button>/)
  assert.match(render('FAILED'), /생성 재시도/)
  assert.doesNotMatch(render('DRAFT', '8'), /PDF 생성<\/button>/)
  assert.match(render('GENERATING'), /5초마다/)
})
test('ready file escapes contract text and keeps signing disabled before PDF review', () => {
  const html = render('READY', '8', 'a'.repeat(64))
  assert.match(html, /PDF 다운로드/)
  assert.match(html, /&lt;script&gt;/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /<button disabled="">현재 계약에 서명<\/button>/)
})

test('draft and signing commands send the expected version and fixed document hashes with consent', async () => {
  const writes=[]
  globalThis.fetch=async (url,init) => {
    if(url==='/api/auth/csrf') return Response.json({ token:'csrf-test',headerName:'X-CSRF-TOKEN' })
    assert.equal(init.headers.get('X-CSRF-TOKEN'),'csrf-test')
    writes.push([url,JSON.parse(init.body)])
    return Response.json({ status:'READY' })
  }
  await prepareContract('11',2,'특약')
  await signContract('11',{ version:3,contentHash:'a'.repeat(64),fileHash:'b'.repeat(64) })
  assert.deepEqual(writes,[['/api/orders/11/contract/draft',{expectedVersion:2,specialTerms:'특약'}],['/api/orders/11/contract/sign',{version:3,contentHash:'a'.repeat(64),fileHash:'b'.repeat(64),agreed:true}]])
})

test('a failed access recheck hides previously cached contract content and download', () => {
  const html = render('READY', '8', 'a'.repeat(64), true)
  assert.doesNotMatch(html, /PDF 다운로드|unsafe/)
  assert.match(html, /접근할 수 없습니다/)
})
