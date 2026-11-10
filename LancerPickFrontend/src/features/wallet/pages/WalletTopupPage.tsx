import { useTopupAccess } from '../queries'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { formatWon } from '../../../shared/lib/format'
import { parseTopupAmount, readPendingCheckout } from '../checkoutFlow'
import { TopupSummary } from '../components/TopupSummary'
import { isTossTestClientKey } from '../tossCheckout'
import { useTopupCheckout } from '../useTopupCheckout'
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
  const amount = parseTopupAmount(input)
  const key = import.meta.env.VITE_TOSS_CLIENT_KEY ?? ''
  const {
    step,
    loading,
    agreed,
    widgetsReady,
    error,
    submittedId,
    prepare,
    changeAmount,
    requestPayment,
  } = useTopupCheckout({ preview, pending, amount, clientKey: key })

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
        <TopupSummary amount={amount} />
      </div>
    </>
  )
}
