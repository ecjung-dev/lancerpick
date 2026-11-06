import { Link } from 'react-router'
import { requestSignupEmail } from '../api'
import { EmailLinkRequestForm } from '../components/EmailLinkRequestForm'
import { useCurrentMember } from '../queries'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

/**
 * /signup — 이메일 인증 가입 1단계: 인증 메일 요청(3인 설계 §5.1).
 * 이 단계에서는 비밀번호를 받지 않고 회원·지갑도 만들지 않는다. 메일 링크(/signup/verify)에서 인증한 뒤 나머지를 입력한다.
 * 가입 여부와 상관없이 같은 안내를 보여준다(이미 가입된 주소인지는 메일을 받은 본인이 인증한 뒤에만 알려 준다).
 * 동작 규칙(1회 전송·60초 잠금·불명확한 결과 처리)은 EmailLinkRequestForm을 따른다.
 */
export function SignupPage() {
  const current = useCurrentMember()

  if (current.data) {
    return <AuthLayout title="이미 로그인되어 있어요">
      <p className={styles.notice}>다른 계정을 만들려면 먼저 로그아웃해 주세요.</p>
      <div className={styles.links}><Link to="/">서비스 둘러보기</Link></div>
    </AuthLayout>
  }

  return <AuthLayout title="회원가입" description="이메일을 인증하면 가입할 수 있어요. 가입하면 테스트 적립금 1,000,000원이 지급돼요. 실제 현금 거래는 없어요.">
    <EmailLinkRequestForm
      idPrefix="signup"
      request={requestSignupEmail}
      submitLabel="인증 메일 받기"
      successText={'입력한 주소로 가입 인증 메일을 보냈어요. 메일의 링크를 열고 "인증하기"를 누르면 가입을 계속할 수 있어요. '
        + '링크는 15분 동안 한 번만 쓸 수 있어요. 메일이 오지 않으면 스팸함을 확인한 뒤 잠시 후 다시 요청해 주세요.'} />
    <p className={styles.help}>이메일은 로그인 아이디로 사용해요.</p>
    <p className={styles.switch}>이미 회원이신가요? <Link to="/login">로그인</Link></p>
  </AuthLayout>
}
