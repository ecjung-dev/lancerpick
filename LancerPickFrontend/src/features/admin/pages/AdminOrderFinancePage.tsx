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
import styles from './Admin.module.css'

/** 결제 목록에서 넘겨준 돌아갈 주소. 금융 조회 경로만 허용한다. */
function backPath(from: unknown) {
  return typeof from === 'string' && (from === '/admin/finance' || from.startsWith('/admin/finance?')) ? from : '/admin/finance'
}
const time = (value: string | null | undefined) => value ? formatDateTime(value) : '—'
/** 기록이 없으면 0원으로 바꾸지 않고 "기록 없음"으로 보여 준다. */
const NONE = <p>기록 없음</p>

/** /admin/finance/orders/:id. 한 주문의 결제·보관금·정산·종료 분배와 계약 PDF 상태를 조회하고, 후보인 PDF는 재생성을 요청할 수 있다(K6). */
export function AdminOrderFinancePage() {
  const { id = '' } = useParams()
  const back = backPath(useLocation().state?.from)
  const finance = useOrderFinance(id)
  const problem = accessProblem(finance.error)
  const missing = !isValidId(id) || (finance.error instanceof ApiError && finance.error.status === 404)
  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <Link to={back}>← 금융 조회</Link>
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
  return <>
    <header className={styles.heading}>
      <p>{data.policyVersion} · 주문 {orderStatusLabel(data.orderStatus)}</p>
      <h1>주문 #{data.orderId}</h1>
      <p>주문 금액 {formatWon(data.orderAmount)} · 구매자 #{data.buyerId} · 판매자 #{data.sellerId}</p>
    </header>
    <section className={styles.block}><h2>결제·환불</h2>{payment ? <dl className={styles.facts}>
      <dt>상태</dt><dd>{payment.status}</dd>
      <dt>결제 금액</dt><dd>{formatWon(payment.amount)} · {time(payment.paidAt)}</dd>
      <dt>환불 금액</dt><dd>{formatWon(payment.refundedAmount)}{payment.refundedAt && <> · {time(payment.refundedAt)}</>}</dd>
    </dl> : NONE}</section>
    <section className={styles.block}><h2>보관금</h2>{funds ? <dl className={styles.facts}>
      <dt>상태</dt><dd>{funds.status}</dd>
      <dt>보관 금액</dt><dd>{formatWon(funds.amount)} · 남은 금액 {formatWon(funds.remainingAmount)}</dd>
      <dt>보관 시작</dt><dd>{time(funds.heldAt)}</dd><dt>처리</dt><dd>{time(funds.resolvedAt)}</dd>
    </dl> : NONE}</section>
    <section className={styles.block}><h2>정산</h2>{settlement ? <dl className={styles.facts}>
      <dt>유형</dt><dd>{settlement.type}</dd>
      <dt>총액</dt><dd>{formatWon(settlement.grossAmount)}</dd>
      <dt>수수료</dt><dd>{formatWon(settlement.feeAmount)} ({(settlement.feeRateBps / 100).toFixed(2)}%)</dd>
      <dt>판매자 수익</dt><dd>{formatWon(settlement.sellerAmount)}</dd><dt>시각</dt><dd>{time(settlement.createdAt)}</dd>
    </dl> : NONE}</section>
    <section className={styles.block}><h2>종료 분배</h2>{closure ? <dl className={styles.facts}>
      <dt>사유</dt><dd>{closure.reason}</dd>
      <dt>결제 금액</dt><dd>{formatWon(closure.paidAmount)}</dd>
      <dt>구매자 반환</dt><dd>{formatWon(closure.buyerRefundAmount)}</dd>
      <dt>판매자 총액·수수료·순수익</dt><dd>{formatWon(closure.sellerGrossAmount)} · {formatWon(closure.feeAmount)} · {formatWon(closure.sellerNetAmount)}</dd>
    </dl> : NONE}</section>
  </>
}

function ContractPdfSection({ orderId }: { orderId: string }) {
  const pdf = useContractPdf(orderId)
  const problem = accessProblem(pdf.error)
  return <section className={styles.block}>
    <h2>계약 PDF</h2>
    {problem ? <AdminAccessNotice problem={problem} />
      : pdf.error instanceof ApiError && pdf.error.status === 404 ? <p>계약이 없는 주문이에요(기존 거래이거나 아직 계약을 만들지 않았어요).</p>
        : pdf.isError ? <AsyncState title="계약 PDF 상태를 불러오지 못했어요" error={pdf.error} onRetry={() => { void pdf.refetch() }} />
          : !pdf.data ? <AsyncState title="계약 PDF 상태를 불러오고 있어요" />
            : <PdfFacts data={pdf.data} />}
  </section>
}

function PdfFacts({ data }: { data: ContractPdf }) {
  return <><dl className={styles.facts}>
    <dt>버전</dt><dd>{data.version}{data.currentVersion ? ' (현재)' : ''} · 계약 #{data.contractId}</dd>
    <dt>상태</dt><dd>{data.status}</dd>
    <dt>파일 정보</dt><dd>{data.fileMetadataPresent ? 'DB에 파일 정보 있음(실제 파일 확인은 아님)' : 'DB에 파일 정보 없음'}</dd>
    <dt>생성·변경</dt><dd>{time(data.createdAt)} · {time(data.updatedAt)}</dd>
    <dt>판단</dt><dd>{decisionLabel(data.generationCandidate.eligible, data.generationCandidate.reason, data.generationCandidate.kind)} · 확인 {time(data.checkedAt)}</dd>
  </dl>
  {/* 조회한 버전을 그대로 보낸다. 그사이 계약이 바뀌면 서버가 이번 생성을 반영하지 않는다. */}
  <AdminReprocessPanel action="CONTRACT_PDF_REGENERATE" target={data.orderId} version={data.version}
    eligible={data.currentVersion && data.generationCandidate.eligible} />
  </>
}
