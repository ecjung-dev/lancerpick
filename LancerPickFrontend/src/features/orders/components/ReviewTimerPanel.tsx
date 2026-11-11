import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useReviewTimer } from '../queries'
import { useCurrentMember } from '../../auth/queries'
import { startReviewTimer } from '../api'
import { reviewTimerView } from '../reviewTimer'
import { orderCommandFailureMessage } from '../failure'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'

interface ReviewTimerPanelProps {
  orderId: string
  sellerId: string
}

export function ReviewTimerPanel({ orderId, sellerId }: ReviewTimerPanelProps) {
  const client = useQueryClient()
  const query = useReviewTimer(orderId)
  // 안내 문구에만 쓴다. 시작 버튼은 서버의 canStart로만 표시한다.
  const isSeller = useCurrentMember().data?.id === sellerId
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const lock = useRef(false)
  const lastStatus = useRef<string | undefined>(undefined)
  const status = query.data?.status

  // 주기 조회로 상태가 바뀐 것(자동 확정 완료·구매자 응답 등)을 발견하면 주문 상세·대금도 서버 기준으로 다시 확인한다.
  useEffect(() => {
    if (status === undefined) return
    const previous = lastStatus.current
    lastStatus.current = status
    if (previous !== undefined && previous !== status)
      void client.invalidateQueries({ queryKey: ['wallet'] })
  }, [status, client])

  async function start() {
    if (lock.current || query.isFetching || !query.data?.canStart) return
    lock.current = true
    setBusy(true)
    setFailure(null)
    try {
      await startReviewTimer(orderId)
    } catch (error) {
      setFailure(orderCommandFailureMessage(error))
    } finally {
      // 성공·실패 모두 서버 상태로 다시 확인한다. 시작 요청은 자동 재전송하지 않는다.
      await client.invalidateQueries({ queryKey: ['wallet'] })
      lock.current = false
      setBusy(false)
    }
  }

  if (query.isError) {
    return (
      <section>
        <h2>자동 구매 확정</h2>
        <AsyncState
          title="자동 구매 확정 상태를 확인하지 못했어요"
          description={query.error.message}
          error={query.error}
        />
        <Button
          variant="secondary"
          disabled={query.isFetching}
          onClick={() => {
            void query.refetch()
          }}
        >
          다시 조회
        </Button>
      </section>
    )
  }

  if (!query.data) return null
  const view = reviewTimerView(query.data, isSeller)
  if (view === null) return null

  return (
    <section aria-label="자동 구매 확정">
      <h2>자동 구매 확정</h2>
      <p>{view.title}</p>
      <p>{view.detail}</p>
      {query.data.canStart && (
        <Button disabled={busy || query.isFetching} onClick={() => void start()}>
          {busy ? '시작하는 중…' : '7일 자동 구매 확정 타이머 시작'}
        </Button>
      )}
      {failure && <p role="alert">{failure}</p>}
    </section>
  )
}
