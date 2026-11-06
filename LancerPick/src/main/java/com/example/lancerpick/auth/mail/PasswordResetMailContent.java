package com.example.lancerpick.auth.mail;

import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 메일 내용(4인 설계 §4: 메일 내용은 김창재 담당).
 * 링크 = 서버 설정의 고정 프런트 주소 + /password-reset#token=원문 (FrontendLinks).
 * 비밀번호는 메일에 넣지 않는다.
 */
@Component
@Profile("!demo")
public class PasswordResetMailContent {
    static final String SUBJECT = "[랜서픽] 비밀번호 재설정 안내";

    private final FrontendLinks links;

    public PasswordResetMailContent(FrontendLinks links) {
        this.links = links;
    }

    public AccountMail build(long challengeId, String to, String token, Duration validFor) {
        String link = links.tokenLink("/password-reset", token);
        String body = """
                랜서픽 비밀번호 재설정 안내

                아래 링크를 열어 새 비밀번호를 설정해 주세요.
                링크는 %d분 동안 한 번만 쓸 수 있습니다.

                %s

                비밀번호 재설정을 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(validFor.toMinutes(), link);
        return new AccountMail(challengeId, to, SUBJECT, body);
    }
}
