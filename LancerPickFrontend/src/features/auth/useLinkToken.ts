import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { readLinkToken } from './linkToken.ts'

/**
 * 메일 링크 화면 공통: 처음 그릴 때 주소의 #token=... 을 읽어 화면 상태(메모리)에만 두고, 곧바로 주소창에서 지운다
 * (방문 기록·화면 공유·주소 복사로 새지 않게). 토큰이 없거나 형식이 틀리면 null.
 * 링크를 여는 것(GET)만으로는 서버에 아무것도 보내지 않는다. 사용은 화면의 버튼(POST)으로만 한다.
 */
export function useLinkToken(): string | null {
  const location = useLocation()
  const navigate = useNavigate()
  const [token] = useState(() => readLinkToken(location.hash))

  useEffect(() => {
    if (location.hash) navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: location.state })
  }, [location.hash, location.pathname, location.search, location.state, navigate])

  return token
}
