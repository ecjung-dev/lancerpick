import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { codePointLength, normalizeText } from '../../../shared/lib/text'
import { saveDisplayName } from '../profile'
import { useCurrentMember } from '../queries'
import { isUnclearResult, memberQueryKey, resetAccountState } from '../session'
import type { Member } from '../types'
import { DISPLAY_NAME_MAX, validateDisplayName } from '../validation'
import styles from './MyProfilePage.module.css'
import { PasswordChangeForm } from './PasswordChangeForm'

const PROFILE_PATH = '/mypage/profile'

/** /mypage/profile. 내 정보 확인, 표시 이름 수정(API 2절 PATCH /me), 비밀번호 변경(POST /me/password). */
export function MyProfilePage() {
  const member = useCurrentMember()
  const [accountChanged, setAccountChanged] = useState(false)

  if (member.isPending) {
    return <Layout><AsyncState title="로그인 상태를 확인하고 있어요" /></Layout>
  }
  if (member.isError && !member.data) {
    return (
      <Layout>
        <AsyncState
          title="로그인 상태를 확인하지 못했어요"
          error={member.error}
          onRetry={() => { void member.refetch() }}
        />
      </Layout>
    )
  }
  if (!member.data) {
    return <Layout><LoginNotice /></Layout>
  }
  return (
    <Layout>
      {accountChanged && (
        <p className={styles.notice} role="alert">
          로그인 계정이 바뀌어 저장하지 않았어요. 지금 로그인한 계정의 정보를 보여드려요.
        </p>
      )}
      {/* 계정이 바뀌면 key가 바뀌어 입력 상태도 새로 시작한다(이전 계정 입력이 남지 않게). */}
      <ProfileForm
        key={member.data.id}
        member={member.data}
        onAccountChanged={() => setAccountChanged(true)}
      />
      <hr className={styles.divider} />
      <PasswordChangeForm
        key={`password-${member.data.id}`}
        member={member.data}
        onAccountChanged={() => setAccountChanged(true)}
      />
    </Layout>
  )
}

/**
 * 실패·동시 요청·늦은 응답 처리:
 * - 처리 중에는 입력을 막고, 빠른 두 번 클릭은 ref로 한 번 더 막는다. 실패해도 자동으로 다시 보내지 않는다(retry: false).
 * - 401: 이전 계정 캐시를 정리하고 로그인 안내로 바뀐다.
 * - 통신 오류·5xx: 서버가 저장했을 수 있으므로 실패로 단정하지 않고 내 정보를 다시 조회한다. 입력값은 남긴다.
 * - 응답이 화면을 떠난 뒤 도착하면 화면 상태는 바꾸지 않는다(캐시 갱신은 saveDisplayName이 한다).
 */
function ProfileForm({ member, onAccountChanged }: { member: Member; onAccountChanged: () => void }) {
  const client = useQueryClient()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [name, setName] = useState(member.displayName)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    retry: false,
    mutationFn: (displayName: string) => saveDisplayName(client, member.id, displayName),
    onSettled: () => { sending.current = false },
    onSuccess: (outcome) => {
      if (outcome.kind === 'accountChanged') { onAccountChanged(); return }
      if (outcome.kind !== 'saved' || !active.current) return
      setName(outcome.member.displayName)
      setSaved(true)
    },
    onError: async (failure) => {
      if (failure instanceof ApiError && failure.status === 401) {
        await resetAccountState(client, null)
        return
      }
      if (isUnclearResult(failure)) {
        await client.invalidateQueries({ queryKey: memberQueryKey })
        if (active.current) setNotice('저장 결과를 확인하지 못해 내 정보를 다시 불러왔어요. 현재 표시 이름을 확인한 뒤 필요하면 다시 저장해 주세요.')
        return
      }
      if (active.current) setError(failure instanceof ApiError ? failure.message : '저장하지 못했어요. 다시 시도해 주세요.')
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (save.isPending || sending.current) return
    const result = validateDisplayName(name)
    setError(result.error)
    setNotice(undefined)
    setSaved(false)
    if (result.error) return
    if (result.displayName === member.displayName) {
      setNotice('지금 표시 이름과 같아요.')
      return
    }
    sending.current = true
    save.mutate(result.displayName)
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <dl className={styles.info}>
        <div>
          <dt>이메일</dt>
          <dd>{member.email}</dd>
        </div>
        <div>
          <dt>현재 표시 이름</dt>
          <dd>{member.displayName}</dd>
        </div>
      </dl>
      {saved && <p className={styles.success} role="status">표시 이름을 저장했어요.</p>}
      {notice && <p className={styles.notice} role="status">{notice}</p>}
      <fieldset disabled={save.isPending}>
        <label htmlFor="profile-display-name">표시 이름 <span>필수</span></label>
        <Input
          id="profile-display-name"
          value={name}
          autoComplete="nickname"
          aria-invalid={!!error}
          aria-describedby="profile-display-name-help profile-display-name-error"
          onChange={(e) => {
            setName(e.target.value)
            setError(undefined)
            setSaved(false)
          }}
        />
        <p id="profile-display-name-help" className={styles.help}>
          <span>주문·판매자 프로필에서 다른 회원에게 보여요. 다른 회원과 같아도 괜찮아요.</span>
          <span>{codePointLength(normalizeText(name))} / {DISPLAY_NAME_MAX}자</span>
        </p>
        <p id="profile-display-name-error" className={styles.fieldError}>{error}</p>
        <div className={styles.actions}>
          <p>이메일(로그인 아이디)은 바꿀 수 없어요.</p>
          <Button type="submit">{save.isPending ? '저장 중…' : '표시 이름 저장'}</Button>
        </div>
      </fieldset>
    </form>
  )
}

function LoginNotice() {
  return (
    <>
      <AsyncState title="로그인 후 내 정보를 볼 수 있어요" description="로그인하면 이 화면으로 돌아와요." />
      <Link className={styles.link} to={`/login?next=${encodeURIComponent(PROFILE_PATH)}`}>
        로그인으로 이동 →
      </Link>
    </>
  )
}

function Layout({ children }: { children: ReactNode }) {
  return (
    <section className={styles.page}>
      <nav className={styles.breadcrumb} aria-label="현재 위치">
        <Link to="/">홈</Link>
        <span>/</span>
        <Link to="/mypage">마이페이지</Link>
        <span>/ 내 정보</span>
      </nav>
      <header className={styles.heading}>
        <p>MY ACCOUNT</p>
        <h1>내 정보</h1>
        <span>다른 회원에게 보이는 표시 이름과 비밀번호를 바꿀 수 있어요.</span>
      </header>
      <div className={styles.card}>{children}</div>
    </section>
  )
}
