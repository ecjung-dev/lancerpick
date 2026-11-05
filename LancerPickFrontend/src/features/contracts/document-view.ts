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
