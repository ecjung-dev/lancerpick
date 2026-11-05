import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../shared/api/client'
import { getMemberSession } from '../../auth/api'
import { downloadContract, generateContract, verifyContractPdf } from '../api'
import { prepareContract, signContract } from '../api'
import { useContract, useContractMember } from '../queries'
import { contractFields } from '../document-view'
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
}: {
  orderId: string
  sellerId: string
  orderStatus: string
  policyVersion?: string
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
      <h2>전자계약서</h2>
      {member.data?.id === sellerId &&
        orderStatus === 'AWAITING_CONTRACT' &&
        current.data?.status !== 'SIGNED' &&
        (current.isSuccess ||
          (current.error instanceof ApiError &&
            current.error.status === 404)) && (
          <div>
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
      {chosen.isError && (
        <p role="alert">
          {chosen.error instanceof ApiError && chosen.error.status === 404
            ? '아직 작성된 계약이 없거나 접근할 수 없습니다.'
            : chosen.error.message}
        </p>
      )}
      {document && (
        <>
          <label>
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
          <p role="status">
            {labels[document.status] ?? '계약 상태 확인 필요'}
          </p>
          <p>서명 {document.signatures.length}/2</p>
          <details>
            <summary>계약 원문·특약 확인</summary>
            {contractFields(document.canonicalContent).map((field) => (
              <div key={field.label}>
                <h3>{field.label}</h3>
                <p className={styles.text}>{field.value || '없음'}</p>
              </div>
            ))}
            {contractFields(document.canonicalContent).length === 0 && (
              <p>
                계약 원문 형식을 확인할 수 없습니다. PDF와 상태를 확인해 주세요.
              </p>
            )}
          </details>
          <div className={styles.actions}>
            {document.fileHash &&
              ['READY', 'SIGNED', 'SUPERSEDED'].includes(document.status) && (
                <button
                  disabled={busy || chosen.isFetching}
                  onClick={() => {
                    void act(true)
                  }}
                >
                  PDF 다운로드
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
              <div>
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
            <p>
              계약이 성립했습니다.{' '}
              {policyVersion === 'CONTRACT_V3' ? '전액 보관' : '선금'} 결제 기능
              연결 전까지 결제는 제공되지 않습니다.
            </p>
          )}
        </>
      )}
      <button
        disabled={busy || chosen.isFetching}
        onClick={() => {
          void member
            .refetch()
            .then(() =>
              client.invalidateQueries({ queryKey: ['wallet', 'contract'] }),
            )
        }}
      >
        상태 새로고침
      </button>
      {message && <p role="alert">{message}</p>}
    </section>
  )
}
