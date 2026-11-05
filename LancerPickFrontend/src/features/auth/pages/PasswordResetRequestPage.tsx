import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { requestPasswordReset } from '../api'
import { RESET_REQUEST_COOLDOWN_SECONDS } from '../passwordReset'
import { isUnclearResult } from '../session'
import { validatePasswordResetRequest } from '../validation'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

/**
 * /password-reset/request — 비밀번호 재설정 메일 요청.
 * - 가입 여부와 상관없이 같은 안내를 보여준다(서버도 같은 202). "가입되지 않은 이메일"이라고 알려주지 않는다.
 * - 요청은 한 번만 보낸다(retry: false). 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다.
 * - 보낸 뒤 60초 동안 다시 보내기를 막는다. 결과가 불명확(통신 오류·5xx)해도 서버가 처리했을 수 있으니 똑같이 막는다.
 * - 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않는다.
 */
export function PasswordResetRequestPage() {
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])

  const [email, setEmail] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string }>()
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((left) => left - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const send = useMutation({
    retry: false,
    mutationFn: (value: string) => requestPasswordReset(value),
    onSettled: () => { sending.current = false },
    onSuccess: () => {
      if (!active.current) return
      setCooldown(RESET_REQUEST_COOLDOWN_SECONDS)
      setMessage({
        kind: 'success',
        text: '입력한 주소로 안내할 수 있는 경우 비밀번호 재설정 메일을 보냈어요. 링크는 15분 동안 한 번만 쓸 수 있어요. '
          + '메일이 오지 않으면 스팸함을 확인한 뒤 잠시 후 다시 요청해 주세요.',
      })
    },
    onError: (failure) => {
      if (!active.current) return
      if (isUnclearResult(failure)) {
        setCooldown(RESET_REQUEST_COOLDOWN_SECONDS)
        setMessage({ kind: 'error', text: '요청 결과를 확인하지 못했어요. 메일함을 먼저 확인하고, 메일이 없으면 잠시 후 다시 요청해 주세요.' })
        return
      }
      if (failure instanceof ApiError && failure.code === 'VALIDATION_FAILED') {
        setFieldError('이메일 형식을 확인해 주세요.')
        return
      }
      if (failure instanceof ApiError && failure.code === 'CSRF_INVALID') {
        setMessage({ kind: 'error', text: '요청 정보가 만료됐어요. 다시 시도해 주세요.' })
        return
      }
      setMessage({ kind: 'error', text: '요청하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (send.isPending || sending.current || cooldown > 0) return
    const result = validatePasswordResetRequest({ email })
    setFieldError(result.error)
    setMessage(undefined)
    if (!result.valid) return
    sending.current = true
    send.mutate(result.email)
  }

  const waiting = cooldown > 0

  return <AuthLayout title="비밀번호 재설정" description="가입한 이메일을 입력하면 비밀번호를 다시 설정할 수 있는 링크를 보내드려요.">
    {message && <p className={message.kind === 'success' ? styles.success : styles.error}
      role={message.kind === 'success' ? 'status' : 'alert'}>{message.text}</p>}
    <form onSubmit={submit} noValidate className={styles.form}>
      <fieldset disabled={send.isPending}>
        <div className={styles.field}>
          <label htmlFor="reset-email">이메일</label>
          <Input id="reset-email" type="email" autoComplete="username" value={email} aria-invalid={!!fieldError}
            aria-describedby="reset-email-error"
            onChange={(e) => { setEmail(e.target.value); setFieldError(undefined) }} />
          <p id="reset-email-error" className={styles.fieldError}>{fieldError}</p>
        </div>
        <Button type="submit" className={styles.submit} disabled={waiting}>
          {send.isPending ? '요청 중…' : waiting ? `${cooldown}초 후 다시 요청할 수 있어요` : '재설정 메일 받기'}
        </Button>
      </fieldset>
    </form>
    <p className={styles.switch}>비밀번호가 기억나셨나요? <Link to="/login">로그인</Link></p>
  </AuthLayout>
}
