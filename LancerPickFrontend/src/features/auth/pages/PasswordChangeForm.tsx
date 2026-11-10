import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { ApiError, isUnauthorized } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { submitPasswordChange } from '../password'
import { isUnclearResult, recheckSession, resetAccountState } from '../session'
import type { ChangePasswordInput, Member } from '../types'
import { validatePasswordChange, type PasswordChangeErrors } from '../validation'
import type { LoginPageState } from './LoginPage'
import styles from './MyProfilePage.module.css'

type Draft = { currentPassword: string; newPassword: string; newPasswordConfirm: string }

const EMPTY_DRAFT: Draft = { currentPassword: '', newPassword: '', newPasswordConfirm: '' }

/**
 * 내 정보 화면의 비밀번호 변경(API 2절 POST /me/password). 성공하면 로그인이 끝나고 로그인 화면으로 이동한다.
 *
 * 실패·동시 요청·늦은 응답 처리:
 * - 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다. 실패해도 자동으로 다시 보내지 않는다(retry: false).
 * - 비밀번호 값은 trim하지 않고, 화면 상태(메모리)에만 둔다. 저장소·주소·로그에 남기지 않는다.
 * - 401: 이미 다른 곳에서 비밀번호가 바뀌었거나 로그인이 끝났다. 캐시를 정리하면 화면이 로그인 안내로 바뀐다.
 * - 통신 오류·5xx: 바뀌었을 수 있으므로 실패로 단정하지 않고 로그인 상태를 다시 확인한다.
 *   로그인이 끝났으면(signedOut) 서버가 변경 후 세션을 끊은 것일 수 있다 → 로그인 화면에서 새 비밀번호를 먼저 시도하도록 안내한다.
 *   같은 계정으로 그대로면(same) 비밀번호는 바뀌지 않은 것이다(바뀌었다면 이 세션도 끊긴다).
 * - 응답이 화면을 떠난 뒤 도착하면 오류 문구는 바꾸지 않는다. 성공했다면 로그인이 끝났으므로 로그인 화면으로 보낸다.
 */
export function PasswordChangeForm({ member, onAccountChanged }: { member: Member; onAccountChanged: () => void }) {
  const client = useQueryClient()
  const navigate = useNavigate()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT)
  const [errors, setErrors] = useState<PasswordChangeErrors>({})
  const [message, setMessage] = useState<string>()

  function goToLogin(passwordChanged: LoginPageState['passwordChanged']) {
    const state: LoginPageState = { passwordChanged }
    navigate('/login', { replace: true, state })
  }

  const change = useMutation({
    retry: false,
    mutationFn: (input: ChangePasswordInput) => submitPasswordChange(client, member.id, input),
    onSettled: () => { sending.current = false },
    onSuccess: (outcome) => {
      if (outcome.kind === 'accountChanged') { onAccountChanged(); return }
      if (outcome.kind === 'changed') goToLogin('done')
      // signedOut: 로그인 상태가 null로 바뀌어 화면이 로그인 안내로 바뀐다.
    },
    onError: async (failure) => {
      if (isUnauthorized(failure)) {
        await resetAccountState(client, null)
        return
      }
      if (isUnclearResult(failure)) {
        const outcome = await recheckSession(client)
        if (outcome === 'signedOut') { goToLogin('unclear'); return }
        if (outcome === 'changed') { onAccountChanged(); return }
        if (!active.current) return
        setMessage(outcome === 'same'
          ? '비밀번호가 바뀌지 않았어요. 잠시 후 다시 시도해 주세요.'
          : '변경 결과를 확인하지 못했어요. 잠시 후 로그인 상태를 확인해 주세요.')
        return
      }
      if (!active.current) return
      showFailure(failure)
    },
  })

  /** 화면은 문구가 아니라 code로 판단한다(개발 규칙 5절). */
  function showFailure(failure: Error) {
    if (!(failure instanceof ApiError)) {
      setMessage('비밀번호를 바꾸지 못했어요. 다시 시도해 주세요.')
      return
    }
    if (failure.code === 'CURRENT_PASSWORD_INCORRECT') {
      setDraft((previous) => ({ ...previous, currentPassword: '' }))
      setErrors({ currentPassword: '현재 비밀번호가 올바르지 않아요.' })
      return
    }
    if (failure.code === 'PASSWORD_UNCHANGED') {
      setErrors({ newPassword: '현재 비밀번호와 다른 비밀번호를 입력해 주세요.' })
      return
    }
    if (failure.code === 'VALIDATION_FAILED') {
      setErrors(serverFieldErrors(failure))
      return
    }
    if (failure.code === 'CSRF_INVALID') {
      setMessage('요청 정보가 만료됐어요. 다시 시도해 주세요.')
      return
    }
    setMessage(failure.message)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (change.isPending || sending.current) return
    const result = validatePasswordChange(draft)
    setErrors(result.errors)
    setMessage(undefined)
    if (!result.valid) return
    sending.current = true
    change.mutate(result.input)
  }

  function update(field: keyof Draft, value: string) {
    setDraft((previous) => ({ ...previous, [field]: value }))
    setErrors({})
    setMessage(undefined)
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate aria-labelledby="password-change-title">
      <h2 id="password-change-title" className={styles.sectionTitle}>비밀번호 변경</h2>
      <p className={styles.sectionHelp}>바꾸면 모든 기기에서 로그아웃돼요. 새 비밀번호로 다시 로그인해 주세요.</p>
      {message && <p className={styles.notice} role="alert">{message}</p>}
      <fieldset disabled={change.isPending}>
        {/* 비밀번호 관리 프로그램이 어느 계정의 비밀번호인지 알도록 아이디(이메일)를 숨겨서 함께 둔다. */}
        <input type="text" name="username" autoComplete="username" value={member.email} readOnly hidden />
        <div className={styles.field}>
          <label htmlFor="password-current">현재 비밀번호</label>
          <Input
            id="password-current"
            type="password"
            autoComplete="current-password"
            value={draft.currentPassword}
            aria-invalid={!!errors.currentPassword}
            aria-describedby="password-current-error"
            onChange={(e) => update('currentPassword', e.target.value)}
          />
          <p id="password-current-error" className={styles.fieldError}>{errors.currentPassword}</p>
        </div>
        <div className={styles.field}>
          <label htmlFor="password-new">새 비밀번호</label>
          <Input
            id="password-new"
            type="password"
            autoComplete="new-password"
            value={draft.newPassword}
            aria-invalid={!!errors.newPassword}
            aria-describedby="password-new-help password-new-error"
            onChange={(e) => update('newPassword', e.target.value)}
          />
          <p id="password-new-help" className={styles.help}>영문과 숫자를 포함해 8~64자. 공백·한글은 쓸 수 없어요.</p>
          <p id="password-new-error" className={styles.fieldError}>{errors.newPassword}</p>
        </div>
        <div className={styles.field}>
          <label htmlFor="password-new-confirm">새 비밀번호 확인</label>
          <Input
            id="password-new-confirm"
            type="password"
            autoComplete="new-password"
            value={draft.newPasswordConfirm}
            aria-invalid={!!errors.newPasswordConfirm}
            aria-describedby="password-new-confirm-error"
            onChange={(e) => update('newPasswordConfirm', e.target.value)}
          />
          <p id="password-new-confirm-error" className={styles.fieldError}>{errors.newPasswordConfirm}</p>
        </div>
        <div className={styles.actions}>
          <p>비밀번호는 화면에 다시 보여주지 않아요.</p>
          <Button type="submit">{change.isPending ? '변경 중…' : '비밀번호 변경'}</Button>
        </div>
      </fieldset>
    </form>
  )
}

/** 서버 입력 오류(fieldErrors)를 입력칸 안내로 바꾼다. 서버 문구 대신 화면 규칙 문구를 쓴다. */
function serverFieldErrors(failure: ApiError): PasswordChangeErrors {
  const errors: PasswordChangeErrors = {}
  for (const field of failure.fieldErrors) {
    if (field.field === 'currentPassword') errors.currentPassword = '현재 비밀번호를 입력해 주세요.'
    if (field.field === 'newPassword') errors.newPassword = '영문과 숫자를 포함해 8~64자로 입력해 주세요. 공백·한글은 쓸 수 없어요.'
  }
  if (Object.keys(errors).length === 0) errors.newPassword = '입력값을 확인해 주세요.'
  return errors
}
