import type { QueryClient } from '@tanstack/react-query'

/** 로그아웃·계정 전환 시 호출한다. 진행 중인 이전 계정 조회도 먼저 중단한다. */
export async function clearWalletCache(queryClient: QueryClient) {
  await queryClient.cancelQueries({ queryKey: ['wallet'] })
  queryClient.removeQueries({ queryKey: ['wallet'] })
}
