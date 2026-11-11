import { adminReturnTo, type AdminReturnOrigin } from '../navigation'
import { MemberLink } from '../components/MemberLink'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { orderStatusLabel } from '../../orders/status'
import { decisionLabel, useContractPdf, useOrderFinance, type ContractPdf, type OrderFinance } from '../financeApi'
import { isValidProductId as isValidId } from '../validation'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import { AdminNav } from './AdminNav'
import { AdminReprocessPanel } from '../components/AdminReprocessPanel'
import { AdminActionLogList } from '../components/AdminActionLogList'
import {
  closureReasonLabels, codeLabel, contractStatusLabels, fundsStatusLabels, paymentStatusLabels, policyLabels, settlementTypeLabels,
} from '../statusLabels'
import styles from './Admin.module.css'

/** 주문 금융 상세로 들어오는 화면: 금융 조회(결제 목록), 문의 상세, 알림 처리, 자동 확정 타이머. */
const FINANCE_ORIGINS: readonly AdminReturnOrigin[] = [
  { path: '/admin/finance', label: '금융 조회' },
  { path: /\/admin\/inquiries\/[1-9]\d*/, label: '운영 문의' },
  { path: '/admin/outbox', label: '알림 처리' },
  { path: '/admin/review-timers', label: '자동 확정 타이머' },
]
const time = (value: string | null | undefined) => value ? formatDateTime(value) : '—'
/** 기록이 없으면 0원으로 바꾸지 않고 "기록 없음"으로 보여 준다. */
const NONE = <p>기록 없음</p>
/** 섹션마다 dl이 따로 있어도 라벨 열 폭을 맞춘다. */
const FACTS = `${styles.facts} ${styles.factsAligned}`

/** /admin/finance/orders/:id. 한 주문의 결제·보관금·정산·종료 분배와 계약 PDF 상태를 조회하고, 후보인 PDF는 재생성을 요청할 수 있다(K6). */
export function AdminOrderFinancePage() {
  const { id = '' } = useParams()
  const back = adminReturnTo(useLocation().state?.from, FINANCE_ORIGINS, { to: '/admin/finance', label: '금융 조회' })
  const finance = useOrderFinance(id)
  const problem = accessProblem(finance.error)
  const missing = !isValidId(id) || (finance.error instanceof ApiError && finance.error.status === 404)
  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <Link to={back.to}>← {back.label}</Link>
    {problem ? <AdminAccessNotice problem={problem} />
      : missing ? <AsyncState title="주문을 찾을 수 없어요" description="주문 번호를 다시 확인해 주세요." />
        : finance.isError ? <AsyncState title="주문 금액을 불러오지 못했어요" error={finance.error} onRetry={() => { void finance.refetch() }} />
          : !finance.data ? <AsyncState title="주문 금액을 불러오고 있어요" />
            : <><FinanceSummary data={finance.data} /><ContractPdfSection orderId={id} />
              <section className={styles.block}><h2>관리자 작업 이력</h2>
                <AdminActionLogList filter={{ orderId: id }} empty="이 주문의 계약 PDF 관리자 작업 이력이 없어요." /></section></>}
  </section>
}

function FinanceSummary({ data }: { data: OrderFinance }) {
  const { payment, funds, settlement, closure } = data
  const here = `/admin/finance/orders/${data.orderId}`
  return <>
    <header className={styles.heading}>
      <p>{codeLabel(policyLabels, data.policyVersion)} · 주문 {orderStatusLabel(data.orderStatus)}</p>
      <h1>주문 #{data.orderId}</h1>
      <p>주문 금액 {formatWon(data.orderAmount)} · <MemberLink id={data.buyerId} role="구매자" from={here} /> · <MemberLink id={data.sellerId} role="판매자" from={here} /></p>
    </header>
    {/* 금액 네 묶음은 카드로, 1000px 이상에서 두 열. 카드가 좁으면 라벨 위·값 아래로 바뀐다(Admin.module.css @container). */}
    <div className={styles.financeGrid}>
    <section className={styles.card}><h2>결제·환불</h2>{payment ? <dl className={FACTS}>
      <dt>상태</dt><dd><span className={styles.badge}>{codeLabel(paymentStatusLabels, payment.status)}</span></dd>
      <dt>결제 금액</dt><dd><span className={styles.amount}>{formatWon(payment.amount)}</span> · {time(payment.paidAt)}</dd>
      <dt>환불 금액</dt><dd><span className={styles.amount}>{formatWon(payment.refundedAmount)}</span>{payment.refundedAt && <> · {time(payment.refundedAt)}</>}</dd>
    </dl> : NONE}</section>
    <section className={styles.card}><h2>보관금</h2>{funds ? <dl className={FACTS}>
      <dt>상태</dt><dd><span className={styles.badge}>{codeLabel(fundsStatusLabels, funds.status)}</span></dd>
      <dt>보관 금액</dt><dd><span className={styles.amount}>{formatWon(funds.amount)}</span> · 남은 금액 <span className={styles.amount}>{formatWon(funds.remainingAmount)}</span></dd>
      <dt>보관 시작</dt><dd>{time(funds.heldAt)}</dd><dt>처리</dt><dd>{time(funds.resolvedAt)}</dd>
    </dl> : NONE}</section>
    <section className={styles.card}><h2>정산</h2>{settlement ? <dl className={FACTS}>
      <dt>유형</dt><dd><span className={styles.badge}>{codeLabel(settlementTypeLabels, settlement.type)}</span></dd>
      <dt>총액</dt><dd><span className={styles.amount}>{formatWon(settlement.grossAmount)}</span></dd>
      <dt>수수료</dt><dd><span className={styles.amount}>{formatWon(settlement.feeAmount)}</span> ({(settlement.feeRateBps / 100).toFixed(2)}%)</dd>
      <dt>판매자 수익</dt><dd><span className={styles.amount}>{formatWon(settlement.sellerAmount)}</span></dd><dt>시각</dt><dd>{time(settlement.createdAt)}</dd>
    </dl> : NONE}</section>
    <section className={styles.card}><h2>종료 분배</h2>{closure ? <dl className={FACTS}>
      <dt>사유</dt><dd><span className={styles.badge}>{codeLabel(closureReasonLabels, closure.reason)}</span></dd>
      <dt>결제 금액</dt><dd><span className={styles.amount}>{formatWon(closure.paidAmount)}</span></dd>
      <dt>구매자 반환</dt><dd><span className={styles.amount}>{formatWon(closure.buyerRefundAmount)}</span></dd>
      <dt>판매자 총액·수수료·순수익</dt><dd><span className={styles.amount}>{formatWon(closure.sellerGrossAmount)}</span> · <span className={styles.amount}>{formatWon(closure.feeAmount)}</span> · <span className={styles.amount}>{formatWon(closure.sellerNetAmount)}</span></dd>
    </dl> : NONE}</section>
    </div>
  </>
}

function ContractPdfSection({ orderId }: { orderId: string }) {
  const pdf = useContractPdf(orderId)
  const problem = accessProblem(pdf.error)
  return <section className={`${styles.card} ${styles.block}`}>
    <h2>계약 PDF</h2>
    {problem ? <AdminAccessNotice problem={problem} />
      : pdf.error instanceof ApiError && pdf.error.status === 404 ? <p>계약이 없는 주문이에요(기존 거래이거나 아직 계약을 만들지 않았어요).</p>
        : pdf.isError ? <AsyncState title="계약 PDF 상태를 불러오지 못했어요" error={pdf.error} onRetry={() => { void pdf.refetch() }} />
          : !pdf.data ? <AsyncState title="계약 PDF 상태를 불러오고 있어요" />
            : <PdfFacts data={pdf.data} />}
  </section>
}

function PdfFacts({ data }: { data: ContractPdf }) {
  return <><dl className={FACTS}>
    <dt>버전</dt><dd>{data.version}{data.currentVersion ? ' (현재)' : ''} · 계약 #{data.contractId}</dd>
    <dt>상태</dt><dd><span className={styles.badge}>{codeLabel(contractStatusLabels, data.status)}</span></dd>
    <dt>파일 정보</dt><dd>{data.fileMetadataPresent ? 'DB에 파일 정보 있음(실제 파일 확인은 아님)' : 'DB에 파일 정보 없음'}</dd>
    <dt>생성·변경</dt><dd>{time(data.createdAt)} · {time(data.updatedAt)}</dd>
    <dt>판단</dt><dd>{decisionLabel(data.generationCandidate.eligible, data.generationCandidate.reason, data.generationCandidate.kind)} · 확인 {time(data.checkedAt)}</dd>
  </dl>
  {/* 조회한 버전을 그대로 보낸다. 그사이 계약이 바뀌면 서버가 이번 생성을 반영하지 않는다. */}
  <AdminReprocessPanel action="CONTRACT_PDF_REGENERATE" target={data.orderId} version={data.version}
    eligible={data.currentVersion && data.generationCandidate.eligible} />
  </>
}
