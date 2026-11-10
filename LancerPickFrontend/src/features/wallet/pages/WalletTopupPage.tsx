import { useTopupAccess } from '../queries'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { formatWon } from '../../../shared/lib/format'
import { createWalletTopup, getWalletTopup } from '../api'
import {
  getCheckoutStorage,
  matchesPendingTopup,
  parseTopupAmount,
  readPendingCheckout,
  savePendingCheckout,
} from '../checkoutFlow'
import {
  isTossTestClientKey,
  mountTossCheckout,
  type CheckoutWidgets,
} from '../tossCheckout'
import styles from './WalletTopupPage.module.css'

export function WalletTopupPage() {
  // 인증 공통 연결 전에는 본인 지갑 접근으로 확인한다. 인증 담당자의 상태 훅으로 교체 가능하다.
  // 잔액을 화면/접근 캐시에 보관하지 않는다. 권한은 모든 업무 API에서 서버가 재검증한다.
  const access = useTopupAccess()
  if (access.isPending)
    return (
      <TopupFrame>
        <AsyncState title="로그인 상태를 확인하고 있어요" />
      </TopupFrame>
    )
  if (access.isError)
    return (
      <TopupFrame>
        <AsyncState
          title={
            isUnauthorized(access.error)
              ? '로그인이 필요해요'
              : '충전 화면을 준비하지 못했어요'
          }
          error={access.error}
          onRetry={() => {
            void access.refetch()
          }}
        />
        <div className={styles.links}>
          <Link to="/login">로그인</Link>
          <Link to="/mypage/wallet/topup/preview">화면 미리보기 →</Link>
        </div>
      </TopupFrame>
    )
  return (
    <TopupFrame>
      <CheckoutForm />
    </TopupFrame>
  )
}

export function WalletTopupPreviewPage() {
  return (
    <TopupFrame preview>
      <CheckoutForm preview />
    </TopupFrame>
  )
}

function TopupFrame({
  children,
  preview = false,
}: {
  children: ReactNode
  preview?: boolean
}) {
  return (
    <section className={styles.page}>
      <Link
        className={styles.back}
        to={preview ? '/mypage/wallet/preview' : '/mypage/wallet'}
      >
        ← 적립금 · 이용 내역
      </Link>
      <p className={styles.eyebrow}>MY WALLET</p>
      <h1>테스트 적립금 충전</h1>
      <p className={styles.subtitle}>
        서비스를 이용할 준비, 원하는 금액만큼 채워보세요.
      </p>
      {preview && (
        <div className={styles.notice} role="note">
          <strong>화면 미리보기</strong> 예시 화면입니다. API 요청이나 토스
          결제를 실행하지 않습니다.
        </div>
      )}
      {children}
    </section>
  )
}

function CheckoutForm({ preview = false }: { preview?: boolean }) {
  const [pending] = useState(() => (preview ? null : readPendingCheckout()))
  const [input, setInput] = useState(String(pending?.amount ?? 50000))
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
  const amount = parseTopupAmount(input)
  const key = import.meta.env.VITE_TOSS_CLIENT_KEY ?? ''

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
        throw new Error('브라우저의 세션 저장소를 사용할 수 없습니다.')
      // 신청 저장 전에 저장소 사용 가능 여부를 확인한다. 토큰/시크릿은 저장하지 않는다.
      storage.setItem('lancerpick.wallet.storage-check', '1')
      storage.removeItem('lancerpick.wallet.storage-check')
      if (runtime.current) {
        await runtime.current.destroy()
        runtime.current = null
      }
      if (!alive.current) return
      const mounted = await mountTossCheckout(key, amount)
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
          failure instanceof Error && !isTossTestClientKey(key)
            ? failure
            : new Error(
                '결제 수단을 불러오지 못했습니다. 테스트 키와 토스 결제 UI 설정을 확인해 주세요.',
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
            '결제 화면을 정리하지 못했습니다. 충전 금액 변경을 다시 눌러 주세요.',
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
                '결제 절차가 중단됐습니다. 신청이 만들어졌다면 먼저 충전 상태를 확인해 주세요.',
              ),
        )
    } finally {
      busy.current = false
      if (alive.current) setLoading(false)
    }
  }

  return (
    <>
      <ol className={styles.steps} aria-label="충전 단계">
        <li className={step === 'amount' ? styles.current : ''}>
          01 금액 선택
        </li>
        <li className={step === 'checkout' ? styles.current : ''}>
          02 결제 수단
        </li>
        <li>03 충전 완료</li>
      </ol>
      <div className={styles.columns}>
        <div className={styles.formCard}>
          <h2>얼마를 충전할까요?</h2>
          <p className={styles.help}>
            1원부터 1,000,000원까지 입력할 수 있어요.
          </p>
          {pending && (
            <div className={styles.notice}>
              이전에 만든 충전 신청을 이어서 사용합니다.{' '}
              <Link to={`/mypage/wallet/topup/${pending.id}/status`}>
                신청 상태 확인
              </Link>
            </div>
          )}
          <label className={styles.label} htmlFor="topup-amount">
            충전 금액
          </label>
          <div className={styles.amountInput}>
            <Input
              id="topup-amount"
              inputMode="numeric"
              autoComplete="off"
              value={input}
              disabled={step === 'checkout' || Boolean(pending)}
              aria-invalid={!amount}
              onChange={(event) => setInput(event.target.value)}
            />
            <span>원</span>
          </div>
          {!amount && (
            <p className={styles.help} role="alert">
              1~1,000,000 사이의 정수를 입력해 주세요.
            </p>
          )}
          <div className={styles.presets}>
            {[10000, 30000, 50000, 100000].map((value) => (
              <button
                key={value}
                type="button"
                disabled={step === 'checkout' || Boolean(pending)}
                aria-pressed={amount === value}
                onClick={() => setInput(String(value))}
              >
                {formatWon(value)}
              </button>
            ))}
          </div>
          {step === 'amount' ? (
            <Button
              disabled={
                !amount || loading || (!preview && !isTossTestClientKey(key))
              }
              onClick={() => {
                void prepare()
              }}
            >
              결제 수단 선택 →
            </Button>
          ) : (
            <>
              {!pending && !submittedId && (
                <Button
                  variant="secondary"
                  disabled={loading}
                  onClick={() => {
                    void changeAmount()
                  }}
                >
                  ← 충전 금액 변경
                </Button>
              )}
              <h2 className={styles.paymentHeading}>
                결제 수단을 선택해 주세요
              </h2>
              <p className={styles.help}>
                카드로 처리되는 테스트 결제를 지원합니다. 입금 대기 수단은
                지원하지 않습니다.
              </p>
              {preview ? (
                <div className={styles.widgetPreview}>
                  <strong>카드 결제</strong>
                  <p>실제 화면에서는 토스 결제 수단과 약관이 표시됩니다.</p>
                  <span>미리보기에서는 결제를 실행할 수 없습니다.</span>
                </div>
              ) : (
                <div inert={loading}>
                  <div id="topup-payment-methods" />
                  <div id="topup-agreement" />
                </div>
              )}
              {loading && <p role="status">결제 화면을 준비하고 있어요…</p>}
              <Button
                disabled={
                  preview ||
                  loading ||
                  !widgetsReady ||
                  agreed === false ||
                  Boolean(error) ||
                  Boolean(submittedId)
                }
                onClick={() => {
                  void requestPayment()
                }}
              >
                테스트 결제하기
              </Button>
            </>
          )}
          {!preview && !isTossTestClientKey(key) && (
            <p className={styles.help}>
              토스 테스트 클라이언트 키가 준비되면 결제 수단을 선택할 수
              있습니다.
            </p>
          )}
          {error !== undefined && (
            <div className={styles.error} role="alert">
              <strong>진행 상태를 확인해 주세요</strong>
              <p>
                {error instanceof Error
                  ? error.message
                  : '요청을 처리하지 못했습니다.'}
              </p>
            </div>
          )}
          {error !== undefined && !widgetsReady && !submittedId && (
            <Button
              variant="secondary"
              disabled={loading}
              onClick={() => {
                void prepare()
              }}
            >
              결제 수단 다시 불러오기
            </Button>
          )}
          {isUnauthorized(error) && (
            <Link className={styles.textLink} to="/login">
              다시 로그인하기 →
            </Link>
          )}
          {submittedId && (
            <Link
              className={styles.textLink}
              to={`/mypage/wallet/topup/${submittedId}/status`}
            >
              이 충전 신청의 상태 확인 →
            </Link>
          )}
        </div>
        <aside className={styles.summary} aria-label="충전 요약">
          <span className={styles.tag}>TEST PAYMENT</span>
          <h2>충전할 적립금</h2>
          <strong className={styles.total}>
            {amount ? formatWon(amount) : '금액을 입력해 주세요'}
          </strong>
          <dl>
            <div>
              <dt>충전 비율</dt>
              <dd>1원 = 1 적립금</dd>
            </div>
            <div>
              <dt>추가 수수료</dt>
              <dd>없음</dd>
            </div>
            <div>
              <dt>사용처</dt>
              <dd>랜서픽 서비스 구매</dd>
            </div>
          </dl>
          <p>
            학습용 테스트 결제입니다. 실제 돈이 청구되지 않으며 적립금의 현금
            전환·출금은 지원하지 않습니다.
          </p>
          <p>서버의 승인과 지급이 완료돼야 잔액에 반영됩니다.</p>
        </aside>
      </div>
    </>
  )
}
