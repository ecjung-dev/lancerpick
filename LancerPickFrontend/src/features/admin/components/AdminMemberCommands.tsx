import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { captureAccountScope } from '../../auth/session'
import { formatDateTime } from '../../../shared/lib/format'
import { submitMemberSanction } from '../memberCommands'
import { usePendingMemberCommand } from '../memberQueries'
import { koreaEndTime, koreaInputValue, restrictionLabels } from '../memberValidation'
import { validateReason } from '../validation'
import type {
  AdminMemberDetail,
  MemberSanctionCommand,
  SanctionKind,
} from '../memberTypes'
import styles from '../pages/Admin.module.css'
import memberStyles from './AdminMemberCommands.module.css'

const koreaInput = (iso: string) => koreaInputValue(Date.parse(iso))

export function AdminMemberCommands({
  detail,
  stale,
}: {
  detail: AdminMemberDetail
  stale: boolean
}) {
  const client = useQueryClient()
  const pending = usePendingMemberCommand(detail.member.id)
  const [action, setAction] = useState('apply')
  const [kind, setKind] = useState<SanctionKind>('GENERAL_RESTRICTION')
  const [end, setEnd] = useState('')
  const [reason, setReason] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState<string>()
  const [success, setSuccess] = useState(false)
  const active = useRef(true),
    sending = useRef(false)
  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
    }
  }, [])
  const stored = pending?.command
  const currentAction = stored
    ? stored.action === 'apply'
      ? 'apply'
      : `revoke:${stored.sanctionId}`
    : action
  const currentKind = stored?.action === 'apply' ? stored.kind : kind
  const currentEnd =
    stored?.action === 'apply' ? koreaInput(stored.endsAt) : end
  const currentReason = stored?.reason ?? reason
  const busy = pending?.phase === 'sending'

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (sending.current || busy || stale || !detail.canManage) return
    setError(undefined)
    setSuccess(false)
    let command: MemberSanctionCommand | undefined
    if (!stored) {
      const validated = validateReason(reason)
      if (validated.error) {
        setError(validated.error)
        return
      }
      if (!confirmed) {
        setError('처리 영향을 확인해 주세요.')
        return
      }
      if (action === 'apply') {
        const date = koreaEndTime(end, detail.serverTime)
        if (!date.endsAt) {
          setError(date.error)
          return
        }
        command = {
          action: 'apply',
          kind,
          endsAt: date.endsAt,
          reason: validated.reason,
          requestKey: crypto.randomUUID(),
        }
      } else {
        const id = action.slice('revoke:'.length)
        if (!detail.activeSanctions.some((sanction) => sanction.id === id)) {
          setError('현재 적용 중인 제재를 다시 확인해 주세요.')
          return
        }
        command = { action: 'revoke', sanctionId: id, reason: validated.reason }
      }
    }
    const scope = captureAccountScope(client)
    sending.current = true
    try {
      await submitMemberSanction(client, detail.member.id, command, scope)
      if (!active.current || !scope()) return
      setSuccess(true)
      setReason('')
      setEnd('')
      setConfirmed(false)
      setAction('apply')
    } catch (failure) {
      if (active.current && scope())
        setError(
          failure instanceof Error
            ? failure.message
            : '처리 결과를 확인해 주세요.',
        )
    } finally {
      sending.current = false
    }
  }

  if (!detail.canManage)
    return (
      <p className={styles.notice}>
        관리자 계정과 본인 계정은 이 화면에서 제재할 수 없습니다.
      </p>
    )
  return (
    <form
      className={`${styles.form} ${memberStyles.form}`}
      onSubmit={(event) => {
        void submit(event)
      }}
      noValidate
    >
      <h2>제재 적용·해제</h2>
      <p>
        일반 이용 제한은 신규 주문·상품 등록·주문 수락을 막고 기존 거래 처리는
        허용합니다. 보안 잠금은 로그인·기존 세션을 차단하며 진행 중 거래는
        관리자 확인이 필요합니다.
      </p>
      <p>
        동일 종류를 새로 적용하면 기존 제재를 해제하고 새 기간·사유로
        교체합니다. 서로 다른 종류는 함께 유지됩니다. 제재로 주문 취소·환불·수익
        회수는 하지 않습니다.
      </p>
      {success && (
        <p className={styles.success} role="status">
          처리했습니다. 현재 상태와 이력을 확인해 주세요.
        </p>
      )}
      {pending?.phase === 'uncertain' && (
        <p className={styles.notice} role="alert">
          서버가 처리했을 수 있어요. 아래 원래 요청을 유지한 채 ‘같은 요청으로
          결과 확인’을 눌러 주세요. 화면을 이동해도 같은 로그인 상태에서는
          요청을 보존합니다.
        </p>
      )}
      {error && (
        <p className={styles.fieldError} role="alert">
          {error}
        </p>
      )}
      <fieldset disabled={!!pending || stale}>
        <label htmlFor="member-command-action">처리 종류</label>
        <select
          id="member-command-action"
          value={currentAction}
          onChange={(event) => setAction(event.target.value)}
        >
          <option value="apply">새 제재 적용</option>
          {detail.activeSanctions.map((sanction) => (
            <option key={sanction.id} value={`revoke:${sanction.id}`}>
              {restrictionLabels[sanction.kind]} 해제 · #{sanction.id}
            </option>
          ))}
          {stored?.action === 'revoke' &&
            !detail.activeSanctions.some(
              (sanction) => sanction.id === stored.sanctionId,
            ) && (
              <option value={currentAction}>
                제재 #{stored.sanctionId} 해제 결과 확인
              </option>
            )}
        </select>
        {currentAction === 'apply' && (
          <>
            <label htmlFor="member-sanction-kind">제재 종류</label>
            <select
              id="member-sanction-kind"
              value={currentKind}
              onChange={(event) => setKind(event.target.value as SanctionKind)}
            >
              <option value="GENERAL_RESTRICTION">일반 이용 제한</option>
              <option value="SECURITY_LOCK">보안 잠금</option>
            </select>
            <label htmlFor="member-sanction-end">종료 일시 (한국 시간)</label>
            <input
              id="member-sanction-end"
              type="datetime-local"
              value={currentEnd}
              onChange={(event) => setEnd(event.target.value)}
            />
            <p className={styles.help}>
              조회 기준 서버 시각: {formatDateTime(detail.serverTime)}. 만료 시
              제한은 자동 해제되며 폐기된 로그인 세션은 복원되지 않습니다.
            </p>
          </>
        )}
        <label htmlFor="member-sanction-reason">처리 사유</label>
        <textarea
          id="member-sanction-reason"
          value={currentReason}
          onChange={(event) => setReason(event.target.value)}
        />
        <label className={memberStyles.confirm}>
          <input
            type="checkbox"
            checked={stored ? true : confirmed}
            onChange={(event) => setConfirmed(event.target.checked)}
          />{' '}
          대상 회원과 처리 영향을 확인했습니다.
        </label>
      </fieldset>
      <button type="submit" disabled={busy || stale || (!stored && !confirmed)}>
        {busy
          ? '처리 중…'
          : stored
            ? '같은 요청으로 결과 확인'
            : currentAction === 'apply'
              ? '제재 적용'
              : '제재 해제'}
      </button>
    </form>
  )
}
