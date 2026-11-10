import { useEffect, useRef, useState, type FormEvent, type ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { normalizedLength } from '../../../shared/lib/text'
import { signup, verifySignupEmail } from '../api'
import { useCurrentMember } from '../queries'
import { signupFailureKind, verifyFailureKind } from '../signupEmail'
import type { SignupEmailVerification, SignupInput } from '../types'
import { useLinkToken } from '../useLinkToken'
import { validateSignup, type SignupErrors } from '../validation'
import { AuthLayout } from './AuthLayout'
import type { LoginPageState } from './LoginPage'
import styles from './AuthPage.module.css'

type FieldName = 'password' | 'passwordConfirm' | 'displayName'
/** 화면 단계: 인증 전 → (이미 가입됨 | 가입 정보 입력) / 링크 사용 불가 / 인증 만료 */
type Stage = 'verify' | 'details' | 'registered' | 'invalidLink' | 'proofExpired'

/**
 * /signup/verify#token=... — 이메일 인증 가입 2단계(3인 설계 §5.1).
 * 1) 링크를 여는 것만으로는 인증하지 않는다. "인증하기"를 눌러야 POST가 한 번 나간다(메일 보안 검사기의 자동 열람 대비).
 * 2) 인증되면 서버가 가입 증명(proof)을 준다. 증명은 화면 메모리에만 두고, 비밀번호·표시 이름과 함께 가입 요청에 한 번 보낸다.
 *    이미 가입된 이메일이면(본인이 인증한 뒤에만 알려 줌) 로그인·비밀번호 재설정을 안내한다.
 * 3) 가입 성공 → 로그인 화면(자동 로그인 없음).
 * - 토큰은 처음 그릴 때 주소에서 읽어 메모리로 옮기고 주소창에서 지운다(useLinkToken). 증명·토큰을 저장소에 남기지 않는다.
 * - 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다. 자동 재전송 없음(retry: false).
 * - 통신 오류·5xx: 인증은 됐을 수 있으므로 같은 링크를 다시 보내지 않고 새 인증 메일을 안내한다.
 *   가입은 됐을 수 있으므로 다시 보내지 않고 로그인부터 시도하도록 안내한다.
 */
export function SignupVerifyPage() {
  const navigate = useNavigate()
  const current = useCurrentMember()
  const token = useLinkToken()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])

  const [stage, setStage] = useState<Stage>(token === null ? 'invalidLink' : 'verify')
  const [verified, setVerified] = useState<SignupEmailVerification>()
  const [message, setMessage] = useState<string>()
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [errors, setErrors] = useState<SignupErrors>({})

  const verify = useMutation({
    retry: false, // 링크 토큰은 일회용이다. 절대 자동으로 다시 보내지 않는다
    mutationFn: (value: string) => verifySignupEmail(value),
    onSettled: () => { sending.current = false },
    onSuccess: (result) => {
      if (!active.current) return
      setVerified(result)
      setStage(result.alreadyRegistered ? 'registered' : 'details')
    },
    onError: (failure) => {
      if (!active.current) return
      const kind = verifyFailureKind(failure)
      if (kind === 'invalidLink') { setStage('invalidLink'); return }
      if (kind === 'csrf') { setMessage('요청 정보가 만료됐어요. 다시 시도해 주세요.'); return }
      if (kind === 'unclear') {
        setStage('invalidLink')
        setMessage('인증 결과를 확인하지 못했어요. 링크가 이미 사용됐을 수 있으니 인증 메일을 다시 받아 주세요.')
        return
      }
      setMessage('인증하지 못했어요. 잠시 후 다시 시도해 주세요.')
    },
  })

  const submit = useMutation({
    retry: false, // 증명은 일회용이고 가입이 이미 됐을 수 있다. 자동으로 다시 보내지 않는다
    mutationFn: (input: SignupInput) => signup(input),
    onSettled: () => { sending.current = false },
    onSuccess: (member) => {
      const state: LoginPageState = { signedUpEmail: member.email }
      navigate('/login', { replace: true, state })
    },
    onError: (failure) => {
      if (!active.current) return
      const kind = signupFailureKind(failure)
      if (kind === 'proofInvalid') { setStage('proofExpired'); return }
      if (kind === 'alreadyRegistered') { setStage('registered'); return }
      if (kind === 'fields') { setErrors(serverFieldErrors(failure)); return }
      if (kind === 'csrf') { setMessage('요청 정보가 만료됐어요. 다시 시도해 주세요.'); return }
      if (kind === 'unclear') {
        setMessage('가입 결과를 확인하지 못했어요. 가입됐을 수 있으니 먼저 같은 이메일로 로그인해 보세요.')
        return
      }
      setMessage(failure instanceof ApiError ? failure.message : '가입하지 못했어요. 다시 시도해 주세요.')
    },
  })

  function startVerify() {
    if (token === null || verify.isPending || sending.current) return
    setMessage(undefined)
    sending.current = true
    verify.mutate(token)
  }

  function submitDetails(event: FormEvent) {
    event.preventDefault()
    if (!verified || submit.isPending || sending.current) return
    const result = validateSignup({
      email: verified.email, password, passwordConfirm, displayName, emailVerificationProof: verified.proof,
    })
    setErrors(result.errors)
    setMessage(undefined)
    if (!result.valid) return
    sending.current = true
    submit.mutate(result.input)
  }

  function change(setter: (value: string) => void, field: FieldName) {
    return (value: string) => {
      setter(value)
      setErrors((previous) => ({ ...previous, [field]: undefined }))
      setMessage(undefined)
    }
  }

  if (current.data && stage !== 'details') {
    return <AuthLayout title="이미 로그인되어 있어요">
      <p className={styles.notice}>다른 계정을 만들려면 먼저 로그아웃해 주세요.</p>
      <div className={styles.links}><Link to="/">서비스 둘러보기</Link></div>
    </AuthLayout>
  }

  if (stage === 'invalidLink' || stage === 'proofExpired') {
    return <AuthLayout title={stage === 'invalidLink' ? '링크를 쓸 수 없어요' : '인증이 만료됐어요'}>
      <p className={styles.error} role="alert">
        {message ?? (stage === 'invalidLink'
          ? '유효하지 않거나 만료된 인증 링크예요. 링크는 15분 동안 한 번만 쓸 수 있어요.'
          : '이메일 인증이 만료됐거나 이미 사용됐어요. 인증 메일을 다시 받아 주세요.')}
      </p>
      <div className={styles.links}>
        <Link to="/signup">인증 메일 다시 받기</Link>
        <Link to="/login">로그인</Link>
      </div>
    </AuthLayout>
  }

  if (stage === 'registered') {
    return <AuthLayout title="이미 가입된 이메일이에요">
      <p className={styles.notice}><strong>{verified?.email}</strong>은(는) 이미 가입돼 있어요. 로그인하거나, 비밀번호가 기억나지 않으면 재설정해 주세요.</p>
      <div className={styles.links}>
        <Link to="/login">로그인</Link>
        <Link to="/password-reset/request">비밀번호 재설정</Link>
      </div>
    </AuthLayout>
  }

  if (stage === 'verify') {
    return <AuthLayout title="이메일 인증" description="아래 버튼을 누르면 이메일 인증을 마치고 가입 정보를 입력할 수 있어요.">
      {message && <p className={styles.error} role="alert">{message}</p>}
      <Button type="button" className={styles.submit} disabled={verify.isPending} onClick={startVerify}>
        {verify.isPending ? '인증 중…' : '인증하기'}
      </Button>
    </AuthLayout>
  }

  return <AuthLayout title="가입 정보 입력" description="이메일 인증을 마쳤어요. 비밀번호와 표시 이름을 정하면 가입이 끝나요.">
    {message && <p className={styles.error} role="alert">{message}</p>}
    <form onSubmit={submitDetails} noValidate className={styles.form}>
      <fieldset disabled={submit.isPending}>
        <Field id="signup-email" label="이메일" help="인증한 이메일이에요. 로그인 아이디로 사용해요.">
          <Input id="signup-email" type="email" autoComplete="username" value={verified?.email ?? ''} readOnly
            aria-describedby="signup-email-help" />
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
          help={`다른 회원에게 보이는 이름이에요. ${normalizedLength(displayName)} / 30자`}>
          <Input id="signup-name" autoComplete="nickname" value={displayName} aria-invalid={!!errors.displayName}
            aria-describedby="signup-name-help signup-name-error" onChange={(e) => change(setDisplayName, 'displayName')(e.target.value)} />
        </Field>
        <Button type="submit" className={styles.submit}>{submit.isPending ? '가입 중…' : '가입하기'}</Button>
      </fieldset>
    </form>
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

/** 서버 입력 오류(fieldErrors)를 입력칸 안내로 바꾼다. 서버 문구 대신 화면 규칙 문구를 쓴다. */
function serverFieldErrors(error: Error): SignupErrors {
  if (!(error instanceof ApiError)) return {}
  const errors: SignupErrors = {}
  for (const field of error.fieldErrors) {
    if (field.field === 'password') errors.password = '영문과 숫자를 포함해 8~64자로 입력해 주세요.'
    if (field.field === 'displayName') errors.displayName = '표시 이름은 1~30자여야 해요.'
  }
  if (Object.keys(errors).length === 0) errors.password = '입력값을 다시 확인해 주세요.'
  return errors
}
