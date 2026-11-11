import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import { isValidProductId as isValidId } from './validation.ts'

/**
 * 관리자 금융·계약 PDF 운영 조회(K3·K6 조회만). ID는 문자열, 금액은 정수 원, 시각은 UTC다.
 * 응답의 후보 판단(eligible·reason)은 조회 순간의 조건일 뿐 실행 권한이 아니다. 실행은 reprocessApi.ts(K6)가 따로 요청하고 서버가 다시 검사한다.
 */
export type TopupStatus = 'READY' | 'PROCESSING' | 'UNKNOWN' | 'COMPLETED' | 'FAILED'
export type PolicyVersion = 'LEGACY_V1' | 'CONTRACT_V2' | 'CONTRACT_V3'
export interface Decision { eligible: boolean; reason: string }

export interface Topup {
  id: string; memberId: string; amount: number; status: TopupStatus; lastErrorCode: string | null
  recoveryAttemptCount: number; createdAt: string; processingStartedAt: string | null; nextRecoveryAt: string | null
  recoveryLeaseUntil: string | null; completedAt: string | null; automaticRecovery: Decision; manualRecheck: Decision
}

export interface PaymentRecord {
  id: string; orderId: string; buyerId: string; sellerId: string; policyVersion: string; orderStatus: string
  status: string; amount: number; refundedAmount: number; paidAt: string; refundedAt: string | null
}

export interface OrderFinance {
  orderId: string; buyerId: string; sellerId: string; policyVersion: string; orderStatus: string; orderAmount: number
  payment: PaymentRecord | null
  funds: { paymentId: string; status: string; amount: number; remainingAmount: number; heldAt: string; resolvedAt: string | null } | null
  settlement: { id: string; type: string; grossAmount: number; feeRateBps: number; feeAmount: number; sellerAmount: number; createdAt: string } | null
  closure: { reason: string; paidAmount: number; buyerRefundAmount: number; sellerGrossAmount: number; feeAmount: number; sellerNetAmount: number } | null
}

export interface RecoveryJob {
  executionId: string | null; instanceId: string; status: string; scanRequestedAt: string | null
  readCount: number; creditedCount: number; filteredCount: number; outcomes: Record<string, number>; restart: Decision
}

export interface ContractPdf {
  orderId: string; contractId: string; version: number; status: string; currentVersion: boolean; fileMetadataPresent: boolean
  createdAt: string; updatedAt: string; checkedAt: string
  generationCandidate: { eligible: boolean; kind: string; reason: string | null }
}

export interface TopupFilter { memberId: string; status: '' | TopupStatus }
export interface PaymentFilter { orderId: string; buyerId: string; sellerId: string; status: '' | 'PAID' | 'REFUNDED'; policyVersion: '' | PolicyVersion }

const POSITIVE = /^[1-9][0-9]{0,18}$/
const TOPUP_STATUSES: TopupStatus[] = ['READY', 'PROCESSING', 'UNKNOWN', 'COMPLETED', 'FAILED']
const POLICIES: PolicyVersion[] = ['LEGACY_V1', 'CONTRACT_V2', 'CONTRACT_V3']
const invalid = () => Promise.reject(new Error('올바른 번호가 아니에요.'))

/** URL에서 필터를 읽는다. 허용하지 않는 값은 "조건 없음"으로 바꿔 서버 400을 일으키지 않는다. */
export function readTopupFilter(params: URLSearchParams): TopupFilter {
  const memberId = params.get('memberId') ?? ''
  const status = params.get('status') ?? ''
  return {
    memberId: POSITIVE.test(memberId) ? memberId : '',
    status: (TOPUP_STATUSES as string[]).includes(status) ? (status as TopupStatus) : '',
  }
}

export function readPaymentFilter(params: URLSearchParams): PaymentFilter {
  const id = (key: string) => { const value = params.get(key) ?? ''; return POSITIVE.test(value) ? value : '' }
  const status = params.get('status')
  const policy = params.get('policyVersion') ?? ''
  return {
    orderId: id('orderId'), buyerId: id('buyerId'), sellerId: id('sellerId'),
    status: status === 'PAID' || status === 'REFUNDED' ? status : '',
    policyVersion: (POLICIES as string[]).includes(policy) ? (policy as PolicyVersion) : '',
  }
}

function query(page: number, entries: Record<string, string>) {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  for (const [key, value] of Object.entries(entries)) if (value) params.set(key, value)
  return params
}

export function getTopups(filter: TopupFilter, page: number, signal?: AbortSignal) {
  return apiRequest<PageResponse<Topup>>(`/admin/finance/topups?${query(page, { ...filter })}`, { signal })
}

export function getPayments(filter: PaymentFilter, page: number, signal?: AbortSignal) {
  return apiRequest<PageResponse<PaymentRecord>>(`/admin/finance/payments?${query(page, { ...filter })}`, { signal })
}

export function getRecoveryJobs(page: number, signal?: AbortSignal) {
  return apiRequest<PageResponse<RecoveryJob>>(`/admin/finance/recovery-jobs?${query(page, {})}`, { signal })
}

export function getOrderFinance(orderId: string, signal?: AbortSignal) {
  if (!isValidId(orderId)) return invalid()
  return apiRequest<OrderFinance>(`/admin/finance/orders/${orderId}`, { signal })
}

export function getContractPdf(orderId: string, signal?: AbortSignal) {
  if (!isValidId(orderId)) return invalid()
  return apiRequest<ContractPdf>(`/admin/orders/${orderId}/contract-pdf`, { signal })
}

/** 금융 상태는 결제·복구 작업·다른 관리자로 계속 바뀌므로 매번 최신으로 조회하고 보관하지 않는다. ['admin'] 아래라 계정 전환 때 지워진다. */
const fresh = { retry: false, staleTime: 0, gcTime: 0 } as const
export const financeKeys = {
  topups: (filter: TopupFilter, page: number) => ['admin', 'finance', 'topups', filter, page] as const,
  payments: (filter: PaymentFilter, page: number) => ['admin', 'finance', 'payments', filter, page] as const,
  jobs: (page: number) => ['admin', 'finance', 'jobs', page] as const,
  order: (id: string) => ['admin', 'finance', 'order', id] as const,
  pdf: (id: string) => ['admin', 'finance', 'pdf', id] as const,
}

export const useTopups = (filter: TopupFilter, page: number) =>
  useQuery({ queryKey: financeKeys.topups(filter, page), queryFn: ({ signal }) => getTopups(filter, page, signal), ...fresh })
export const usePayments = (filter: PaymentFilter, page: number) =>
  useQuery({ queryKey: financeKeys.payments(filter, page), queryFn: ({ signal }) => getPayments(filter, page, signal), ...fresh })
export const useRecoveryJobs = (page: number) =>
  useQuery({ queryKey: financeKeys.jobs(page), queryFn: ({ signal }) => getRecoveryJobs(page, signal), ...fresh })
export const useOrderFinance = (id: string) =>
  useQuery({ queryKey: financeKeys.order(id), queryFn: ({ signal }) => getOrderFinance(id, signal), ...fresh })
export const useContractPdf = (id: string) =>
  useQuery({ queryKey: financeKeys.pdf(id), queryFn: ({ signal }) => getContractPdf(id, signal), ...fresh })

/** 후보가 아닌 이유. 원래 코드도 함께 보여 줘 개발자와 같은 말로 찾을 수 있게 한다. 모르는 값은 코드만 표시한다. */
const REASONS: Record<string, string> = {
  TERMINAL_STATE: '이미 끝난 충전', PAYMENT_NOT_IDENTIFIED: '결제 식별 정보 없음', TOO_EARLY: '처리 시작 직후라 아직 기다리는 중',
  LEASE_ACTIVE: '다른 작업이 처리 중', RETRY_DELAY: '다음 재시도 시각 전', AUTOMATIC_LIMIT_REACHED: '자동 재시도 횟수 초과',
  RECOVERY_DISABLED: '복구 기능 꺼짐', PROVIDER_DISABLED: '결제 연동 꺼짐', JOB_RUNNING: '실행 중',
  JOB_NOT_RESTARTABLE: '재시작할 수 없는 상태', NEWER_EXECUTION_EXISTS: '더 최근 실행이 있음', INVALID_JOB_PARAMETERS: '실행 조건 값 오류',
  NOT_CURRENT_VERSION: '현재 버전이 아님', ORDER_NOT_WRITABLE: '지금 주문 상태에서는 생성 불가', CANCELLATION_PENDING: '취소 요청 대기 중',
  DOCUMENT_READY: '문서 생성 완료', DOCUMENT_SIGNED: '서명 완료', GENERATION_ACTIVE: '생성 중',
  CLOCK_BEFORE_UPDATE: '서버 시각 확인 필요', UNSUPPORTED_STATE: '지원하지 않는 상태',
}
const KINDS: Record<string, string> = { INITIAL_GENERATION: '최초 생성', RETRY_FAILED: '실패 후 재생성', RETRY_EXPIRED: '시간 초과 후 재생성' }

/** 후보 판단 표시. "후보"는 실행 허가가 아님을 문구로 드러낸다. */
export function decisionLabel(eligible: boolean, reason: string | null | undefined, kind?: string) {
  if (eligible) return `재처리 후보${kind && KINDS[kind] ? ` (${KINDS[kind]})` : ''} · 실행 직전에 서버가 다시 확인해요`
  if (!reason || reason === 'NONE') return '후보 아님'
  return `후보 아님: ${REASONS[reason] ? `${REASONS[reason]} (${reason})` : reason}`
}
