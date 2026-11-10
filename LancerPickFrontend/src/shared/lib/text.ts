/** API 10절의 Unicode White_Space 집합. 비밀번호에는 적용하지 않는다. */
export function normalizeText(value: string) {
  const characters = [...value.replace(/\r\n?/g, '\n')]
  let start = 0
  let end = characters.length
  while (start < end && isWhiteSpace(characters[start])) start++
  while (end > start && isWhiteSpace(characters[end - 1])) end--
  return characters.slice(start, end).join('')
}

export function codePointLength(value: string) { return [...value].length }

/** 서버와 같은 기준의 입력 길이: 앞뒤 공백을 정리한 뒤 코드 포인트로 센다(글자 수 안내·최대 길이 검사용). */
export function normalizedLength(value: string) { return codePointLength(normalizeText(value)) }

function isWhiteSpace(character: string) {
  const c = character.codePointAt(0) ?? 0
  return (c >= 0x0009 && c <= 0x000D) || c === 0x0020 || c === 0x0085 || c === 0x00A0
    || c === 0x1680 || (c >= 0x2000 && c <= 0x200A) || c === 0x2028 || c === 0x2029
    || c === 0x202F || c === 0x205F || c === 0x3000
}
