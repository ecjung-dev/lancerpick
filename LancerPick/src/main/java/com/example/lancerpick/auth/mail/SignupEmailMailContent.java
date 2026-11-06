package com.example.lancerpick.auth.mail;

import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 가입 이메일 인증 메일 내용(3인 설계 §5.1, 메일 내용은 김창재 담당).
 * 링크 = 서버 설정의 고정 프런트 주소 + /signup/verify#token=원문 (FrontendLinks).
 * 링크를 여는 것만으로는 인증되지 않는다. 화면에서 "인증하기"를 눌러야 POST로 인증한다.
 * 이미 가입된 주소에도 같은 메일을 보낸다(요청 단계에서 가입 여부를 드러내지 않기 위해). 가입 여부 안내는 인증 뒤에 화면이 한다.
 */
@Component
@Profile("!demo")
public class SignupEmailMailContent {
    static final String SUBJECT = "[랜서픽] 회원가입 이메일 인증 안내";

    private final FrontendLinks links;

    public SignupEmailMailContent(FrontendLinks links) {
        this.links = links;
    }

    public AccountMail build(long challengeId, String to, String token, Duration validFor) {
        String link = links.tokenLink("/signup/verify", token);
        String body = """
                랜서픽 회원가입 이메일 인증 안내

                아래 링크를 열고 "인증하기"를 누르면 가입을 계속할 수 있습니다.
                링크는 %d분 동안 한 번만 쓸 수 있습니다.

                %s

                랜서픽 가입을 요청하지 않았다면 이 메일을 무시해 주세요. 계정은 만들어지지 않습니다.
                """.formatted(validFor.toMinutes(), link);
        return new AccountMail(challengeId, to, SUBJECT, body);
    }
}
