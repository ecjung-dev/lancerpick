import { ApiError } from '../../shared/api/client.ts'

/** 서버가 이미 처리했을 수 있는 실패의 안내. 재시도를 권하지 않고 상태 확인을 먼저 요청한다. */
export const UNCLEAR_ORDER_RESULT =
  '처리 결과를 확인하지 못했어요. 서버에서 이미 처리됐을 수 있으니 다시 요청하기 전에 현재 상태를 확인해 주세요.'

/**
 * 주문 변경 명령의 실패 안내.
 * 통신 오류(status 0)·5xx·예상 밖 오류는 저장 후 응답만 유실됐을 수 있어 실패로 단정하지 않는다(개발 규칙 4절).
 */
export function orderCommandFailureMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.status === 0 || error.status >= 500) {
    return UNCLEAR_ORDER_RESULT
  }
  return `${error.message} (${error.code})`
}
