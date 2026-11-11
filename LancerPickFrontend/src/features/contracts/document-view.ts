const labels: Record<string, string> = {
  orderId: '주문번호',
  templateVersion: '표준 양식',
  buyerId: '구매자 회원번호',
  buyerName: '구매자',
  sellerId: '판매자 회원번호',
  sellerName: '판매자',
  productTitle: '서비스',
  total: '총금액(원)',
  fundingMode: '대금 처리 방식',
  feeRateBps: '정상 수수료(bps, 1000=10%)',
  deposit: '선금(원)',
  balance: '잔금(원)',
  durationDays: '소요일',
  description: '서비스 설명',
  scope: '작업 범위',
  preparation: '준비사항',
  originalTerms: '신청 당시 조건',
  requirements: '구매자 요구사항',
  standardBody: '표준 계약 본문',
  specialTerms: '특약',
}
export function contractFields(
  canonical: string,
): { label: string; value: string }[] {
  try {
    const parsed: unknown = JSON.parse(canonical)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))
      return []
    return Object.entries(labels).flatMap(([key, label]) => {
      const value = (parsed as Record<string, unknown>)[key]
      return typeof value === 'string' || typeof value === 'number'
        ? [
            {
              label,
              value:
                key === 'fundingMode' && value === 'FULL_HOLD'
                  ? '전액 보관'
                  : String(value),
            },
          ]
        : []
    })
  } catch {
    return []
  }
}


const groups = [
  { title: '거래 정보', keys: ['orderId', 'templateVersion', 'productTitle'] },
  { title: '계약 당사자', keys: ['buyerName', 'buyerId', 'sellerName', 'sellerId'] },
  { title: '금액 및 작업 기간', keys: ['total', 'fundingMode', 'feeRateBps', 'deposit', 'balance', 'durationDays'] },
  { title: '서비스 및 요청사항', keys: ['description', 'scope', 'preparation', 'requirements'] },
  { title: '계약 본문 및 특약', keys: ['originalTerms', 'standardBody', 'specialTerms'] },
]

/** Display only. Canonical text and hashes sent to the server are never changed. */
export function contractSections(canonical: string) {
  const fields = contractFields(canonical)
  return groups.flatMap(group => {
    const items = group.keys.flatMap(key => {
      const field = fields.find(item => item.label === labels[key])
      if (!field) return []
      let { label, value } = field
      if (['total', 'deposit', 'balance'].includes(key) && /^\d+$/.test(value) && Number.isSafeInteger(Number(value))) {
        label = label.replace('(원)', '')
        value = `${Number(value).toLocaleString('ko-KR')}원`
      }
      if (key === 'durationDays' && /^\d+$/.test(value)) { label = '작업 기간'; value += '일' }
      if (key === 'feeRateBps' && /^\d+$/.test(value) && Number.isSafeInteger(Number(value))) {
        label = '정상 수수료율'
        value = `${Number(value) / 100}% (${value} bps)`
      }
      return [{ key, label, value, long: ['description', 'scope', 'preparation', 'requirements', 'originalTerms', 'standardBody', 'specialTerms'].includes(key) }]
    })
    return items.length ? [{ title: group.title, fields: items }] : []
  })
}
