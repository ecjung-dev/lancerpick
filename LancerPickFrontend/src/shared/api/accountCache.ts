import type { Query, QueryClient } from '@tanstack/react-query'

/** 인증 상태는 호출자가 갱신하고, 이전 계정의 조회는 취소 후 제거한다. */
export async function clearAccountQueries(queryClient: QueryClient) {
  const accountQueries = {
    predicate: (query: Query) => query.queryKey[0] !== 'auth',
  }
  await queryClient.cancelQueries(accountQueries)
  queryClient.removeQueries(accountQueries)
}
