import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types.ts'
import { isValidProductId as isValidId } from './validation.ts'

/**
 * 관리자 알림 처리 실패 조회(K6, 조회만). ID는 문자열, 시각은 UTC다.
 * 재처리 버튼은 없다(관리자 작업 감사 이력 확정 뒤 별도 연결, admin-action-audit-design.md).
 */
export type OutboxIssueStatus = 'FAILED' | 'RETRYING'

interface OutboxIssue {
  id: string; eventId: string; type: string; aggregateType: string; aggregateId: string
  status: OutboxIssueStatus; attemptCount: number; lastErrorCode: string | null
  nextAttemptAt: string | null; createdAt: string
}

export interface OutboxFilter { status: OutboxIssueStatus; orderId: string }

/** URL에서 조건을 읽는다. 허용하지 않는 값은 기본값(FAILED·주문 조건 없음)으로 바꿔 서버 400을 일으키지 않는다. */
export function readOutboxFilter(params: URLSearchParams): OutboxFilter {
  const orderId = params.get('orderId') ?? ''
  return {
    status: params.get('status') === 'RETRYING' ? 'RETRYING' : 'FAILED',
    orderId: isValidId(orderId) ? orderId : '',
  }
}

export function getOutboxIssues(filter: OutboxFilter, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20', status: filter.status })
  if (filter.orderId) params.set('orderId', filter.orderId)
  return apiRequest<PageResponse<OutboxIssue>>(`/admin/outbox-events?${params}`, { signal })
}

/** 처리기가 계속 상태를 바꾸므로 매번 최신으로 조회하고 보관하지 않는다. ['admin'] 캐시는 계정 전환 때 지워진다. */
const outboxKeys = { list: (filter: OutboxFilter, page: number) => ['admin', 'outbox', filter, page] as const }

export const useOutboxIssues = (filter: OutboxFilter, page: number) =>
  useQuery({ queryKey: outboxKeys.list(filter, page), queryFn: ({ signal }) => getOutboxIssues(filter, page, signal), retry: false, staleTime: 0, gcTime: 0 })

/** event-contract.md의 고정 오류 코드. 처음 보는 값(과거 기록 등)은 원래 값을 그대로 보여 준다. */
const errorLabels: Record<string, string> = {
  UNSUPPORTED_EVENT: '지원하지 않는 이벤트 형식',
  INVALID_PAYLOAD: '이벤트 내용 형식 오류',
  INVALID_RECIPIENT: '받는 사람이 주문 당사자가 아님',
  AGGREGATE_NOT_FOUND: '대상 주문 없음',
  LOCK_UNAVAILABLE: '잠금 대기 시간 초과',
  PROCESSING_FAILED: '처리 중 오류',
}

/** 다시 시도해도 성공할 수 없어 첫 실패에서 바로 멈추는 오류. */
const permanent = new Set(['UNSUPPORTED_EVENT', 'INVALID_PAYLOAD', 'INVALID_RECIPIENT', 'AGGREGATE_NOT_FOUND'])

export function outboxErrorLabel(code: string | null) {
  if (!code) return '기록 없음'
  const label = errorLabels[code]
  if (!label) return code
  return `${label}${permanent.has(code) ? ' · 재시도해도 같은 결과' : ''} (${code})`
}
