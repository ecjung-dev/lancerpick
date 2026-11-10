import { Link } from 'react-router'
import { requestAccountGuide } from '../api'
import { EmailLinkRequestForm } from '../components/EmailLinkRequestForm'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

/**
 * /account-guide — 아이디 안내(K1). 랜서픽은 별도 아이디 없이 가입한 이메일로 로그인한다.
 * 이메일을 입력하면 그 주소가 가입한 이메일인 경우에만 그 주소로 안내 메일을 보낸다. 화면은 가입 여부를 알려 주지 않는다.
 * 잊어버린 이메일을 찾아 주는 기능이 아니므로 그렇게 표현하지 않는다(3인 설계 1절).
 * 동작 규칙(같은 안내·1회 전송·60초 잠금)은 EmailLinkRequestForm을 따른다.
 */
export function AccountGuidePage() {
  return <AuthLayout title="아이디 안내" description="랜서픽은 가입한 이메일 주소가 아이디예요. 가입에 쓴 것 같은 이메일을 입력하면, 맞는 경우 그 주소로 안내 메일을 보내드려요.">
    <EmailLinkRequestForm
      idPrefix="guide"
      autoComplete="username"
      request={requestAccountGuide}
      submitLabel="아이디 안내 메일 받기"
      successText={'입력한 주소가 가입한 이메일이면 아이디 안내 메일을 보냈어요. 메일이 오지 않으면 스팸함을 확인하고, '
        + '다른 이메일 주소로 다시 시도해 보세요. 가입에 쓴 이메일을 전혀 모르면 찾아드릴 수 없어요.'} />
    <p className={styles.switch}>이메일은 아는데 비밀번호를 잊으셨나요? <Link to="/password-reset/request">비밀번호 재설정</Link></p>
    <p className={styles.switch}><Link to="/login">로그인으로 돌아가기</Link></p>
  </AuthLayout>
}
