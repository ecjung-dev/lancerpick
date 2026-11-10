import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { captureAccountScope } from '../../auth/session'
import {
  ACTION_LABELS, STATUS_LABELS, clearReprocessResult, resultLabel, submitReprocess, usePendingReprocess,
  type ReprocessAction,
} from '../reprocessApi'
import { REASON_MAX, validateReason } from '../validation'
import styles from '../pages/Admin.module.css'

const BUTTONS: Record<ReprocessAction, string> = {
  TOPUP_RECHECK: '재확인 요청', TOPUP_RECOVERY_RESTART: '재시작 요청', CONTRACT_PDF_REGENERATE: 'PDF 다시 만들기 요청',
}
const EFFECTS: Record<ReprocessAction, string> = {
  TOPUP_RECHECK: '저장된 결제 정보로 결제사에 한 번 조회해요. 새 결제 승인·잔액 직접 수정은 하지 않아요.',
  TOPUP_RECOVERY_RESTART: '실패·중지된 복구 작업을 같은 조건으로 다시 시작해요. 접수돼도 작업이 바로 끝나는 것은 아니에요.',
  CONTRACT_PDF_REGENERATE: '현재 계약의 PDF를 다시 만들어요. 이미 완성·서명된 파일은 바꾸지 않아요.',
}

/**
 * K6 재처리 실행 칸. 조회 결과의 "후보"일 때만 새 요청을 받는다(최종 판단은 서버가 다시 한다).
 * 요청 키는 버튼을 누를 때 한 번 만든다. 결과가 불명확하면 같은 키·같은 사유로만 다시 보낼 수 있다.
 */
export function AdminReprocessPanel({ action, target, eligible, version }: {
  action: ReprocessAction; target: string; eligible: boolean; version?: number
}) {
  const client = useQueryClient()
  const pending = usePendingReprocess(action, target)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  const active = useRef(true)
  const sending = useRef(false)
  const id = useId()
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])

  if (!eligible && !pending) return null
  const busy = pending?.phase === 'sending'
  const uncertain = pending?.phase === 'uncertain'
  const receipt = pending?.phase === 'done' ? pending.receipt : undefined

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (sending.current || busy) return
    setError(undefined)
    let input
    if (!uncertain) {
      const checked = validateReason(reason)
      if (checked.error) { setError(checked.error); return }
      input = { action, target, version, reason: checked.reason, requestKey: crypto.randomUUID() }
    }
    const scope = captureAccountScope(client)
    sending.current = true
    try {
      await submitReprocess(client, action, target, input, scope)
      if (active.current && scope()) setReason('')
    } catch (failure) {
      if (active.current && scope())
        setError(failure instanceof Error ? failure.message : '처리 결과를 확인해 주세요.')
    } finally {
      sending.current = false
    }
  }

  if (receipt) {
    return <div className={styles.notice} role="status">
      <p><strong>{ACTION_LABELS[action]} · {STATUS_LABELS[receipt.status] ?? receipt.status}</strong></p>
      <p>{resultLabel(receipt)}</p>
      <small>요청 #{receipt.id} · 사유: {receipt.reason}</small>
      {eligible && <div className={styles.actions}>
        <button type="button" onClick={() => clearReprocessResult(client, action, target)}>새 요청 작성</button>
      </div>}
    </div>
  }

  return <details className={styles.reprocess} open={!!pending}>
    <summary>{ACTION_LABELS[action]}</summary>
    <form onSubmit={(event) => { void submit(event) }} noValidate>
      <p className={styles.help}>{EFFECTS[action]} 실행 직전에 서버가 조건을 다시 확인해요.</p>
      {uncertain && <p className={styles.notice} role="alert">
        서버가 이미 처리했을 수 있어요. 새로 요청하지 말고 ‘같은 요청으로 결과 확인’을 눌러 주세요. 같은 요청은 다시 실행되지 않아요.
      </p>}
      {error && <p className={styles.fieldError} role="alert">{error}</p>}
      <label htmlFor={`${id}-reason`}>처리 사유 <span>필수 · 최대 {REASON_MAX}자</span></label>
      <textarea id={`${id}-reason`} value={pending ? pending.command.reason : reason} disabled={!!pending}
        onChange={(event) => { setReason(event.target.value); setError(undefined) }} />
      <div className={styles.actions}>
        <button type="submit" disabled={busy}>{busy ? '처리 중…' : uncertain ? '같은 요청으로 결과 확인' : BUTTONS[action]}</button>
      </div>
    </form>
  </details>
}
