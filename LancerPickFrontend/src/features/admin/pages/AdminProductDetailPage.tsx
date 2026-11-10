import { adminBackPath } from '../navigation'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../../../shared/api/client'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatDateTime, formatWon } from '../../../shared/lib/format'
import { normalizedLength } from '../../../shared/lib/text'
import { usePageSearchParam } from '../../../shared/lib/usePageSearchParam'
import { captureAccountScope, isUnclearResult } from '../../auth/session'
import { changeVisibility } from '../api'
import { adminKeys, useAdminProduct, useModerationLogs } from '../queries'
import type { AdminProductDetail, VisibilityInput } from '../types'
import { REASON_MAX, isValidProductId, saleStatusLabel, validateReason } from '../validation'
import { accessProblem } from '../access'
import { AdminAccessNotice } from './AdminAccess'
import styles from './Admin.module.css'

/** 목록에서 넘겨준 돌아갈 주소. 관리자 목록 경로만 허용한다. */
export function AdminProductDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const back = adminBackPath(location.state?.from, '/admin/products')
  const product = useAdminProduct(id)
  const problem = accessProblem(product.error)
  const missing = !isValidProductId(id) || (product.error instanceof ApiError && product.error.status === 404)

  return <section className={styles.page}>
    <Link to={back}>← 상품 운영 목록</Link>
    {problem ? <AdminAccessNotice problem={problem} />
      : missing ? <AsyncState title="상품을 찾을 수 없어요" description="상품 번호를 다시 확인해 주세요." />
        : !product.data && product.isError ? <AsyncState title="상품을 불러오지 못했어요" error={product.error} onRetry={() => { void product.refetch() }} />
          : !product.data ? <AsyncState title="상품을 불러오고 있어요" />
            : <>
              {/* 다시 조회만 실패했으면 입력 중인 사유를 잃지 않도록 화면은 두고 안내만 한다. */}
              {product.isError && <div className={styles.notice} role="alert">
                <p>최신 상태를 불러오지 못했어요. 아래 내용은 마지막으로 확인한 상태예요.</p>
                <button type="button" disabled={product.isFetching} onClick={() => { void product.refetch() }}>다시 불러오기</button>
              </div>}
              <ProductSummary product={product.data} refreshing={product.isFetching} />
              <VisibilityForm key={product.data.id} product={product.data} />
              <ModerationLogs id={product.data.id} />
            </>}
  </section>
}

function ProductSummary({ product, refreshing }: { product: AdminProductDetail; refreshing: boolean }) {
  return <>
    <header className={styles.heading}>
      <p>{product.category.name} · 판매자 {product.seller.displayName}</p>
      <h1>{product.title}</h1>
      <p>
        <span className={product.isHidden ? styles.hidden : styles.visible}>{product.isHidden ? '숨김' : '공개'}</span>
        <span className={styles.badge}>{saleStatusLabel(product.saleStatus)}</span>
        {formatWon(product.priceAmount)} · {product.durationDays}일 · 버전 {product.version}
      </p>
      {refreshing && <p role="status">최신 상태를 확인하고 있어요.</p>}
    </header>
    <section className={styles.block}><h2>서비스 소개</h2><p className={styles.text}>{product.description}</p></section>
    <section className={styles.block}><h2>작업 범위</h2><p className={styles.text}>{product.scopeDescription}</p></section>
    <section className={styles.block}><h2>구매 전 준비사항</h2><p className={styles.text}>{product.preparationDescription}</p></section>
    <small>등록 {formatDateTime(product.createdAt)} · 수정 {formatDateTime(product.updatedAt)}</small>
  </>
}

/**
 * 숨김·복구. 실패·동시 변경·늦은 응답 처리:
 * - 처리 중에는 버튼을 막고, 실패해도 자동으로 다시 보내지 않는다(retry: false).
 * - 409(다른 관리자·판매자가 먼저 바꿈): 입력한 사유는 남기고 최신 상품을 다시 불러온다. 자동으로 덮어쓰지 않는다.
 * - 통신 오류·5xx: 서버가 처리했을 수 있으므로 실패로 단정하지 않고 상품·이력을 다시 조회한다.
 * - 응답이 화면을 떠난 뒤 도착하면 화면 상태는 바꾸지 않는다(캐시만 갱신).
 * - 다른 관리자가 먼저 같은 상태로 바꿨으면 서버는 이력 없이 현재 상태를 돌려준다(200).
 *   그래서 "내가 바꿨다"가 아니라 "지금 이 상태다"로 안내하고 운영 이력을 다시 불러온다.
 * - 빠른 두 번 클릭은 렌더링 전에도 막도록 ref로 한 번 더 막는다.
 */
function VisibilityForm({ product }: { product: AdminProductDetail }) {
  const client = useQueryClient()
  const active = useRef(true)
  const sending = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const nextHidden = !product.isHidden

  const save = useMutation({
    retry: false,
    mutationFn: (input: VisibilityInput & { scope: () => boolean }) => changeVisibility(product.id, input, input.scope),
    onSettled: () => { sending.current = false },
    onSuccess: (updated, input) => {
      // 기다리는 동안 계정이 바뀌었으면 이전 계정의 결과를 화면·캐시에 반영하지 않는다.
      if (!input.scope()) return
      client.setQueryData(adminKeys.detail(product.id), updated)
      if (active.current) setReason('')
      void client.invalidateQueries({ queryKey: ['admin', 'products'] })
      void client.invalidateQueries({ queryKey: ['admin', 'logs', product.id] })
    },
    onError: async (failure, input) => {
      if (!input.scope()) return
      if (failure instanceof ApiError && failure.code === 'PRODUCT_VERSION_CONFLICT') {
        await client.invalidateQueries({ queryKey: adminKeys.detail(product.id) })
        if (active.current) setNotice('다른 곳에서 상품이 먼저 변경됐어요. 최신 상태를 확인한 뒤 다시 시도해 주세요.')
        return
      }
      if (isUnclearResult(failure)) {
        await Promise.all([
          client.invalidateQueries({ queryKey: adminKeys.detail(product.id) }),
          client.invalidateQueries({ queryKey: ['admin', 'logs', product.id] }),
        ])
        if (active.current) setNotice('처리 결과를 확인하지 못해 최신 상태를 다시 불러왔어요. 상태를 확인한 뒤 필요하면 다시 시도해 주세요.')
        return
      }
      if (active.current) setError(failure instanceof ApiError ? failure.message : '처리하지 못했어요. 다시 시도해 주세요.')
    },
  })

  const saveProblem = accessProblem(save.error)
  if (saveProblem) return <AdminAccessNotice problem={saveProblem} />

  function submit(event: FormEvent) {
    event.preventDefault()
    if (save.isPending || sending.current) return
    const result = validateReason(reason)
    setError(result.error)
    setNotice(undefined)
    if (result.error) return
    sending.current = true
    save.mutate({ hidden: nextHidden, reason: result.reason, expectedVersion: product.version, scope: captureAccountScope(client) })
  }

  return <form className={styles.form} onSubmit={submit} noValidate>
    <h2>{nextHidden ? '상품 숨기기' : '숨김 복구하기'}</h2>
    <p>{nextHidden
      ? '숨기면 공개 목록과 신규 주문에서 빠져요. 판매 상태와 진행 중인 거래는 바뀌지 않아요.'
      : '복구하면 다시 공개돼요. 판매 중지 상태는 판매자가 따로 관리해요.'}</p>
    {notice && <p className={styles.notice} role="status">{notice}</p>}
    <fieldset disabled={save.isPending}>
      <label htmlFor="moderation-reason">사유 <span>필수</span></label>
      <textarea id="moderation-reason" rows={4} value={reason} aria-invalid={!!error} aria-describedby="moderation-reason-help moderation-reason-error"
        onChange={(e) => { setReason(e.target.value); setError(undefined) }} placeholder="운영 기록에 남는 사유예요." />
      <p id="moderation-reason-help" className={styles.help}>{normalizedLength(reason)} / {REASON_MAX.toLocaleString()}자</p>
      <p id="moderation-reason-error" className={styles.fieldError}>{error}</p>
      <button type="submit">{save.isPending ? '처리 중…' : nextHidden ? '숨기기' : '복구하기'}</button>
    </fieldset>
    {save.isSuccess && <p className={styles.success} role="status">{save.data.isHidden ? '숨김 상태예요.' : '공개 상태예요.'} 처리 내역은 운영 이력에서 확인할 수 있어요.</p>}
  </form>
}

function ModerationLogs({ id }: { id: string }) {
  const [page, setPage] = usePageSearchParam()
  const logs = useModerationLogs(id, page)
  const problem = accessProblem(logs.error)
  return <section className={styles.block}>
    <h2>운영 이력</h2>
    {problem ? <AdminAccessNotice problem={problem} />
      : logs.isError ? <AsyncState title="운영 이력을 불러오지 못했어요" error={logs.error} onRetry={() => { void logs.refetch() }} />
        : !logs.data ? <AsyncState title="운영 이력을 불러오고 있어요" />
          : logs.data.items.length === 0 ? <p>{page > 0 ? '이 페이지에 이력이 없어요.' : '아직 운영 이력이 없어요.'}</p>
            : <ul className={styles.list}>{logs.data.items.map((log) => <li key={log.id}>
              <strong>{log.action === 'HIDE' ? '숨김' : '복구'}</strong>
              <time dateTime={log.createdAt}>{formatDateTime(log.createdAt)}</time>
              <p className={styles.text}>{log.reason}</p>
              <small>관리자 #{log.adminId}</small>
            </li>)}</ul>}
    {logs.data && logs.data.items.length === 0 && page > 0 && <button onClick={() => setPage(0)}>첫 페이지로</button>}
    {logs.data && logs.data.totalPages > 1 && <nav className={styles.pager} aria-label="운영 이력 페이지">
      <button disabled={logs.isFetching || page === 0} onClick={() => setPage(page - 1)}>이전</button>
      <span>{page + 1}페이지</span>
      <button disabled={logs.isFetching || page + 1 >= logs.data.totalPages} onClick={() => setPage(page + 1)}>다음</button>
    </nav>}
  </section>
}
