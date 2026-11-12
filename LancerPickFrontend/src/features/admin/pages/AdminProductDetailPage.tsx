import { adminBackPath } from '../navigation'
import { BackLink } from '../../../shared/components/BackLink'
import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useParams } from 'react-router'
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
import { AdminNav } from './AdminNav'
import { MemberLink } from '../components/MemberLink'
import { AdminPager } from '../components/AdminPager'
import styles from './Admin.module.css'
import { StatusBadge } from '../components/StatusBadge'
import { saleStatusTones, toneOf, visibilityTone } from '../statusTone'
import { useMountedRef } from '../../../shared/lib/useMountedRef'

/** 목록에서 넘겨준 돌아갈 주소. 관리자 목록 경로만 허용한다. */
export function AdminProductDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const back = adminBackPath(location.state?.from, '/admin/products')
  const product = useAdminProduct(id)
  const problem = accessProblem(product.error)
  const missing = !isValidProductId(id) || (product.error instanceof ApiError && product.error.status === 404)

  return <section className={styles.page}>
    <p className={styles.eyebrow}>ADMIN</p>
    <AdminNav />
    <BackLink to={back}>상품 운영 목록</BackLink>
    {problem ? <AdminAccessNotice problem={problem} />
      : missing ? <AsyncState title="상품을 찾을 수 없어요" description="상품 번호를 다시 확인해 주세요." />
        : !product.data && product.isError ? <AsyncState title="상품을 불러오지 못했어요" error={product.error} onRetry={() => { void product.refetch() }} />
          : !product.data ? <AsyncState title="상품을 불러오는 중이에요" />
            : <>
              {/* 다시 조회만 실패했으면 입력 중인 사유를 잃지 않도록 화면은 두고 안내만 한다. */}
              {product.isError && <div className={styles.notice} role="alert">
                <p>최신 상태를 불러오지 못했어요. 아래 내용은 마지막으로 확인한 상태예요.</p>
                <button type="button" className={`${styles.button} ${styles.secondary}`} disabled={product.isFetching} onClick={() => { void product.refetch() }}>다시 조회</button>
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
      <p>{product.category.name} · <MemberLink id={product.seller.id} from={`/admin/products/${product.id}`}>판매자 {product.seller.displayName}</MemberLink></p>
      <h1>{product.title}</h1>
      {/* 목록과 같은 기준: 숨김·판매 중지만 배지, 공개·판매 중은 보통 글자. */}
      <p className={styles.statusLine}>
        <StatusBadge tone={visibilityTone(product.isHidden)}>{product.isHidden ? '숨김' : '공개'}</StatusBadge>
        <StatusBadge tone={toneOf(saleStatusTones, product.saleStatus)}>{saleStatusLabel(product.saleStatus)}</StatusBadge>
      </p>
      {refreshing && <p role="status">최신 상태를 확인하고 있어요.</p>}
    </header>
    <section className={styles.card} aria-label="상품 기본 정보">
      <dl className={`${styles.facts} ${styles.factsAligned}`}>
        <dt>상품 번호</dt><dd>#{product.id}</dd>
        <dt>가격</dt><dd className={styles.amount}>{formatWon(product.priceAmount)}</dd>
        <dt>작업 기간</dt><dd>{product.durationDays}일</dd>
        <dt>버전</dt><dd>{product.version}</dd>
        <dt>등록</dt><dd>{formatDateTime(product.createdAt)}</dd>
        <dt>수정</dt><dd>{formatDateTime(product.updatedAt)}</dd>
      </dl>
    </section>
    <section className={styles.block}><h2>서비스 소개</h2><p className={styles.text}>{product.description}</p></section>
    <section className={styles.block}><h2>작업 범위</h2><p className={styles.text}>{product.scopeDescription}</p></section>
    <section className={styles.block}><h2>구매 전 준비사항</h2><p className={styles.text}>{product.preparationDescription}</p></section>
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
  const active = useMountedRef()
  const sending = useRef(false)
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
      <p id="moderation-reason-help" className={styles.help}>{normalizedLength(reason)} / {REASON_MAX.toLocaleString('ko-KR')}자</p>
      <p id="moderation-reason-error" className={styles.fieldError}>{error}</p>
      <button className={styles.button} type="submit">{save.isPending ? '처리 중…' : nextHidden ? '숨기기' : '복구하기'}</button>
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
        : !logs.data ? <AsyncState title="운영 이력을 불러오는 중이에요" />
          : logs.data.items.length === 0 ? <p className={styles.empty}>{page > 0 ? '이 페이지에 이력이 없어요.' : '아직 운영 이력이 없어요.'}</p>
            : <div className={styles.tableWrap}><table className={styles.table}>
              <thead><tr><th scope="col">처리</th><th scope="col">사유</th><th scope="col">관리자</th><th scope="col">처리일</th></tr></thead>
              <tbody>{logs.data.items.map((log) => <tr key={log.id}>
                <td data-label="처리"><StatusBadge tone={visibilityTone(log.action === 'HIDE')}>{log.action === 'HIDE' ? '숨김' : '복구'}</StatusBadge></td>
                <td className={styles.cellText} data-label="사유">{log.reason}</td>
                <td className={styles.cellId} data-label="관리자">관리자 #{log.adminId}</td>
                <td className={styles.cellDate} data-label="처리일"><time dateTime={log.createdAt}>{formatDateTime(log.createdAt)}</time></td>
              </tr>)}</tbody>
            </table></div>}
    {logs.data && <AdminPager page={page} totalPages={logs.data.totalPages} busy={logs.isFetching} onPage={setPage} label="운영 이력 페이지" />}
  </section>
}
