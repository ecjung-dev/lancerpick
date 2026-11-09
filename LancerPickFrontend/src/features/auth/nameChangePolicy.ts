/** 화면 안내용 판단이다. 실제 허용 여부는 서버 시각과 회원 잠금 아래에서 결정한다. */
export function isDisplayNameChangeLocked(
  availableAt: string | null | undefined,
  now: number,
) {
  if (availableAt == null) return false
  const deadline = Date.parse(availableAt)
  return !Number.isFinite(deadline) || now < deadline
}
