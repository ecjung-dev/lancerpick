import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-back-link',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { BackLink } = await server.ssrLoadModule('/src/shared/components/BackLink.tsx')
after(async () => { await server.close() })

const render = props => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(BackLink, props)))

test('back link stays a plain link whose accessible name is only the destination', () => {
  const html = render({ to: '/orders?page=2', children: '거래 내역' })
  assert.match(html, /^<a [^>]*href="\/orders\?page=2"/)
  assert.match(html, /<span aria-hidden="true">←<\/span>거래 내역<\/a>$/)
  assert.doesNotMatch(html, /role="button"|aria-label=/)
})

test('back link keeps extra classes alongside the shared style', () => {
  const html = render({ to: '/', className: 'extra', children: '상품 목록' })
  assert.match(html, /class="[^"]*\bextra"/)
})
