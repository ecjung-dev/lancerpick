package com.example.lancerpick.auth.mail;

import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기존 회원 이메일 인증 메일 내용(K1, 메일 내용은 김창재 담당).
 * 링크 = 서버 설정의 고정 프런트 주소 + /mypage/email-verification#token=원문(FrontendLinks). 비밀번호는 넣지 않는다.
 */
@Component
@Profile("!demo")
public class ExistingEmailMailContent {
    static final String SUBJECT = "[랜서픽] 이메일 인증 안내";

    private final FrontendLinks links;

    public ExistingEmailMailContent(FrontendLinks links) {
        this.links = links;
    }

    public AccountMail build(long challengeId, String to, String token, Duration validFor) {
        String link = links.tokenLink("/mypage/email-verification", token);
        String body = """
                랜서픽 이메일 인증 안내

                아래 링크를 열어 이 이메일 주소를 인증해 주세요.
                링크는 %d분 동안 한 번만 쓸 수 있고, 요청한 계정으로 로그인한 상태에서만 인증됩니다.

                %s

                인증을 요청하지 않았다면 이 메일을 무시해 주세요. 계정에는 아무 변화가 없습니다.
                """.formatted(validFor.toMinutes(), link);
        return new AccountMail(challengeId, to, SUBJECT, body);
    }
}
