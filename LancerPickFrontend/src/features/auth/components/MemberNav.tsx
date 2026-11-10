import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, NavLink, useNavigate } from 'react-router'
import { isUnauthorized } from '../../../shared/api/client'
import { logout } from '../api'
import { useCurrentMember } from '../queries'
import { NotificationNavLink } from '../../notifications/components/NotificationNavLink'
import { ChatEntry } from '../../order-chat/components/ChatEntry'
import { useChatWorkspace } from '../../order-chat/workspace'
import { isUnclearResult, recheckSession, resetAccountState } from '../session'

/**
 * 헤더 오른쪽 회원 메뉴. 로그인 상태는 서버의 GET /api/me 결과로만 판단한다.
 * 클래스 이름은 AppLayout이 넘겨준다(헤더 스타일은 공통 레이아웃 담당).
 */
export function MemberNav({ signupClassName, buttonClassName }: { signupClassName: string; buttonClassName: string }) {
  const member = useCurrentMember()
  const chat = useChatWorkspace()
  const client = useQueryClient()
  const navigate = useNavigate()
  const out = useMutation({
    retry: false,
    mutationFn: logout,
    onSettled: async (_data, error) => {
      // 401은 이미 세션이 끝난 것이므로 로그아웃 성공과 똑같이 정리한다.
      if (error && !(isUnauthorized(error))) {
        // 서버는 로그아웃을 끝냈을 수도 있다. 실패로 단정하지 않고 실제 상태를 다시 확인한다.
        if (!isUnclearResult(error)) return
        // 서버는 로그아웃을 끝냈을 수도 있다. 다시 확인해서 로그인 상태가 아니면 개인 캐시를 지우고 보호 화면에서 벗어난다.
        const outcome = await recheckSession(client)
        if (outcome !== 'same') navigate('/', { replace: true })
        return
      }
      await resetAccountState(client, null)
      navigate('/', { replace: true })
    },
  })

  if (member.isPending) return <span aria-live="polite">확인 중…</span>
  if (member.data) {
    return <>
      {/* 메뉴 표시는 편의일 뿐이다. 실제 관리자 권한은 서버가 /api/admin/** 요청마다 다시 확인한다. */}
      {member.data.role === 'ADMIN' && <NavLink to="/admin">관리자</NavLink>}
      <NotificationNavLink key={member.data.id} onOpen={chat?.minimize} />
      <ChatEntry />
      <NavLink to="/mypage">{member.data.displayName}님</NavLink>
      <button type="button" className={buttonClassName} disabled={out.isPending} onClick={() => out.mutate()}>
        {out.isPending ? '로그아웃 중…' : '로그아웃'}
      </button>
      {out.isError && !(isUnauthorized(out.error))
        && <span role="alert">로그아웃하지 못했어요. 다시 시도해 주세요.</span>}
    </>
  }
  // 로그인 안 함(null) 또는 서버 확인 실패: 로그인 메뉴를 보여준다. 보호 화면은 각자 다시 확인한다.
  return <>
    <NavLink to="/login">로그인</NavLink>
    <Link to="/signup" className={signupClassName}>회원가입</Link>
  </>
}
