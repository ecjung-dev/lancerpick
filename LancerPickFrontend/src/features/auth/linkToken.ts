/** 서버 일회용 토큰 형식: URL-safe Base64 43자(32바이트). 다른 값은 서버로 보내지 않고 바로 "유효하지 않은 링크"로 안내한다. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/

/**
 * 메일 링크의 주소 뒤쪽(#token=...)에서 토큰을 꺼낸다. 없거나 형식이 틀리면 null.
 * 토큰을 # 뒤(fragment)에 두는 이유: 브라우저가 서버로 보내지 않아 접속 로그·Referer에 남지 않는다(3인 설계 7.1).
 * 꺼낸 토큰은 화면 상태(메모리)에만 두고 localStorage·주소·로그에 남기지 않는다.
 * 비밀번호 재설정(/password-reset)과 가입 인증(/signup/verify) 링크가 함께 쓴다.
 */
export function readLinkToken(hash: string): string | null {
  const text = hash.startsWith('#') ? hash.slice(1) : hash
  const token = new URLSearchParams(text).get('token')
  return token !== null && TOKEN_PATTERN.test(token) ? token : null
}

/** 다시 요청하기 버튼을 막는 시간(초). 서버 제한(이메일+목적당 최소 60초)과 같다. 화면 편의용이고 실제 제한은 서버가 한다. */
export const MAIL_REQUEST_COOLDOWN_SECONDS = 60
