import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { getMemberSession } from '../../auth/api'
import { downloadContract, generateContract, verifyContractPdf } from '../api'
import { prepareContract, signContract } from '../api'
import { useContract, useContractMember } from '../queries'
import { Icon } from '../../../shared/components/Icon'
import { contractSections } from '../document-view'
import styles from './ContractPanel.module.css'

const labels = {
  DRAFT: 'PDF 생성 대기',
  GENERATING: 'PDF 생성 중',
  FAILED: 'PDF 생성 실패',
  READY: '서명 대기',
  SIGNED: '양측 서명 완료',
  SUPERSEDED: '교체된 계약',
}

export function ContractPanel({
  orderId,
  sellerId,
  orderStatus,
  policyVersion = 'CONTRACT_V2',
  hasContractHistory = false,
}: {
  orderId: string
  sellerId: string
  orderStatus: string
  policyVersion?: string
  hasContractHistory?: boolean
}) {
  const client = useQueryClient()
  const member = useContractMember()
  const current = useContract(orderId, member.data?.id)
  const [selected, setSelected] = useState<number>()
  const chosen = useContract(orderId, member.data?.id, selected)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [terms, setTerms] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [reviewed, setReviewed] = useState('')
  const lock = useRef(false)
  const pendingDownload = useRef<AbortController | null>(null)
  const epoch = useRef(0)
  useEffect(
    () => () => {
      epoch.current += 1
      pendingDownload.current?.abort()
    },
    [member.data?.id],
  )
  const document = chosen.isError || member.isError ? undefined : chosen.data
  const mutable = ['AWAITING_PAYMENT', 'AWAITING_CONTRACT'].includes(
    orderStatus,
  )
  // 주문 상세의 권한 확인 후 전달된 상태·이력으로 정상 미작성과 계약 조회 실패를 구분한다.
  // 이미 받은 계약 데이터, 특정 버전 선택, 계약 생성 이력이 있으면 404도 오류로 남긴다.
  const contractNotCreated =
    member.isSuccess &&
    selected === undefined &&
    !current.data &&
    !hasContractHistory &&
    ['REQUESTED', 'AWAITING_CONTRACT', 'REJECTED', 'CANCELLED'].includes(orderStatus) &&
    current.error instanceof ApiError &&
    current.error.status === 404 &&
    current.error.code === 'RESOURCE_NOT_FOUND'

  const sections = document ? contractSections(document.canonicalContent) : []

  // 계약 주문 수락은 주문 상세의 공통 수락 버튼(POST /orders/{id}/accept)에서 한다.
  async function act(download: boolean | 'prepare' | 'sign') {
    if (
      lock.current ||
      !member.data ||
      (!document && typeof download === 'boolean')
    )
      return
    lock.current = true
    setBusy(true)
    setMessage('')
    const owner = member.data.id
    const startedAt = epoch.current
    try {
      if ((await getMemberSession()).id !== owner)
        throw new Error('계정이 변경되었습니다. 새로고침 후 확인해 주세요.')
      if (epoch.current !== startedAt) return
      if (download === 'prepare')
        await prepareContract(orderId, current.data?.version ?? 0, terms)
      else if (download === 'sign') {
        if (!document || !agreed || reviewed !== document.fileHash)
          throw new Error('현재 버전 PDF를 내려받고 동의해 주세요.')
        await signContract(orderId, document)
        setAgreed(false)
        setReviewed('')
      } else if (download && document) {
        const controller = new AbortController()
        pendingDownload.current = controller
        const blob = await downloadContract(
          orderId,
          document.version,
          controller.signal,
        )
        await verifyContractPdf(blob, document.fileHash ?? '')
        if ((await getMemberSession()).id !== owner)
          throw new Error('계정이 변경되어 다운로드를 중단했습니다.')
        if (controller.signal.aborted || epoch.current !== startedAt) return
        const url = URL.createObjectURL(blob)
        const anchor = window.document.createElement('a')
        anchor.href = url
        anchor.download = `contract-${orderId}-v${document.version}.pdf`
        anchor.click()
        setReviewed(document.fileHash ?? '')
        setTimeout(() => URL.revokeObjectURL(url), 1000)
      } else if (document) {
        await generateContract(orderId, document.version)
        setMessage('현재 계약 상태를 다시 확인합니다.')
      }
    } catch (error) {
      setMessage(
        error instanceof ApiError && error.status === 0
          ? '처리 결과가 불명확합니다. 상태 새로고침 후 확인해 주세요. 자동 재전송하지 않습니다.'
          : error instanceof Error
            ? error.message
            : '계약서를 확인하지 못했습니다.',
      )
    } finally {
      await client.invalidateQueries({ queryKey: ['wallet', 'contract'] })
      await client.invalidateQueries({ queryKey: ['wallet', 'order', orderId] })
      lock.current = false
      setBusy(false)
    }
  }

  return (
    <section className={styles.panel} aria-label="전자계약서">
<header className={styles.heading}><div><span className={styles.eyebrow}>ELECTRONIC CONTRACT</span><h2>전자계약서</h2><p>계약 내용과 서명 현황을 확인하세요.</p></div><Icon name="document" size={26} /></header>
      {member.data?.id === sellerId &&
        orderStatus === 'AWAITING_CONTRACT' &&
        current.data?.status !== 'SIGNED' &&
        (current.isSuccess || contractNotCreated) && (
          <div className={styles.draft}>
            <label>
              추가 특약
              <textarea
                value={terms}
                maxLength={10000}
                onChange={(e) => setTerms(e.target.value)}
                disabled={busy}
              />
            </label>
            <p>
              표준 본문은 고정되며 특약만 추가됩니다. 변경하면 이전 서명은 새
              버전에 적용되지 않습니다.
            </p>
            <button
              disabled={busy || Array.from(terms).length > 5000}
              onClick={() => {
                void act('prepare')
              }}
            >
              특약으로 계약 초안 저장
            </button>
          </div>
        )}
      {member.isError && (
        <p role="alert">
          로그인 상태를 확인하지 못했습니다. 페이지를 새로고침해 주세요.
        </p>
      )}
      {(current.isPending || chosen.isPending) && !member.isError && (
        <p role="status">계약서를 확인하고 있습니다.</p>
      )}
      {contractNotCreated && (
        <p role="status">
          아직 작성된 계약서가 없습니다.
          {orderStatus === 'REQUESTED'
            ? ' 판매자 수락 후 계약을 작성합니다.'
            : orderStatus === 'AWAITING_CONTRACT'
              ? member.data?.id === sellerId
                ? ' 추가 특약을 입력해 계약 초안을 작성해 주세요.'
                : ' 판매자가 계약 초안을 작성하면 확인할 수 있습니다.'
              : ' 계약 작성 전에 종료된 주문입니다.'}
        </p>
      )}
      {chosen.isError && !contractNotCreated && (
        <p role="alert">
          {chosen.error instanceof ApiError && chosen.error.status === 404
            ? '계약서를 찾을 수 없거나 조회할 수 없습니다. 새로고침 후 다시 확인해 주세요.'
            : chosen.error.message}
        </p>
      )}
      {document && (
        <>
          <div className={styles.overview}>
          <label className={styles.version}>
            계약 버전{' '}
            <input
              type="number"
              min={1}
              max={current.data?.version ?? document.version}
              value={selected ?? current.data?.version ?? document.version}
              disabled={busy}
              onChange={(e) => {
                const version = Number(e.target.value)
                if (
                  Number.isInteger(version) &&
                  version >= 1 &&
                  version <= (current.data?.version ?? document.version)
                ) {
                  setSelected(version)
                  setMessage('')
                }
              }}
            />
          </label>
          <p className={styles.badge} role="status">
            {labels[document.status] ?? '계약 상태 확인 필요'}
          </p>
          <p className={styles.signatureCount}>서명 <strong>{document.signatures.length}/2</strong></p>
          </div>
          <div className={styles.signatures} aria-label="서명 현황">
            {(['BUYER', 'SELLER'] as const).map(role => {
              const signature = document.signatures.find(item => item.role === role)
              return <div key={role}><span>{role === 'BUYER' ? '구매자' : '판매자'}</span><strong>{signature ? '서명 완료' : '서명 대기'}</strong></div>
            })}
          </div>
          <div className={styles.actions}>
            {document.fileHash &&
              ['READY', 'SIGNED', 'SUPERSEDED'].includes(document.status) && (
                <button
                  disabled={busy || chosen.isFetching}
                  onClick={() => {
                    void act(true)
                  }}
                >
                  <Icon name="document" size={16} />PDF 다운로드
                </button>
              )}
            {member.data?.id === sellerId &&
              mutable &&
              document.version === current.data?.version &&
              ['DRAFT', 'FAILED', 'GENERATING'].includes(document.status) && (
                <button
                  disabled={busy || chosen.isFetching}
                  onClick={() => {
                    void act(false)
                  }}
                >
                  {document.status === 'DRAFT' ? 'PDF 생성' : '생성 재시도'}
                </button>
              )}
          </div>
          <details className={styles.document}>
            <summary>계약 원문·특약 확인</summary>
            <div className={styles.documentBody}>
              {sections.map(section => <section key={section.title} className={styles.documentSection}>
                <h3>{section.title}</h3>
                <dl>{section.fields.map(field => <div key={field.key} className={field.long ? styles.longField : styles.field}>
                  <dt>{field.label}</dt><dd className={styles.text}>{field.value || '없음'}</dd>
                </div>)}</dl>
              </section>)}
              {sections.length === 0 && <p>계약 원문 형식을 확인할 수 없습니다. PDF와 상태를 확인해 주세요.</p>}
            </div>
          </details>

          {document.status === 'GENERATING' && (
            <p>
              생성 중에는 5초마다 확인합니다. 작업이 중단됐다면 기본 설정에서
              생성 시작 2분 이후 재시도할 수 있습니다.
            </p>
          )}
          {document.status === 'READY' &&
            orderStatus === 'AWAITING_CONTRACT' &&
            document.version === current.data?.version &&
            !document.signatures.some(
              (s) => s.memberId === member.data?.id,
            ) && (
              <div className={styles.signing}>
                <label>
                  <input
                    type="checkbox"
                    checked={agreed}
                    onChange={(e) => setAgreed(e.target.checked)}
                    disabled={busy || reviewed !== document.fileHash}
                  />
                  현재 버전 PDF의 표준 본문과 특약을 확인하고 동의합니다.
                </label>
                <button
                  disabled={busy || !agreed || reviewed !== document.fileHash}
                  onClick={() => {
                    void act('sign')
                  }}
                >
                  현재 계약에 서명
                </button>
              </div>
            )}
          {document.status === 'SIGNED' && (
            <p className={styles.notice}>
              계약이 성립했습니다.
              {/* 결제 안내는 결제 대기 중에만 보인다. 결제 후·완료·취소 주문에는 남기지 않는다. */}
              {orderStatus === 'AWAITING_PAYMENT' &&
                (policyVersion === 'CONTRACT_V3'
                  ? ' 주문 상세에서 전액 적립금 결제를 진행해 주세요. 결제 대금은 구매 확정까지 보관됩니다.'
                  : ' 선금 결제 기능 연결 전까지 결제는 제공되지 않습니다.')}
            </p>
          )}
        </>
      )}
      <button
        className={styles.refresh}
        disabled={busy || chosen.isFetching}
        onClick={() => {
          void member
            .refetch()
            .then(() =>
              client.invalidateQueries({ queryKey: ['wallet', 'contract'] }),
            )
        }}
      >
        <Icon name="refresh" size={15} />상태 새로고침
      </button>
      {message && <p role="alert">{message}</p>}
    </section>
  )
}
