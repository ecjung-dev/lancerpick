import assert from 'node:assert/strict'
import { test } from 'node:test'
import { QueryClient } from '@tanstack/react-query'
import { clearWalletCache } from '../src/features/wallet/cache.ts'

test('wallet cache cleanup aborts in-flight requests and removes all account caches', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'account-a', 'balance'], { balance: 1000000 })
  client.setQueryData(['wallet', 'account-b', 'entries', { page: 0 }], { items: [] })
  client.setQueryData(['demo', 'products'], { items: ['keep'] })
  let aborted = false
  let started
  const ready = new Promise(resolve => { started = resolve })
  const pending = client.fetchQuery({
    queryKey: ['wallet', 'account-a', 'entries', { page: 1 }],
    queryFn: ({ signal }) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => { aborted = true; reject(new Error('aborted')) })
      started()
    }),
  }).catch(() => undefined)
  await ready
  await clearWalletCache(client)
  await pending
  assert.equal(aborted, true)
  assert.deepEqual(client.getQueryCache().findAll({ queryKey: ['wallet'] }), [])
  assert.deepEqual(client.getQueryData(['demo', 'products']), { items: ['keep'] })
  client.clear()
})
