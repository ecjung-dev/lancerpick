import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../../shared/api/client'
import { createWalletTopup, getWalletTopup } from './api'
import {
  getCheckoutStorage,
  matchesPendingTopup,
  savePendingCheckout,
  type PendingCheckout,
} from './checkoutFlow'
import {
  isTossTestClientKey,
  mountTossCheckout,
  type CheckoutWidgets,
} from './tossCheckout'

/**
 * 테스트 충전의 결제 위젯 생명주기와 결제 요청 흐름. 위젯 준비·정리와 결제 요청은 같은 alive(언마운트)·busy(중복 실행)·
 * runtime(현재 위젯) 참조를 공유하므로 한 훅에 둔다. 이전 위젯의 늦은 이벤트는 runtime 동일성으로 무시하고,
 * 신청을 만든 뒤에는 금액을 바꾸지 않고 상태 조회로 보낸다. 변경 요청은 자동 재시도하지 않는다.
 */
export function useTopupCheckout({
  preview,
  pending,
  amount,
  clientKey,
}: {
  preview: boolean
  pending: PendingCheckout | null
  amount: number | null
  clientKey: string
}) {
  const [step, setStep] = useState<'amount' | 'checkout'>('amount')
  const [loading, setLoading] = useState(false)
  // V2 웹 SDK는 초기 동의 상태 조회 API를 제공하지 않는다.
  // 변경 이벤트 전에는 상태를 단정하지 않고 requestPayment의 약관 검증에 맡긴다.
  const [agreed, setAgreed] = useState<boolean | null>(null)
  const [widgetsReady, setWidgetsReady] = useState(false)
  const [error, setError] = useState<unknown>()
  const [submittedId, setSubmittedId] = useState<string>()
  const runtime = useRef<CheckoutWidgets | null>(null)
  const alive = useRef(true)
  const busy = useRef(false)
  const navigate = useNavigate()

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      void runtime.current?.destroy().catch(() => undefined)
      runtime.current = null
    }
  }, [])

  async function prepare() {
    if (!amount || busy.current) return
    if (preview) {
      setStep('checkout')
      return
    }
    busy.current = true
    setLoading(true)
    setError(undefined)
    setAgreed(null)
    setWidgetsReady(false)
    setStep('checkout')
    try {
      const storage = getCheckoutStorage()
      if (!storage)
        throw new Error('브라우저의 세션 저장소를 사용할 수 없어요.')
      // 신청 저장 전에 저장소 사용 가능 여부를 확인한다. 토큰/시크릿은 저장하지 않는다.
      storage.setItem('lancerpick.wallet.storage-check', '1')
      storage.removeItem('lancerpick.wallet.storage-check')
      if (runtime.current) {
        await runtime.current.destroy()
        runtime.current = null
      }
      if (!alive.current) return
      const mounted = await mountTossCheckout(clientKey, amount)
      if (!alive.current) {
        await mounted.destroy()
        return
      }
      runtime.current = mounted
      setWidgetsReady(true)
      mounted.agreement.on(
        'agreementStatusChange',
        ({ agreedRequiredTerms }) => {
          if (!alive.current || runtime.current !== mounted) return
          setAgreed(agreedRequiredTerms)
          if (agreedRequiredTerms) setError(undefined)
        },
      )
      mounted.methods.on('paymentMethodSelect', () => {
        if (alive.current && runtime.current === mounted) setError(undefined)
      })
    } catch (failure) {
      if (alive.current)
        setError(
          failure instanceof Error && !isTossTestClientKey(clientKey)
            ? failure
            : new Error(
                '결제 수단을 불러오지 못했어요. 테스트 키와 토스 결제 UI 설정을 확인해 주세요.',
              ),
        )
    } finally {
      busy.current = false
      if (alive.current) setLoading(false)
    }
  }

  async function changeAmount() {
    // 신청을 만든 뒤에는 금액을 바꾸지 않고 해당 신청 상태부터 확인한다.
    if (busy.current || pending || submittedId) return
    busy.current = true
    setLoading(true)
    setError(undefined)
    setWidgetsReady(false)
    setAgreed(null)
    try {
      if (runtime.current) {
        await runtime.current.destroy()
        runtime.current = null
      }
      if (alive.current) setStep('amount')
    } catch {
      if (alive.current)
        setError(
          new Error(
            '결제 화면을 정리하지 못했어요. 충전 금액 변경을 다시 눌러 주세요.',
          ),
        )
    } finally {
      busy.current = false
      if (alive.current) setLoading(false)
    }
  }

  async function requestPayment() {
    const mounted = runtime.current
    if (
      preview ||
      !widgetsReady ||
      !mounted ||
      !amount ||
      agreed === false ||
      busy.current ||
      submittedId
    )
      return
    busy.current = true
    setLoading(true)
    setError(undefined)
    try {
      if (
        (await mounted.methods.getSelectedPaymentMethod()).method !== 'CARD'
      ) {
        throw new Error(
          '이번 테스트에서는 카드로 처리되는 결제 수단을 선택해 주세요.',
        )
      }
      const topup = pending
        ? await getWalletTopup(pending.id)
        : await createWalletTopup(amount)
      if (!alive.current) return
      setSubmittedId(topup.id)
      if (topup.status !== 'READY') {
        navigate(`/mypage/wallet/topup/${topup.id}/status`)
        return
      }
      if (
        !topup.isTest ||
        topup.currency !== 'KRW' ||
        topup.amount !== amount ||
        (pending && !matchesPendingTopup(topup, pending))
      ) {
        throw new Error(
          '저장된 충전 조건을 확인해 주세요. 새 결제를 시작하지 않고 상태를 조회해 주세요.',
        )
      }
      const nonce = pending?.nonce ?? crypto.randomUUID()
      savePendingCheckout({
        id: topup.id,
        orderId: topup.tossOrderId,
        amount: topup.amount,
        nonce,
        createdAt: Date.now(),
      })
      await mounted.widgets.setAmount({ currency: 'KRW', value: topup.amount })
      const base = `${window.location.origin}/mypage/wallet/topup/${encodeURIComponent(topup.id)}`
      await mounted.widgets.requestPayment({
        orderId: topup.tossOrderId,
        orderName: '랜서픽 테스트 적립금 충전',
        successUrl: `${base}/success?nonce=${encodeURIComponent(nonce)}`,
        failUrl: `${base}/fail?nonce=${encodeURIComponent(nonce)}`,
        windowTarget: 'self',
      })
    } catch (failure) {
      if (alive.current)
        setError(
          failure instanceof ApiError ||
            (failure instanceof Error &&
              failure.message.startsWith('이번 테스트'))
            ? failure
            : new Error(
                '결제 절차가 중단됐어요. 신청이 만들어졌다면 먼저 충전 상태를 확인해 주세요.',
              ),
        )
    } finally {
      busy.current = false
      if (alive.current) setLoading(false)
    }
  }

  return {
    step,
    loading,
    agreed,
    widgetsReady,
    error,
    submittedId,
    prepare,
    changeAmount,
    requestPayment,
  }
}
