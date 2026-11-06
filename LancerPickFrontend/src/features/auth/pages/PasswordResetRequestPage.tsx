import { Link } from 'react-router'
import { requestPasswordReset } from '../api'
import { EmailLinkRequestForm } from '../components/EmailLinkRequestForm'
import { AuthLayout } from './AuthLayout'
import styles from './AuthPage.module.css'

/**
 * /password-reset/request — 비밀번호 재설정 메일 요청. 동작 규칙(같은 안내·1회 전송·60초 잠금)은 EmailLinkRequestForm을 따른다.
 */
export function PasswordResetRequestPage() {
  return <AuthLayout title="비밀번호 재설정" description="가입한 이메일을 입력하면 비밀번호를 다시 설정할 수 있는 링크를 보내드려요.">
    <EmailLinkRequestForm
      idPrefix="reset"
      autoComplete="username"
      request={requestPasswordReset}
      submitLabel="재설정 메일 받기"
      successText={'입력한 주소로 안내할 수 있는 경우 비밀번호 재설정 메일을 보냈어요. 링크는 15분 동안 한 번만 쓸 수 있어요. '
        + '메일이 오지 않으면 스팸함을 확인한 뒤 잠시 후 다시 요청해 주세요.'} />
    <p className={styles.switch}>비밀번호가 기억나셨나요? <Link to="/login">로그인</Link></p>
  </AuthLayout>
}
