import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { verifyExistingEmail } from '../api'
import { useCurrentMember } from '../queries'
import { captureAccountScope, isUnclearResult, memberQueryKey } from '../session'
import { useLinkToken } from '../useLinkToken'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'
import { useMountedRef } from '../../../shared/lib/useMountedRef'

type Stage = 'ready' | 'done' | 'invalidLink' | 'unclear'

/**
 * /mypage/email-verification#token=... — 기존 회원 이메일 인증 링크(K1, 3인 설계 §5.2).
 * - 링크를 여는 것만으로는 인증하지 않는다. "이메일 인증하기"를 눌러야 POST가 한 번 나간다(메일 검사기의 자동 열람 대비).
 * - 요청한 계정으로 로그인한 상태여야 한다. 로그아웃 상태면 로그인 후 메일의 링크를 다시 열도록 안내한다
 *   (토큰은 주소에서 지워 메모리에만 두므로 로그인 화면을 거치면 사라진다. 저장소에 남기지 않는다).
 * - 다른 계정으로 로그인했으면 서버가 같은 400으로 거절한다(그 계정을 인증하지 않음).
 * - 자동 재전송 없음(retry: false). 통신 오류·5xx면 인증됐을 수 있으니 내 정보에서 상태를 확인하도록 안내한다.
 */
export function EmailVerificationPage() {
  const client = useQueryClient()
  const member = useCurrentMember()
  const token = useLinkToken()
  const active = useMountedRef()
  const sending = useRef(false)
  const [stage, setStage] = useState<Stage>(token === null ? 'invalidLink' : 'ready')
  const [message, setMessage] = useState<string>()

  const verify = useMutation({
    retry: false, // 링크 토큰은 일회용이다. 자동으로 다시 보내지 않는다
    mutationFn: (input: { token: string; scope: () => boolean }) => verifyExistingEmail(input.token, input.scope),
    onSettled: () => { sending.current = false },
    onSuccess: async (_result, input) => {
      if (!input.scope()) return
      await client.invalidateQueries({ queryKey: memberQueryKey })
      if (active.current) setStage('done')
    },
    onError: async (failure, input) => {
      if (!input.scope() || !active.current) return
      if (failure instanceof ApiError && failure.code === 'EMAIL_VERIFICATION_INVALID') { setStage('invalidLink'); return }
      if (failure instanceof ApiError && failure.code === 'CSRF_INVALID') { setMessage('요청 정보가 만료됐어요. 다시 시도해 주세요.'); return }
      if (isUnclearResult(failure)) {
        await client.invalidateQueries({ queryKey: memberQueryKey })
        if (active.current) setStage('unclear')
        return
      }
      setMessage(failure instanceof ApiError ? failure.message : '인증하지 못했어요. 잠시 후 다시 시도해 주세요.')
    },
  })

  function submit() {
    if (token === null || verify.isPending || sending.current) return
    sending.current = true
    setMessage(undefined)
    verify.mutate({ token, scope: captureAccountScope(client) })
  }

  const title = '이메일 인증'
  if (stage === 'done') {
    return <AuthLayout title={title} description="이메일 인증을 마쳤어요.">
      <p className={styles.success} role="status">이 주소로 메일을 받을 수 있는 것을 확인했어요.</p>
      <p className={styles.switch}><Link to="/mypage/profile">내 정보로 가기</Link></p>
    </AuthLayout>
  }
  if (stage === 'invalidLink') {
    return <AuthLayout title={title} description="이 링크로는 인증할 수 없어요.">
      <p className={styles.error} role="alert">링크가 만료됐거나 이미 사용됐어요. 또는 인증을 요청한 계정이 아닌 다른 계정으로 로그인했을 수 있어요.</p>
      <p className={styles.switch}><Link to="/mypage/profile">내 정보에서 인증 메일 다시 받기</Link></p>
    </AuthLayout>
  }
  if (stage === 'unclear') {
    return <AuthLayout title={title} description="인증 결과를 확인하지 못했어요.">
      <p className={styles.error} role="alert">인증이 끝났을 수 있어요. 같은 링크를 다시 보내지 말고 내 정보에서 인증 상태를 확인해 주세요.</p>
      <p className={styles.switch}><Link to="/mypage/profile">내 정보에서 확인하기</Link></p>
    </AuthLayout>
  }
  if (member.isPending) {
    return <AuthLayout title={title} description="로그인 상태를 확인하고 있어요."><p role="status">잠시만 기다려 주세요.</p></AuthLayout>
  }
  if (!member.data) {
    return <AuthLayout title={title} description="인증을 요청한 계정으로 로그인해야 인증할 수 있어요.">
      <p className={styles.error} role="alert">로그인한 뒤 메일의 링크를 다시 열어 주세요. 링크는 받은 뒤 15분 동안 쓸 수 있어요.</p>
      <p className={styles.switch}><Link to="/login">로그인</Link></p>
    </AuthLayout>
  }
  return <AuthLayout title={title} description={`${member.data.email} 주소를 인증해요. 버튼을 눌러야 인증돼요.`}>
    {message && <p className={styles.error} role="alert">{message}</p>}
    <Button type="button" className={styles.submit} disabled={verify.isPending} onClick={submit}>
      {verify.isPending ? '인증 중…' : '이메일 인증하기'}
    </Button>
  </AuthLayout>
}
