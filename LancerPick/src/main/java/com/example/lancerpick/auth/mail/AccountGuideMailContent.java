package com.example.lancerpick.auth.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 아이디 안내 메일 내용(K1, 메일 내용은 김창재 담당). 랜서픽에는 별도 아이디가 없고 가입한 이메일이 아이디다.
 * 토큰이 없으므로 account_challenges 행 없이 {@link AccountMail#untracked}로 만든다(2026-11-06 정은찬 결정).
 * 링크는 서버 설정의 고정 프런트 주소(FrontendLinks)만 쓴다. 비밀번호·표시 이름 등 다른 회원 정보는 넣지 않는다.
 */
@Component
@Profile("!demo")
public class AccountGuideMailContent {
    static final String SUBJECT = "[랜서픽] 아이디 안내";

    private final FrontendLinks links;

    public AccountGuideMailContent(FrontendLinks links) {
        this.links = links;
    }

    public AccountMail build(String to) {
        String body = """
                랜서픽 아이디 안내

                랜서픽은 가입한 이메일 주소로 로그인합니다.
                이 메일을 받은 주소가 랜서픽 아이디입니다.

                로그인: %s

                비밀번호가 기억나지 않으면 아래에서 재설정할 수 있습니다.
                %s

                아이디 안내를 요청하지 않았다면 이 메일을 무시해 주세요. 계정에는 아무 변화가 없습니다.
                """.formatted(links.pageLink("/login"), links.pageLink("/password-reset/request"));
        return AccountMail.untracked(to, SUBJECT, body);
    }
}
