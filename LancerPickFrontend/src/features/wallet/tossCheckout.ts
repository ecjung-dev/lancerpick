import type { TossPaymentsWidgets, WidgetAgreementWidget, WidgetPaymentMethodWidget } from '@tosspayments/tosspayments-sdk'

export interface CheckoutWidgets {
  widgets: TossPaymentsWidgets
  methods: WidgetPaymentMethodWidget
  agreement: WidgetAgreementWidget
  destroy: () => Promise<void>
}

export function isTossTestClientKey(key: string) { return /^test_gck_[A-Za-z0-9_]+$/.test(key) }

/** 테스트 키만 허용. SDK는 사용자가 결제 수단을 여는 시점에 로드한다. */
export async function mountTossCheckout(key: string, amount: number): Promise<CheckoutWidgets> {
  if (!isTossTestClientKey(key)) throw new Error('토스 테스트 클라이언트 키 설정을 확인해 주세요.')
  const { loadTossPayments, ANONYMOUS } = await import('@tosspayments/tosspayments-sdk')
  const sdk = await loadTossPayments(key)
  // PG의 저장형 고객 기능을 쓰지 않는다. 사이트 회원/충전 소유권은 서버에서 별도로 검증한다.
  const widgets = sdk.widgets({ customerKey: ANONYMOUS })
  await widgets.setAmount({ currency: 'KRW', value: amount })
  const methods = await widgets.renderPaymentMethods({ selector: '#topup-payment-methods' })
  try {
    const agreement = await widgets.renderAgreement({ selector: '#topup-agreement' })
    return { widgets, methods, agreement, destroy: async () => { await Promise.all([methods.destroy(), agreement.destroy()]) } }
  } catch (error) {
    await methods.destroy()
    throw error
  }
}
