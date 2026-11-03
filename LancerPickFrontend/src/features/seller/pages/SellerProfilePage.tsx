import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { codePointLength, normalizeText } from '../../../shared/lib/text'
import { getMySellerProfile, getSellerSession, saveSellerProfile } from '../api'
import type { SellerProfile, SellerProfileInput, SellerSession } from '../types'
import { validateSellerProfile, type ProfileErrors } from '../validation'
import styles from './SellerProfilePage.module.css'

const expired = (error: unknown) => error instanceof ApiError && error.status === 401

export function SellerProfilePage() {
  const session = useQuery({
    queryKey: ['seller-profile-session'], queryFn: ({ signal }) => getSellerSession(signal),
    staleTime: 0, gcTime: 0, retry: false, refetchOnWindowFocus: 'always',
  })
  if (expired(session.error)) return <Layout><LoginNotice /></Layout>
  if (session.isError) return <Layout><AsyncState title="로그인 상태를 확인하지 못했어요" error={session.error} onRetry={() => { void session.refetch() }} /></Layout>
  if (!session.data) return <Layout><AsyncState title="로그인 상태를 확인하고 있어요" /></Layout>
  return <Layout>
    {session.isFetching && <p role="status">로그인 상태를 다시 확인하고 있어요.</p>}
    <div hidden={session.isFetching}>
      <ConnectedProfile key={session.data.id} member={session.data} sessionChecking={session.isFetching} />
    </div>
  </Layout>
}

function ConnectedProfile({ member, sessionChecking }: { member: SellerSession; sessionChecking: boolean }) {
  const client = useQueryClient()
  const active = useRef(true)
  const [saved, setSaved] = useState(false)
  const [accountChanged, setAccountChanged] = useState(false)
  const key = ['seller-profile', member.id] as const
  const profile = useQuery({
    queryKey: key, queryFn: ({ signal }) => getMySellerProfile(signal), gcTime: 0,
    staleTime: 0, retry: false, refetchOnWindowFocus: false,
  })
  useEffect(() => {
    active.current = true
    return () => {
      active.current = false
      void client.cancelQueries({ queryKey: ['seller-profile', member.id], exact: true })
      client.removeQueries({ queryKey: ['seller-profile', member.id], exact: true })
    }
  }, [client, member.id])

  const save = useMutation({
    retry: false,
    mutationFn: async (input: SellerProfileInput) => {
      const current = await getSellerSession()
      if (current.id !== member.id) {
        if (active.current) setAccountChanged(true)
        throw new Error('로그인 계정이 바뀌었어요. 페이지를 새로고침해 주세요.')
      }
      return saveSellerProfile(input)
    },
    onSuccess: (response) => {
      if (!active.current) return
      if (response.id !== member.id) { setAccountChanged(true); return }
      client.setQueryData(key, response)
      setSaved(true)
    },
    onError: (error) => {
      if (expired(error)) client.removeQueries({ queryKey: key, exact: true })
    },
  })

  if (expired(profile.error) || expired(save.error)) return <LoginNotice />
  if (accountChanged || (profile.data && profile.data.id !== member.id)) return <AsyncState title="로그인 계정을 다시 확인해 주세요" description="계정이 바뀌었을 수 있어 프로필을 표시하지 않습니다. 페이지를 새로고침해 주세요." />
  if (profile.isPending) return <AsyncState title="판매자 프로필을 불러오고 있어요" />
  if (profile.isError) return <AsyncState title="프로필을 불러오지 못했어요" error={profile.error} onRetry={() => { void profile.refetch() }} />
  return <>
    <div className={styles.mode}><strong>{member.displayName}</strong><span>{profile.data ? '등록된 프로필을 수정해요' : '첫 판매자 프로필을 등록해요'}</span></div>
    {saved && <p className={styles.success} role="status">판매자 프로필을 저장했어요.</p>}
    {save.isError && <p className={styles.error} role="alert">{save.error.message}{save.error instanceof ApiError && save.error.traceId && <small>문의 코드: {save.error.traceId}</small>}</p>}
    <ProfileForm key={profile.dataUpdatedAt} initial={profile.data} busy={save.isPending || sessionChecking}
      onChange={() => { setSaved(false); save.reset() }} onSave={(input) => { setSaved(false); save.mutate(input) }} />
  </>
}

function ProfileForm({ initial, busy, onChange, onSave }: {
  initial: SellerProfile | null; busy: boolean; onChange: () => void; onSave: (input: SellerProfileInput) => void
}) {
  const [introduction, setIntroduction] = useState(initial?.introduction ?? '')
  const [specialty, setSpecialty] = useState(initial?.specialty ?? '')
  const [portfolioUrl, setPortfolioUrl] = useState(initial?.portfolioUrl ?? '')
  const [errors, setErrors] = useState<ProfileErrors>({})
  function submit(event: FormEvent) {
    event.preventDefault()
    const result = validateSellerProfile({ introduction, specialty, portfolioUrl })
    setErrors(result.errors)
    if (result.valid && !busy) onSave(result.input)
  }
  function changed() { setErrors({}); onChange() }
  return <form onSubmit={submit} noValidate className={styles.form}>
    <fieldset disabled={busy}>
      <div className={styles.field}>
        <label htmlFor="seller-introduction">판매자 소개 <span>필수</span></label>
        <textarea id="seller-introduction" rows={7} value={introduction} aria-invalid={!!errors.introduction}
          aria-describedby="seller-introduction-help seller-introduction-error" onChange={(e) => { setIntroduction(e.target.value); changed() }}
          placeholder="어떤 일을 하고, 어떤 도움을 줄 수 있는지 소개해 주세요." />
        <div id="seller-introduction-help" className={styles.help}><span>소개는 공개 프로필에 표시돼요.</span><span>{codePointLength(normalizeText(introduction))} / 2,000자</span></div>
        <p id="seller-introduction-error" className={styles.fieldError}>{errors.introduction}</p>
      </div>
      <div className={styles.field}>
        <label htmlFor="seller-specialty">전문 분야 <span>필수</span></label>
        <Input id="seller-specialty" value={specialty} aria-invalid={!!errors.specialty}
          aria-describedby="seller-specialty-help seller-specialty-error" onChange={(e) => { setSpecialty(e.target.value); changed() }} placeholder="예: 로고 디자인, 웹 개발" />
        <p id="seller-specialty-help" className={styles.help}>{codePointLength(normalizeText(specialty))} / 100자</p>
        <p id="seller-specialty-error" className={styles.fieldError}>{errors.specialty}</p>
      </div>
      <div className={styles.field}>
        <label htmlFor="seller-portfolio">포트폴리오 링크 <span>선택</span></label>
        <Input id="seller-portfolio" type="url" value={portfolioUrl} aria-invalid={!!errors.portfolioUrl}
          aria-describedby="seller-portfolio-help seller-portfolio-error" onChange={(e) => { setPortfolioUrl(e.target.value); changed() }} placeholder="https://" />
        <p id="seller-portfolio-help" className={styles.help}>HTTPS 주소를 입력해 주세요. 비워서 저장하면 기존 링크가 삭제돼요.</p>
        <p id="seller-portfolio-error" className={styles.fieldError}>{errors.portfolioUrl}</p>
      </div>
      <div className={styles.actions}><p>소개와 전문 분야를 함께 저장해요.</p><Button type="submit" disabled={busy}>{busy ? '저장 중…' : initial ? '프로필 수정 저장' : '판매자 프로필 등록'}</Button></div>
    </fieldset>
  </form>
}

function LoginNotice() {
  return <><AsyncState title="로그인 후 프로필을 관리할 수 있어요" description="로그인한 계정의 판매자 소개와 전문 분야를 등록해 주세요." /><Link className={styles.link} to="/login">로그인으로 이동 →</Link></>
}

function Layout({ children }: { children: ReactNode }) {
  return <section className={styles.page}>
    <nav className={styles.breadcrumb} aria-label="현재 위치"><Link to="/">홈</Link><span>/</span><Link to="/mypage">마이페이지</Link><span>/ 판매자 프로필</span></nav>
    <header className={styles.heading}><p>SELLER PROFILE</p><h1>판매자 프로필 관리</h1><span>나의 전문성을 소개하고, 함께할 다음 일을 준비하세요.</span></header>
    <div className={styles.card}>{children}</div>
  </section>
}
