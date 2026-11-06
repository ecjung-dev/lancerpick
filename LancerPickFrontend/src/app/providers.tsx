import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { PropsWithChildren } from 'react'
import { recheckAfterUnauthorized } from '../features/auth/session'

const queryClient: QueryClient = new QueryClient({
  // 조회 401: 다른 곳에서 세션이 끝났을 수 있으니 로그인 상태를 다시 확인해 헤더·개인 캐시를 정리한다.
  queryCache: new QueryCache({ onError: (error) => { void recheckAfterUnauthorized(queryClient, error) } }),
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000 },
    mutations: { retry: false },
  },
})

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  )
}
