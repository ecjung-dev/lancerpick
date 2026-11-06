import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parseEnv } from 'node:util'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const root = fileURLToPath(new URL('.', import.meta.url))
  const keysPath = fileURLToPath(new URL('./keys.env', import.meta.url))
  const personal = existsSync(keysPath) ? parseEnv(readFileSync(keysPath, 'utf8')) : {}
  const standard = loadEnv(mode, root, 'VITE_TOSS_CLIENT_KEY')
  // 개인 파일 전체나 서버 시크릿을 브라우저에 주입하지 않는다. 빈 셸 값은 검사 시 SDK를 끈다.
  const clientKey = process.env.VITE_TOSS_CLIENT_KEY ?? personal.VITE_TOSS_CLIENT_KEY ?? standard.VITE_TOSS_CLIENT_KEY ?? ''
  if (clientKey && clientKey !== 'CHANGE_ME' && !/^test_gck_[A-Za-z0-9_]+$/.test(clientKey)) {
    throw new Error('VITE_TOSS_CLIENT_KEY에는 토스 테스트 gck 클라이언트 키만 설정해 주세요.')
  }
  return {
    plugins: [react()],
    define: { 'import.meta.env.VITE_TOSS_CLIENT_KEY': JSON.stringify(clientKey === 'CHANGE_ME' ? '' : clientKey) },
    server: {
      port: 5173,
      strictPort: true,
      fs: {
        // 기본 민감 파일 차단을 유지하면서 사용자 지정 개인 파일도 원문 제공하지 않는다.
        deny: ['.env', '.env.*', '*.{crt,pem,key,p12,pfx,cer,der}', '.npmrc', '.yarnrc.yml', '**/.git/**', '**/keys.env'],
      },
      proxy: {
        '/ws': {
          target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
          ws: true,
          // Origin과 Host를 함께 유지하여 서버의 동일 출처 검사를 통과시킨다.
          changeOrigin: false,
        },
        '/api': {
          // 서버 포트가 사용 중이면 셸의 API_PROXY_TARGET으로만 변경한다. 브라우저에 노출하지 않는다.
          target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
