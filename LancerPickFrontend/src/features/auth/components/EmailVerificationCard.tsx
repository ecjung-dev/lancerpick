import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'
import { requestExistingEmailVerification } from '../api'
import { MAIL_REQUEST_COOLDOWN_SECONDS } from '../linkToken'
import { captureAccountScope, isUnclearResult, memberQueryKey } from '../session'
import type { Member } from '../types'
import styles from '../pages/MyProfilePage.module.css'
import { useMountedRef } from '../../../shared/lib/useMountedRef'

/**
 * 내 정보의 이메일 인증 상태(K1 기존 회원 전환). 인증 전이면 인증 메일을 요청할 수 있다.
 * - 요청은 한 번만 보낸다(retry: false). 처리 중에는 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다.
 * - 보낸 뒤 60초 동안 다시 보내기를 막는다. 결과가 불명확해도(통신 오류·5xx) 서버가 보냈을 수 있으니 똑같이 막는다.
 * - 409(이미 인증됨, 다른 탭에서 끝냄)는 내 정보를 다시 불러와 상태를 맞춘다.
 * - 클릭한 계정과 지금 계정이 다르면 보내지 않고 결과도 반영하지 않는다(captureAccountScope).
 */
export function EmailVerificationCard({ member }: { member: Member }) {
  const client = useQueryClient()
  const active = useMountedRef()
  const sending = useRef(false)
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string }>()
  const [cooldown, setCooldown] = useState(0)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((left) => left - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const send = useMutation({
    retry: false,
    mutationFn: (scope: () => boolean) => requestExistingEmailVerification(scope),
    onSettled: () => { sending.current = false },
    onSuccess: (_result, scope) => {
      if (!scope() || !active.current) return
      setCooldown(MAIL_REQUEST_COOLDOWN_SECONDS)
      setMessage({ kind: 'success', text: `${member.email}로 인증 메일을 보냈어요. 15분 안에 이 브라우저에서 로그인한 채로 링크를 열어 주세요. 메일이 오지 않으면 스팸함을 확인해 주세요.` })
    },
    onError: async (failure, scope) => {
      if (!scope()) return
      if (failure instanceof ApiError && failure.code === 'EMAIL_ALREADY_VERIFIED') {
        await client.invalidateQueries({ queryKey: memberQueryKey })
        return
      }
      if (!active.current) return
      if (isUnclearResult(failure)) {
        setCooldown(MAIL_REQUEST_COOLDOWN_SECONDS)
        setMessage({ kind: 'error', text: '요청 결과를 확인하지 못했어요. 메일함을 먼저 확인하고, 메일이 없으면 잠시 후 다시 요청해 주세요.' })
        return
      }
      setMessage({ kind: 'error', text: failure instanceof ApiError ? failure.message : '요청하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    },
  })

  if (member.emailVerified) {
    return <section aria-labelledby="email-verification-title">
      <h2 id="email-verification-title">이메일 인증</h2>
      <p>{member.email} · 인증 완료</p>
    </section>
  }

  function request() {
    if (send.isPending || sending.current || cooldown > 0) return
    sending.current = true
    setMessage(undefined)
    send.mutate(captureAccountScope(client))
  }

  const waiting = cooldown > 0
  return <section aria-labelledby="email-verification-title">
    <h2 id="email-verification-title">이메일 인증</h2>
    <p>{member.email} · 아직 인증하지 않았어요. 이 주소로 메일을 받을 수 있는지 확인하면 비밀번호 재설정·아이디 안내 메일을 안심하고 받을 수 있어요. 인증하지 않아도 지금처럼 로그인과 거래는 그대로 할 수 있어요.</p>
    {message && <p className={message.kind === 'success' ? styles.success : styles.notice}
      role={message.kind === 'success' ? 'status' : 'alert'}>{message.text}</p>}
    <Button type="button" disabled={send.isPending || waiting} onClick={request}>
      {send.isPending ? '요청 중…' : waiting ? `${cooldown}초 후 다시 요청할 수 있어요` : '인증 메일 받기'}
    </Button>
  </section>
}
