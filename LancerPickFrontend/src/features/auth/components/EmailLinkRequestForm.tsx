import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { MAIL_REQUEST_COOLDOWN_SECONDS } from '../linkToken'
import { isUnclearResult } from '../session'
import { validatePasswordResetRequest } from '../validation'
import styles from '../pages/AuthPage.module.css'

/**
 * 이메일을 받아 링크 메일을 요청하는 공통 폼(비밀번호 재설정 요청·가입 인증 메일 요청).
 * - 가입 여부와 상관없이 같은 안내를 보여준다(서버도 같은 202). "가입되지 않은/이미 가입된 이메일"이라고 알려주지 않는다.
 * - 요청은 한 번만 보낸다(retry: false). 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다.
 * - 보낸 뒤 60초 동안 다시 보내기를 막는다. 결과가 불명확(통신 오류·5xx)해도 서버가 처리했을 수 있으니 똑같이 막는다.
 * - 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않는다.
 */
export function EmailLinkRequestForm({ idPrefix, request, submitLabel, successText, autoComplete = 'email' }: {
  idPrefix: string
  request: (email: string) => Promise<unknown>
  submitLabel: string
  successText: string
  /** 기존 계정(재설정)은 비밀번호 관리 프로그램이 알아보도록 'username', 새 가입은 'email' */
  autoComplete?: 'email' | 'username'
}) {
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
    mutationFn: (value: string) => request(value),
    onSettled: () => { sending.current = false },
    onSuccess: () => {
      if (!active.current) return
      setCooldown(MAIL_REQUEST_COOLDOWN_SECONDS)
      setMessage({ kind: 'success', text: successText })
    },
    onError: (failure) => {
      if (!active.current) return
      if (isUnclearResult(failure)) {
        setCooldown(MAIL_REQUEST_COOLDOWN_SECONDS)
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
  const inputId = `${idPrefix}-email`

  return <>
    {message && <p className={message.kind === 'success' ? styles.success : styles.error}
      role={message.kind === 'success' ? 'status' : 'alert'}>{message.text}</p>}
    <form onSubmit={submit} noValidate className={styles.form}>
      <fieldset disabled={send.isPending}>
        <div className={styles.field}>
          <label htmlFor={inputId}>이메일</label>
          <Input id={inputId} type="email" autoComplete={autoComplete} value={email} aria-invalid={!!fieldError}
            aria-describedby={`${inputId}-error`}
            onChange={(e) => { setEmail(e.target.value); setFieldError(undefined) }} />
          <p id={`${inputId}-error`} className={styles.fieldError}>{fieldError}</p>
        </div>
        <Button type="submit" className={styles.submit} disabled={waiting}>
          {send.isPending ? '요청 중…' : waiting ? `${cooldown}초 후 다시 요청할 수 있어요` : submitLabel}
        </Button>
      </fieldset>
    </form>
  </>
}
