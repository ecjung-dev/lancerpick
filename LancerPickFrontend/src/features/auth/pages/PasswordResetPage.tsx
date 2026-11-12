import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { resetFailureKind, submitPasswordReset } from '../passwordReset'
import { useLinkToken } from '../useLinkToken'
import { validatePasswordReset, type PasswordResetErrors } from '../validation'
import { AuthLayout } from './AuthLayout'
import type { LoginPageState } from './LoginPage'
import styles from './AuthPage.module.css'
import { useMountedRef } from '../../../shared/lib/useMountedRef'

type Draft = { newPassword: string; newPasswordConfirm: string }

/**
 * /password-reset#token=... — 메일 링크로 여는 새 비밀번호 입력 화면.
 * - 링크를 여는 것(GET)만으로는 아무것도 바뀌지 않는다. 사용자가 "비밀번호 변경"을 눌러야 POST가 한 번 나간다
 *   (메일 보안 검사기가 링크를 자동으로 열어도 토큰이 쓰이지 않는다).
 * - 토큰은 처음 화면을 그릴 때 주소에서 읽어 화면 상태(메모리)에만 두고, 곧바로 주소창에서 지운다
 *   (방문 기록·화면 공유·주소 복사로 새지 않게). localStorage에 저장하지 않는다.
 * - 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다. 자동 재전송 없음(retry: false).
 * - 통신 오류·5xx는 바뀌었을 수 있다. 같은 토큰을 다시 보내지 않고 로그인 화면에서 새 비밀번호를 먼저 시도하게 안내한다.
 */
export function PasswordResetPage() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const token = useLinkToken()   // 주소의 #token=... 을 메모리로 옮기고 주소창에서 지운다
  const active = useMountedRef()
  const sending = useRef(false)

  const [draft, setDraft] = useState<Draft>({ newPassword: '', newPasswordConfirm: '' })
  const [errors, setErrors] = useState<PasswordResetErrors>({})
  const [message, setMessage] = useState<string>()
  const [linkInvalid, setLinkInvalid] = useState(token === null)

  function goToLogin(passwordReset: LoginPageState['passwordReset']) {
    const state: LoginPageState = { passwordReset }
    navigate('/login', { replace: true, state })
  }

  const reset = useMutation({
    retry: false, // 토큰은 일회용이다. 절대 자동으로 다시 보내지 않는다
    mutationFn: (newPassword: string) => submitPasswordReset(client, { token: token ?? '', newPassword }),
    onSettled: () => { sending.current = false },
    onSuccess: () => goToLogin('done'),
    onError: (failure) => {
      const kind = resetFailureKind(failure)
      if (kind === 'unclear') { goToLogin('unclear'); return }
      if (!active.current) return
      if (kind === 'invalidLink') { setLinkInvalid(true); return }
      if (kind === 'fields') {
        setErrors({ newPassword: '영문과 숫자를 포함해 8~64자로 입력해 주세요. 공백·한글은 쓸 수 없어요.' })
        return
      }
      if (kind === 'csrf') { setMessage('요청 정보가 만료됐어요. 다시 시도해 주세요.'); return }
      setMessage('비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.')
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (reset.isPending || sending.current || token === null || linkInvalid) return
    const result = validatePasswordReset(draft)
    setErrors(result.errors)
    setMessage(undefined)
    if (!result.valid) return
    sending.current = true
    reset.mutate(result.newPassword)
  }

  function update(field: keyof Draft, value: string) {
    setDraft((previous) => ({ ...previous, [field]: value }))
    setErrors({})
    setMessage(undefined)
  }

  if (linkInvalid) {
    return <AuthLayout title="링크를 쓸 수 없어요">
      <p className={styles.error} role="alert">
        유효하지 않거나 만료된 링크예요. 링크는 15분 동안 한 번만 쓸 수 있고, 비밀번호를 바꾸면 이전 링크는 모두 쓸 수 없어요.
      </p>
      <div className={styles.links}>
        <Link to="/password-reset/request">재설정 메일 다시 받기</Link>
        <Link to="/login">로그인</Link>
      </div>
    </AuthLayout>
  }

  return <AuthLayout title="새 비밀번호 설정" description="새 비밀번호를 정하면 모든 기기에서 로그아웃돼요. 새 비밀번호로 다시 로그인해 주세요.">
    {message && <p className={styles.error} role="alert">{message}</p>}
    <form onSubmit={submit} noValidate className={styles.form}>
      <fieldset disabled={reset.isPending}>
        <div className={styles.field}>
          <label htmlFor="reset-new-password">새 비밀번호</label>
          <Input id="reset-new-password" type="password" autoComplete="new-password" value={draft.newPassword}
            aria-invalid={!!errors.newPassword} aria-describedby="reset-new-password-help reset-new-password-error"
            onChange={(e) => update('newPassword', e.target.value)} />
          <p id="reset-new-password-help" className={styles.help}>영문과 숫자를 포함해 8~64자. 공백·한글은 쓸 수 없어요.</p>
          <p id="reset-new-password-error" className={styles.fieldError}>{errors.newPassword}</p>
        </div>
        <div className={styles.field}>
          <label htmlFor="reset-new-password-confirm">새 비밀번호 확인</label>
          <Input id="reset-new-password-confirm" type="password" autoComplete="new-password" value={draft.newPasswordConfirm}
            aria-invalid={!!errors.newPasswordConfirm} aria-describedby="reset-new-password-confirm-error"
            onChange={(e) => update('newPasswordConfirm', e.target.value)} />
          <p id="reset-new-password-confirm-error" className={styles.fieldError}>{errors.newPasswordConfirm}</p>
        </div>
        <Button type="submit" className={styles.submit}>{reset.isPending ? '변경 중…' : '비밀번호 변경'}</Button>
      </fieldset>
    </form>
  </AuthLayout>
}
