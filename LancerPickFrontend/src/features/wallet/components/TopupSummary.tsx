import { formatWon } from '../../../shared/lib/format'
import styles from '../pages/WalletTopupPage.module.css'

/** 충전 화면 오른쪽 요약. 입력 중인 금액만 표시하며 잔액은 보관·표시하지 않는다. */
export function TopupSummary({ amount }: { amount: number | null }) {
  return (
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
  )
}
