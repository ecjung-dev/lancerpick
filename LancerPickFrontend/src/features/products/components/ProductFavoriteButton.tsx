import { loginPath } from '../../auth/session'
import { useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { Icon } from '../../../shared/components/Icon'
import styles from './ProductFavoriteButton.module.css'
import { Button } from '../../../shared/components/Button'
import { getMemberSession } from '../../auth/api'
import { getFavorite, setFavorite } from '../favoritesApi'
import { useMyProductsSession } from '../queries'

export function ProductFavoriteButton({ productId }: { productId: string }) {
  const session = useMyProductsSession()
  const location = useLocation()
  if (isUnauthorized(session.error)) {
    return <Link className={styles.heart} to={loginPath(location.pathname + location.search)} aria-label="로그인하고 관심 상품 등록" title="로그인하고 관심 상품 등록"><Icon name="heart" size={21} /></Link>
  }
  if (session.isError) return <Button className={styles.heart} variant="secondary" aria-label="로그인 상태 다시 확인" title="로그인 상태 다시 확인" onClick={() => { void session.refetch() }}><Icon name="heart" size={21} /></Button>
  if (!session.data || session.isFetching) return <Button className={styles.heart} variant="secondary" disabled aria-label="로그인 상태를 확인하고 있어요" title="로그인 상태 확인 중"><Icon name="heart" size={21} /></Button>
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

  if (status.isError) return <Button className={styles.heart} variant="secondary" aria-label="관심 상태 다시 확인" title="관심 상태 다시 확인" onClick={() => { void status.refetch() }}><Icon name="heart" size={21} /></Button>
  if (!status.data) return <Button className={styles.heart} variant="secondary" disabled aria-label="관심 상태를 확인하고 있어요" title="관심 상태 확인 중"><Icon name="heart" size={21} /></Button>
  const { favorited } = status.data
  return <>
    <Button className={styles.heart} variant="secondary" aria-label={change.isPending ? '관심 상품 처리 중' : favorited ? '관심 상품 해제' : '관심 상품 등록'} title={favorited ? '관심 상품 해제' : '관심 상품 등록'} aria-pressed={favorited} disabled={change.isPending || status.isFetching} onClick={() => {
      if (sending.current) return
      sending.current = true
      change.mutate(!favorited)
    }}>
      <Icon name="heart" size={21} />
    </Button>
    {change.isError && <p className={styles.error} role="alert">요청 결과를 확인하지 못했어요. 현재 관심 상태를 확인해 주세요.</p>}
  </>
}
