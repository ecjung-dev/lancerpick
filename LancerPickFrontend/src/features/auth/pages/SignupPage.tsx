import { useState, type FormEvent, type ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { codePointLength, normalizeText } from '../../../shared/lib/text'
import { signup } from '../api'
import { useCurrentMember } from '../queries'
import type { SignupInput } from '../types'
import { validateSignup, type SignupErrors } from '../validation'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

type FieldName = keyof SignupErrors

export function SignupPage() {
  const navigate = useNavigate()
  const current = useCurrentMember()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [errors, setErrors] = useState<SignupErrors>({})

  const submit = useMutation({
    retry: false, // 가입 요청은 자동으로 다시 보내지 않는다(중복 가입 방지)
    mutationFn: (input: SignupInput) => signup(input),
    // 가입 후 자동 로그인하지 않고 로그인 화면으로 보낸다(API 2절).
    onSuccess: (member) => navigate('/login', { replace: true, state: { signedUpEmail: member.email } }),
    onError: (error) => setErrors(serverFieldErrors(error)),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const result = validateSignup({ email, password, passwordConfirm, displayName })
    setErrors(result.errors)
    if (result.valid && !submit.isPending) submit.mutate(result.input)
  }

  function change(setter: (value: string) => void, field: FieldName) {
    return (value: string) => {
      setter(value)
      setErrors((previous) => ({ ...previous, [field]: undefined }))
      submit.reset()
    }
  }

  if (current.data) {
    return <AuthLayout title="이미 로그인되어 있어요">
      <p className={styles.notice}>다른 계정을 만들려면 먼저 로그아웃해 주세요.</p>
      <div className={styles.links}><Link to="/">서비스 둘러보기</Link></div>
    </AuthLayout>
  }

  const generalError = submit.isError && Object.keys(serverFieldErrors(submit.error)).length === 0
  return <AuthLayout title="회원가입" description="가입하면 테스트 적립금 1,000,000원이 지급돼요. 실제 현금 거래는 없어요.">
    {generalError && <p className={styles.error} role="alert">{signupErrorMessage(submit.error)}
      {submit.error instanceof ApiError && submit.error.traceId && <small>문의 코드: {submit.error.traceId}</small>}</p>}
    <form onSubmit={onSubmit} noValidate className={styles.form}>
      <fieldset disabled={submit.isPending}>
        <Field id="signup-email" label="이메일" error={errors.email} help="로그인 아이디로 사용해요.">
          <Input id="signup-email" type="email" autoComplete="email" value={email} aria-invalid={!!errors.email}
            aria-describedby="signup-email-help signup-email-error" onChange={(e) => change(setEmail, 'email')(e.target.value)} />
        </Field>
        <Field id="signup-password" label="비밀번호" error={errors.password} help="영문과 숫자를 포함해 8~64자. 특수문자는 선택이에요.">
          <Input id="signup-password" type="password" autoComplete="new-password" value={password} aria-invalid={!!errors.password}
            aria-describedby="signup-password-help signup-password-error" onChange={(e) => change(setPassword, 'password')(e.target.value)} />
        </Field>
        <Field id="signup-password-confirm" label="비밀번호 확인" error={errors.passwordConfirm}>
          <Input id="signup-password-confirm" type="password" autoComplete="new-password" value={passwordConfirm}
            aria-invalid={!!errors.passwordConfirm} aria-describedby="signup-password-confirm-error"
            onChange={(e) => change(setPasswordConfirm, 'passwordConfirm')(e.target.value)} />
        </Field>
        <Field id="signup-name" label="표시 이름" error={errors.displayName}
          help={`다른 회원에게 보이는 이름이에요. ${codePointLength(normalizeText(displayName))} / 30자`}>
          <Input id="signup-name" autoComplete="nickname" value={displayName} aria-invalid={!!errors.displayName}
            aria-describedby="signup-name-help signup-name-error" onChange={(e) => change(setDisplayName, 'displayName')(e.target.value)} />
        </Field>
        <Button type="submit" className={styles.submit}>{submit.isPending ? '가입 중…' : '가입하기'}</Button>
      </fieldset>
    </form>
    <p className={styles.switch}>이미 회원이신가요? <Link to="/login">로그인</Link></p>
  </AuthLayout>
}

function Field({ id, label, error, help, children }: {
  id: string; label: string; error?: string; help?: string; children: ReactElement
}) {
  return <div className={styles.field}>
    <label htmlFor={id}>{label} <span>필수</span></label>
    {children}
    {help && <p id={`${id}-help`} className={styles.help}>{help}</p>}
    <p id={`${id}-error`} className={styles.fieldError}>{error}</p>
  </div>
}

/** 서버 응답을 입력칸별 오류로 바꾼다. 409 중복 이메일, 400 fieldErrors(password 등). */
function serverFieldErrors(error: Error): SignupErrors {
  if (!(error instanceof ApiError)) return {}
  if (error.code === 'EMAIL_ALREADY_EXISTS') return { email: '이미 가입된 이메일이에요.' }
  const errors: SignupErrors = {}
  for (const field of error.fieldErrors) {
    if (field.field === 'email') errors.email = '이메일 형식을 확인해 주세요.'
    if (field.field === 'password') errors.password = '영문과 숫자를 포함해 8~64자로 입력해 주세요.'
    if (field.field === 'displayName') errors.displayName = '표시 이름은 1~30자여야 해요.'
  }
  return errors
}

function signupErrorMessage(error: Error) {
  if (!(error instanceof ApiError)) return '가입하지 못했어요. 다시 시도해 주세요.'
  // 가입이 이미 처리됐을 수 있다. 다시 누르면 409가 날 수 있어 로그인을 먼저 안내한다.
  if (error.status === 0 || error.status >= 500) return '가입 결과를 확인하지 못했어요. 가입됐을 수 있으니 먼저 같은 이메일로 로그인해 보세요.'
  if (error.code === 'VALIDATION_FAILED') return '입력값을 다시 확인해 주세요.'
  if (error.code === 'CSRF_INVALID') return '요청 정보가 만료됐어요. 다시 시도해 주세요.'
  return error.message
}
