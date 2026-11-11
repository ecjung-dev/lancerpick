import { skipToken, useQuery, type QueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import { captureAccountScope, isUnclearResult } from '../auth/session.ts'
import { isValidProductId as isValidId } from './validation.ts'

/**
 * K6 관리자 재처리 실행·감사 이력(API는 AdminReprocessingController).
 * 서버가 감사 요청을 먼저 커밋하고 업무를 실행한 뒤 결과를 따로 커밋한다. 화면은 결과를 고르지 않고 receipt의 상태·결과 코드만 보여 준다.
 * 같은 requestKey·같은 사유로 다시 보내면 서버는 다시 실행하지 않고 기존 기록을 돌려준다(200).
 */
export type ReprocessAction = 'TOPUP_RECHECK' | 'TOPUP_RECOVERY_RESTART' | 'CONTRACT_PDF_REGENERATE'
export type ActionStatus = 'REQUESTED' | 'SUCCEEDED' | 'REJECTED' | 'FAILED' | 'UNKNOWN'

export interface ActionReceipt {
  id: string; adminId: string; action: ReprocessAction; targetType: string; targetId: string; orderId: string | null
  reason: string; requestKey: string; status: ActionStatus; resultCode: string | null
  requestedAt: string; leaseUntil: string; completedAt: string | null
}

/** target은 충전 ID / Batch 실행 ID / 주문 ID(PDF). version은 PDF에서만 쓴다. */
export interface ReprocessCommand {
  action: ReprocessAction; target: string; version?: number; reason: string; requestKey: string
}

function path(command: ReprocessCommand) {
  if (!isValidId(command.target)) throw new Error('대상 번호를 확인해 주세요.')
  switch (command.action) {
    case 'TOPUP_RECHECK': return `/admin/finance/topups/${command.target}/recheck`
    case 'TOPUP_RECOVERY_RESTART': return `/admin/finance/recovery-jobs/${command.target}/restart`
    case 'CONTRACT_PDF_REGENERATE': return `/admin/orders/${command.target}/contract-pdf/regenerate`
  }
}

/** 관리자 ID·결과는 보내지 않는다. 다시 보낼 때는 저장해 둔 본문을 그대로 보낸다. */
export function executeReprocess(command: ReprocessCommand, isCurrentAccount?: () => boolean) {
  const json: Record<string, unknown> = { reason: command.reason, requestKey: command.requestKey }
  if (command.action === 'CONTRACT_PDF_REGENERATE' && command.version !== undefined) json.version = command.version
  return apiRequest<ActionReceipt>(path(command), { method: 'POST', json, cache: 'no-store', isCurrentAccount })
}

export interface ActionLogFilter { action?: ReprocessAction; targetId?: string; orderId?: string }

export function getActionLogs(filter: ActionLogFilter, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (filter.action) params.set('action', filter.action)
  if (filter.action && filter.targetId && isValidId(filter.targetId)) params.set('targetId', filter.targetId)
  if (filter.orderId && isValidId(filter.orderId)) params.set('orderId', filter.orderId)
  return apiRequest<PageResponse<ActionReceipt>>(`/admin/action-logs?${params}`, { signal, cache: 'no-store' })
}

export const actionLogPrefix = ['admin', 'action-logs'] as const
export const useActionLogs = (filter: ActionLogFilter, page: number) =>
  useQuery({
    queryKey: [...actionLogPrefix, filter, page],
    queryFn: ({ signal }) => getActionLogs(filter, page, signal),
    retry: false, staleTime: 0, gcTime: 0,
  })

/**
 * 보낸 요청을 계정별 메모리에 보관한다(['admin'] 아래라 로그아웃·계정 전환 때 지워진다).
 * sending: 응답 대기 중, uncertain: 서버가 처리했을 수 있어 같은 요청으로 결과를 확인해야 함, done: 서버가 기록을 돌려줌.
 */
export interface PendingReprocess {
  command: ReprocessCommand; phase: 'sending' | 'uncertain' | 'done'; receipt?: ActionReceipt; operationId: string
}
const pendingPrefix = ['admin', 'reprocess'] as const
export const pendingReprocessKey = (action: ReprocessAction, target: string) => [...pendingPrefix, action, target] as const

export function usePendingReprocess(action: ReprocessAction, target: string) {
  return useQuery<PendingReprocess | null>({
    queryKey: pendingReprocessKey(action, target), queryFn: skipToken, initialData: null, gcTime: Infinity,
  }).data ?? null
}

/** 끝난 결과를 지우고 새 요청을 쓸 수 있게 한다. 결과가 불명확한 요청은 지우지 않는다. */
export function clearReprocessResult(client: QueryClient, action: ReprocessAction, target: string) {
  const key = pendingReprocessKey(action, target)
  if (client.getQueryData<PendingReprocess | null>(key)?.phase === 'done') client.setQueryData(key, null)
}

/**
 * 새 요청(input) 또는 결과가 불명확했던 이전 요청(같은 키·같은 본문)을 보낸다.
 * - 처리 중에는 다시 보내지 않는다. 자동 재시도도 하지 않는다.
 * - 통신 오류·5xx: 서버가 기록했을 수 있으므로 요청을 남겨 두고 "같은 요청으로 결과 확인"만 허용한다.
 * - 명확한 거절(4xx): 첫 요청이면 기록이 없으므로 지운다. 이미 불명확했던 요청의 재전송이 거절되면 첫 요청 결과를 알 수 없어 그대로 둔다.
 * - 응답이 와도 계정이 바뀌었으면 아무것도 바꾸지 않는다.
 */
export async function submitReprocess(
  client: QueryClient, action: ReprocessAction, target: string, input?: ReprocessCommand,
  isCurrentAccount = captureAccountScope(client),
) {
  if (!isCurrentAccount()) throw new DOMException('계정이 변경됐어요.', 'AbortError')
  const key = pendingReprocessKey(action, target)
  const previous = client.getQueryData<PendingReprocess | null>(key)
  if (previous?.phase === 'sending') throw new Error('이미 처리 중인 요청이 있어요.')
  const retrying = previous?.phase === 'uncertain'
  const command = retrying ? previous.command : input
  if (!command || command.action !== action || command.target !== target) throw new Error('처리할 요청을 확인해 주세요.')
  client.setQueryDefaults(pendingPrefix, { gcTime: Infinity })
  const pending: PendingReprocess = { command, phase: 'sending', operationId: crypto.randomUUID() }
  client.setQueryData(key, pending)
  const mine = () => client.getQueryData<PendingReprocess | null>(key)?.operationId === pending.operationId
  try {
    const receipt = await executeReprocess(command, isCurrentAccount)
    if (!isCurrentAccount()) return receipt
    if (mine()) client.setQueryData(key, { ...pending, phase: 'done', receipt })
    return receipt
  } catch (error) {
    if (isCurrentAccount() && mine())
      client.setQueryData(key, isUnclearResult(error) || retrying ? { ...pending, phase: 'uncertain' } : null)
    throw error
  } finally {
    // 결과와 상관없이 대상의 최신 상태·이력을 다시 읽는다.
    if (isCurrentAccount()) {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['admin', 'finance'] }),
        client.invalidateQueries({ queryKey: actionLogPrefix }),
      ])
    }
  }
}

export const ACTION_LABELS: Record<ReprocessAction, string> = {
  TOPUP_RECHECK: '충전 재확인', TOPUP_RECOVERY_RESTART: '충전 복구 작업 재시작', CONTRACT_PDF_REGENERATE: '계약 PDF 다시 만들기',
}
const TARGET_LABELS: Record<ReprocessAction, string> = {
  TOPUP_RECHECK: '충전', TOPUP_RECOVERY_RESTART: '실행', CONTRACT_PDF_REGENERATE: '계약',
}
export const targetLabel = (receipt: ActionReceipt) =>
  `${TARGET_LABELS[receipt.action] ?? receipt.targetType} #${receipt.targetId}${receipt.orderId ? ` · 주문 #${receipt.orderId}` : ''}`

export const STATUS_LABELS: Record<ActionStatus, string> = {
  REQUESTED: '처리 중', SUCCEEDED: '처리됨', REJECTED: '반영 안 됨', FAILED: '실패', UNKNOWN: '결과 확인 필요',
}

/** 결과 코드 설명. "수락"과 "완료"를 섞지 않는다. 모르는 코드는 코드만 보여 준다. */
const RESULTS: Record<string, string> = {
  TOPUP_CREDITED: '결제사 확인 후 적립금에 반영했어요.',
  TOPUP_RESOLVED_FAILED: '결제사에서 취소·실패로 확정됐어요. 충전되지 않았어요.',
  TOPUP_STATE_CHANGED: '그사이 충전 상태나 처리 담당이 바뀌어 이번 요청은 반영하지 않았어요.',
  TOPUP_RESULT_UNCONFIRMED: '결제사 응답이나 반영 결과를 확인하지 못했어요. 충전 상태를 다시 조회해 주세요.',
  BATCH_RESTART_ACCEPTED: '재시작을 접수했어요. 작업이 끝났다는 뜻은 아니니 복구 작업 목록에서 최신 실행을 확인해 주세요.',
  BATCH_RESTART_REJECTED: '재시작 조건에 맞지 않아 거절됐어요(이미 실행 중이거나 더 최근 실행이 있는 경우 등).',
  BATCH_RESULT_UNCONFIRMED: '재시작 접수 여부를 확인하지 못했어요. 복구 작업 목록을 다시 확인해 주세요.',
  PDF_GENERATED: '계약 PDF를 만들어 저장했어요.',
  PDF_GENERATION_FAILED: 'PDF를 만들지 못했어요. 계약은 실패 상태로 기록돼 다시 시도할 수 있어요.',
  PDF_STATE_CHANGED: '그사이 계약이나 주문 상태가 바뀌어 이번 생성은 반영하지 않았어요.',
  PDF_RESULT_UNCONFIRMED: 'PDF 생성 결과를 확인하지 못했어요. 계약 PDF 상태를 다시 조회해 주세요.',
  LEASE_EXPIRED: '결과를 받지 못한 채 대기 시간이 지나 닫힌 요청이에요. 대상 상태를 직접 확인해 주세요.',
  SUCCEEDED: '처리됐어요.', BUSINESS_REJECTED: '업무 조건에 맞지 않아 반영하지 않았어요.',
  EXECUTION_FAILED: '실행에 실패했어요.', RESULT_UNCONFIRMED: '결과를 확인하지 못했어요.',
}
export function resultLabel(receipt: Pick<ActionReceipt, 'status' | 'resultCode'>) {
  if (receipt.status === 'REQUESTED') return '서버가 아직 결과를 기록하지 않았어요. 잠시 후 이력을 다시 확인해 주세요.'
  if (!receipt.resultCode) return STATUS_LABELS[receipt.status] ?? receipt.status
  return RESULTS[receipt.resultCode] ? `${RESULTS[receipt.resultCode]} (${receipt.resultCode})` : receipt.resultCode
}
