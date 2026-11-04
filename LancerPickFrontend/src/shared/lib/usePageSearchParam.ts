import { useSearchParams } from 'react-router'

/** 목록 위치를 URL에 보관해 새로고침·뒤로가기에도 같은 페이지를 조회한다. */
export function usePageSearchParam() {
  const [params, setParams] = useSearchParams()
  const requestedPage = Number(params.get('page') ?? 0)
  const page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 2147483647
    ? requestedPage : 0

  function setPage(nextPage: number) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('page', String(nextPage))
      return next
    })
  }

  return [page, setPage] as const
}
