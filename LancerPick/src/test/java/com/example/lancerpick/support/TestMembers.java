package com.example.lancerpick.support;

import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.member.dto.EmailRules;
import com.example.lancerpick.member.dto.SignupRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 회원 가입 도우미. 2026-11-06부터 POST /api/members는 이메일 인증 증명(emailVerificationProof)이 필수다.
 * 테스트마다 메일 → 링크 → 인증을 거치지 않도록, 인증을 마친 가입 증명 행을 테스트 DB에 직접 넣고 증명 원문을 돌려준다.
 * (테스트 fixture SQL은 운영 코드 규칙의 예외다. 실제 인증 흐름은 SignupEmailIntegrationTests가 검사한다.)
 *
 * 사용 예:
 *   mvc.perform(post("/api/members").with(csrf()).contentType(JSON).content(TestMembers.signupJson(jdbc, email, "abcd1234", "이름")))
 *   memberService.signup(TestMembers.signupRequest(jdbc, email, "abcd1234", "이름"))
 *
 * 증명 행은 발급·인증 2000-01-01, 만료 2999-12-31(UTC)로 넣어 고정 Clock을 쓰는 테스트에서도 쓸 수 있다.
 * member_id가 없는 행이라 회원 삭제를 막지 않는다. 정리가 필요하면 deleteSignupProofs를 쓴다.
 */
public final class TestMembers {

    private TestMembers() {
    }

    /** 이메일에 묶인 사용 가능한 가입 증명을 만들고 원문을 돌려준다. 이메일은 서버와 같은 규칙으로 정규화해 묶는다. */
    public static String verifiedProof(JdbcTemplate jdbc, String email) {
        OneTimeTokens.Issued proof = OneTimeTokens.issue();
        jdbc.update("INSERT INTO account_challenges (purpose, member_id, email, token_hash, credential_version, "
                        + "issued_at, expires_at, verified_at, consumed_at, delivery_status, last_error_code) "
                        + "VALUES ('SIGNUP_EMAIL', NULL, ?, ?, NULL, '2000-01-01 00:00:00', '2999-12-31 00:00:00', "
                        + "'2000-01-01 00:00:00', NULL, 'SENT', NULL)",
                boundEmail(email), proof.hash());
        return proof.value();
    }

    /** POST /api/members 본문(JSON 문자열). 문자열 값은 그대로 넣는다(정규화는 서버가 한다). */
    public static String signupJson(JdbcTemplate jdbc, String email, String password, String displayName) {
        return "{\"email\":\"" + escape(email) + "\",\"password\":\"" + escape(password)
                + "\",\"displayName\":\"" + escape(displayName)
                + "\",\"emailVerificationProof\":\"" + verifiedProof(jdbc, email) + "\"}";
    }

    /** POST /api/members 본문(Map). Map으로 JSON을 만드는 테스트용. */
    public static Map<String, Object> signupBody(JdbcTemplate jdbc, String email, String password, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("displayName", displayName);
        body.put("emailVerificationProof", verifiedProof(jdbc, email));
        return body;
    }

    /** MemberService.signup을 직접 부르는 테스트용. */
    public static SignupRequest signupRequest(JdbcTemplate jdbc, String email, String password, String displayName) {
        return new SignupRequest(email, password, displayName, verifiedProof(jdbc, email));
    }

    /** 이 테스트가 만든 가입 인증 행을 지운다(email LIKE 패턴). */
    public static void deleteSignupProofs(JdbcTemplate jdbc, String emailLikePattern) {
        jdbc.update("DELETE FROM account_challenges WHERE purpose = 'SIGNUP_EMAIL' AND email LIKE ?", emailLikePattern);
    }

    /** 서버(EmailRules)와 같은 정규화. 형식 오류 이메일(형식 검사 테스트)은 소문자·앞뒤 공백 제거만 해서 넣는다. */
    private static String boundEmail(String email) {
        String fallback = email.trim().toLowerCase(Locale.ROOT);
        String bound = EmailRules.normalize(email).orElse(fallback);
        return bound.length() > 254 ? bound.substring(0, 254) : bound;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
