import { useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { getMemberSession } from '../../auth/api'
import { getFavorite, setFavorite } from '../favoritesApi'
import { useMyProductsSession } from '../queries'

export function ProductFavoriteButton({ productId }: { productId: string }) {
  const session = useMyProductsSession()
  const location = useLocation()
  if (session.error instanceof ApiError && session.error.status === 401) {
    return <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}>로그인하고 관심 상품 등록</Link>
  }
  if (session.isError) return <Button variant="secondary" onClick={() => { void session.refetch() }}>로그인 상태 다시 확인</Button>
  if (!session.data || session.isFetching) return <p role="status">로그인 상태를 확인하고 있어요.</p>
  return <FavoriteAction key={`${session.data.id}:${productId}`} memberId={session.data.id} productId={productId} />
}

function FavoriteAction({ memberId, productId }: { memberId: string; productId: string }) {
  const client = useQueryClient()
  const sending = useRef(false)
  const status = useQuery({
    queryKey: ['favorites', memberId, 'product', productId],
    queryFn: ({ signal }) => getFavorite(productId, signal),
    retry: false,
    gcTime: 0,
  })
  const change = useMutation({
    retry: false,
    mutationFn: async (favorited: boolean) => {
      if ((await getMemberSession()).id !== memberId) {
        throw new Error('로그인 계정이 바뀌었어요.')
      }
      await setFavorite(productId, favorited)
    },
    onSettled: async () => {
      try {
        await client.invalidateQueries({ queryKey: ['favorites', memberId] })
      } finally {
        sending.current = false
      }
    },
  })

  if (status.isError) return <Button variant="secondary" onClick={() => { void status.refetch() }}>관심 상태 다시 확인</Button>
  if (!status.data) return <p role="status">관심 상태를 확인하고 있어요.</p>
  const { favorited } = status.data
  return <>
    <Button variant="secondary" aria-pressed={favorited} disabled={change.isPending || status.isFetching} onClick={() => {
      if (sending.current) return
      sending.current = true
      change.mutate(!favorited)
    }}>
      {change.isPending ? '처리 중…' : favorited ? '관심 상품 해제' : '관심 상품 등록'}
    </Button>
    {change.isError && <p role="alert">요청 결과를 확인하지 못했어요. 현재 관심 상태를 확인해 주세요.</p>}
  </>
}
