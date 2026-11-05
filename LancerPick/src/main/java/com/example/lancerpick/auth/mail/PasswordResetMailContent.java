package com.example.lancerpick.auth.mail;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 메일 내용(4인 설계 §4: 메일 내용은 김창재 담당).
 * 링크 = 서버 설정의 고정 프런트 주소 + /password-reset#token=원문 (3인 설계 7.1).
 * - 요청의 Host 헤더나 사용자가 보낸 returnUrl을 쓰지 않는다(남의 사이트로 토큰을 보내는 링크를 막는다).
 * - 토큰은 # 뒤(fragment)에 둔다. fragment는 브라우저가 서버로 보내지 않아 접속 로그·Referer에 남지 않는다.
 * 비밀번호는 메일에 넣지 않는다.
 */
@Component
@Profile("!demo")
public class PasswordResetMailContent {
    static final String SUBJECT = "[랜서픽] 비밀번호 재설정 안내";

    private final String resetPageUrl;

    public PasswordResetMailContent(@Value("${lancerpick.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.resetPageUrl = validatedBaseUrl(frontendBaseUrl) + "/password-reset";
    }

    public AccountMail build(long challengeId, String to, String token, Duration validFor) {
        String link = resetPageUrl + "#token=" + token;
        String body = """
                랜서픽 비밀번호 재설정 안내

                아래 링크를 열어 새 비밀번호를 설정해 주세요.
                링크는 %d분 동안 한 번만 쓸 수 있습니다.

                %s

                비밀번호 재설정을 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(validFor.toMinutes(), link);
        return new AccountMail(challengeId, to, SUBJECT, body);
    }

    /** 서버가 켜질 때 한 번 확인한다. http/https, 호스트 있음, 아이디·쿼리·fragment 없음. 끝의 / 는 뗀다. */
    private static String validatedBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("lancerpick.frontend.base-url is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("lancerpick.frontend.base-url must be an http(s) origin without query or fragment");
        }
        String text = uri.toString();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
