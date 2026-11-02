import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { formatWon } from '../../../shared/lib/format'
import { confirmWalletTopup, getWalletTopup, reconcileWalletTopup } from '../api'
import { confirmCheckoutOnce, forgetPendingCheckout, isTopupId, matchesPendingTopup, readPendingCheckout, readTopupCallback } from '../checkoutFlow'
import type { WalletTopup } from '../types'
import styles from './WalletTopupPage.module.css'

type Outcome = 'success' | 'fail' | 'status'

export function WalletTopupResultPage({ outcome }: { outcome: Outcome }) {
  const { id = '' } = useParams()
  return <ResultState key={`${id}:${outcome}`} id={id} outcome={outcome} />
}

function ResultState({ id, outcome }: { id: string; outcome: Outcome }) {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [flow] = useState(() => {
    const pending = readPendingCheckout()
    return { pending, confirmation: outcome === 'success' ? readTopupCallback(new URLSearchParams(location.search), pending, id) : null }
  })
  const [topup, setTopup] = useState<WalletTopup>()
  const [error, setError] = useState<unknown>()
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // paymentKey·제공자 오류 문구를 주소·뒤로가기 이력에 남기지 않는다. 키는 메모리에서만 사용한다.
    if (location.search) navigate(location.pathname, { replace: true })
  }, [location.search, location.pathname, navigate])

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    async function load() {
      try {
        if (!isTopupId(id)) throw new Error('충전 신청 주소를 확인해 주세요.')
        let current = await getWalletTopup(id, controller.signal)
        if (!active) return
        if (flow.confirmation && flow.pending && current.status === 'READY' && matchesPendingTopup(current, flow.pending)) {
          await confirmCheckoutOnce(id, () => confirmWalletTopup(id, flow.confirmation!))
          current = await getWalletTopup(id, controller.signal)
        }
        if (!active) return
        setTopup(current)
        if (current.status === 'COMPLETED' || current.status === 'FAILED') forgetPendingCheckout(id)
        if (current.status === 'COMPLETED') void queryClient.invalidateQueries({ queryKey: ['wallet'] })
      } catch (failure) {
        if (active) setError(failure)
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false; controller.abort() }
  }, [id, flow, queryClient])

  async function refresh(reconcile: boolean) {
    if (loading || !isTopupId(id)) return
    setLoading(true); setError(undefined)
    try {
      const current = reconcile ? await reconcileWalletTopup(id) : await getWalletTopup(id)
      setTopup(current)
      if (current.status === 'COMPLETED' || current.status === 'FAILED') forgetPendingCheckout(id)
      if (current.status === 'COMPLETED') void queryClient.invalidateQueries({ queryKey: ['wallet'] })
    } catch (failure) { setError(failure) } finally { setLoading(false) }
  }

  const pending = topup?.status === 'PROCESSING' || topup?.status === 'UNKNOWN'
  const complete = topup?.status === 'COMPLETED'
  const title = complete ? '충전이 완료됐어요' : pending ? '충전 결과를 확인하고 있어요' : topup?.status === 'FAILED' ? '충전이 완료되지 않았어요' : '충전 신청을 확인해 주세요'
  return <section className={`${styles.page} ${styles.result}`}>
    <p className={styles.eyebrow}>MY WALLET</p>
    {loading && !topup ? <AsyncState title="서버에서 충전 상태를 확인하고 있어요" /> : <>
      <span className={styles.resultMark} aria-hidden="true">{complete ? '✓' : '· · ·'}</span><h1>{title}</h1>
      {topup && <><p className={styles.subtitle}>{complete ? `${formatWon(topup.amount)}이 테스트 적립금에 반영됐습니다.` : pending ? '성공·실패가 아직 확정되지 않았습니다. 새 결제보다 결과 재확인을 먼저 진행해 주세요.' : '신청 저장과 결제창 이동만으로 적립금이 지급되지는 않습니다.'}</p>
        <dl className={styles.resultDetails}><div><dt>충전 신청</dt><dd>#{topup.id}</dd></div><div><dt>신청 금액</dt><dd>{formatWon(topup.amount)}</dd></div><div><dt>처리 상태</dt><dd>{({ READY: '승인 전', PROCESSING: '처리 중', UNKNOWN: '결과 확인 필요', COMPLETED: '충전 완료', FAILED: '충전 미완료' })[topup.status]}</dd></div></dl>
      </>}
      {outcome === 'fail' && !complete && <p className={styles.help}>결제창 절차가 중단됐습니다. 이 안내만으로 서버의 결제 취소·환불을 확정하지 않습니다.</p>}
      {outcome === 'success' && !flow.confirmation && topup?.status === 'READY' && <p className={styles.help}>유효한 결제 복귀 정보를 찾지 못해 자동 승인하지 않았습니다.</p>}
      {error !== undefined && <AsyncState title={error instanceof ApiError && error.status === 401 ? '다시 로그인해 주세요' : '충전 상태를 확인하지 못했어요'} error={error} description={error instanceof Error ? error.message : '잠시 후 상태를 다시 조회해 주세요.'} />}
      <div className={styles.resultActions}>
        {isTopupId(id) && !complete && <Button disabled={loading} onClick={() => { void refresh(Boolean(pending)) }}>{loading ? '확인 중…' : pending ? '결제 결과 재확인' : '신청 상태 새로고침'}</Button>}
        <Link to="/mypage/wallet" className={styles.actionLink}>적립금 내역으로</Link>
        {error instanceof ApiError && error.status === 401 && <Link to="/login">로그인</Link>}
        {topup && !pending && <Link to="/mypage/wallet/topup" onClick={() => { if (topup.status === 'READY') forgetPendingCheckout(id) }}>충전 화면으로</Link>}
      </div>
    </>}
    <p className={styles.resultFootnote}>테스트 결제 전용 · 실제 현금 청구 및 출금 없음</p>
  </section>
}
