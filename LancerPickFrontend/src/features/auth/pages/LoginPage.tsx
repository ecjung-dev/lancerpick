import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { login } from '../api'
import { useCurrentMember } from '../queries'
import { isUnclearResult, recheckSession, resetAccountState, safeNextPath } from '../session'
import type { LoginInput } from '../types'
import { validateLogin, type LoginErrors } from '../validation'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

/** 가입 화면이 navigate('/login', { state })로 넘겨주는 값 */
interface SignupState { signedUpEmail?: string }

export function LoginPage() {
  const location = useLocation()
  const signedUpEmail = (location.state as SignupState | null)?.signedUpEmail
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const navigate = useNavigate()
  const client = useQueryClient()
  const current = useCurrentMember()

  const [email, setEmail] = useState(signedUpEmail ?? '')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<LoginErrors>({})

  const submit = useMutation({
    retry: false, // 로그인 요청은 자동으로 다시 보내지 않는다
    mutationFn: (input: LoginInput) => login(input),
    onSuccess: async (member) => {
      await resetAccountState(client, member)
      navigate(next, { replace: true })
    },
    // 결과가 불명확하면 실제 로그인 상태를 다시 확인한다. 로그인됐다면 '이미 로그인' 화면으로 바뀐다.
    onError: async (error) => {
      if (!isUnclearResult(error)) return
      // 서버에서는 로그인이 됐을 수 있다. 다시 확인해서 로그인돼 있으면 원래 가려던 곳으로 보낸다.
      const outcome = await recheckSession(client)
      if (outcome === 'changed' || outcome === 'same') navigate(next, { replace: true })
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const result = validateLogin({ email, password })
    setErrors(result.errors)
    if (result.valid && !submit.isPending) submit.mutate(result.input)
  }

  if (current.data && !submit.isPending && !submit.isSuccess) {
    return <AuthLayout title="이미 로그인되어 있어요">
      <p className={styles.notice}><strong>{current.data.displayName}</strong>님으로 로그인한 상태예요.</p>
      <div className={styles.links}><Link to="/">서비스 둘러보기</Link><Link to="/mypage">마이페이지</Link></div>
    </AuthLayout>
  }

  return <AuthLayout title="로그인" description="이메일과 비밀번호로 로그인해 주세요.">
    {signedUpEmail && <p className={styles.success} role="status">가입이 완료됐어요. 로그인해 주세요.</p>}
    {submit.isError && <p className={styles.error} role="alert">{loginErrorMessage(submit.error)}
      {submit.error instanceof ApiError && submit.error.traceId && <small>문의 코드: {submit.error.traceId}</small>}</p>}
    <form onSubmit={onSubmit} noValidate className={styles.form}>
      <fieldset disabled={submit.isPending}>
        <div className={styles.field}>
          <label htmlFor="login-email">이메일</label>
          <Input id="login-email" type="email" autoComplete="username" value={email} aria-invalid={!!errors.email}
            aria-describedby="login-email-error" onChange={(e) => { setEmail(e.target.value); setErrors({}); submit.reset() }} />
          <p id="login-email-error" className={styles.fieldError}>{errors.email}</p>
        </div>
        <div className={styles.field}>
          <label htmlFor="login-password">비밀번호</label>
          <Input id="login-password" type="password" autoComplete="current-password" value={password} aria-invalid={!!errors.password}
            aria-describedby="login-password-error" onChange={(e) => { setPassword(e.target.value); setErrors({}); submit.reset() }} />
          <p id="login-password-error" className={styles.fieldError}>{errors.password}</p>
        </div>
        <Button type="submit" className={styles.submit}>{submit.isPending ? '로그인 중…' : '로그인'}</Button>
      </fieldset>
    </form>
    <p className={styles.switch}>아직 회원이 아니신가요? <Link to="/signup">회원가입</Link></p>
  </AuthLayout>
}

/** 화면은 문구가 아니라 code로 판단한다(개발 규칙 5절). */
function loginErrorMessage(error: Error) {
  if (!(error instanceof ApiError)) return '로그인하지 못했어요. 다시 시도해 주세요.'
  if (isUnclearResult(error)) return '로그인 결과를 확인하지 못했어요. 로그인 상태를 다시 확인하고 있어요.'
  if (error.code === 'INVALID_CREDENTIALS') return '이메일 또는 비밀번호가 올바르지 않아요.'
  if (error.code === 'CSRF_INVALID') return '요청 정보가 만료됐어요. 다시 로그인해 주세요.'
  if (error.code === 'VALIDATION_FAILED') return '이메일과 비밀번호를 입력해 주세요.'
  return error.message
}
