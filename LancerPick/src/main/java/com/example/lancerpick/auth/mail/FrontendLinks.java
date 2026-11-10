package com.example.lancerpick.auth.mail;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 계정 메일 링크의 주소. 서버 설정의 고정 프런트 주소(lancerpick.frontend.base-url)만 쓴다(3인 설계 7.1).
 * - 요청의 Host 헤더나 사용자가 보낸 returnUrl을 쓰지 않는다(남의 사이트로 토큰을 보내는 링크를 막는다).
 * - 토큰은 # 뒤(fragment)에 둔다. fragment는 브라우저가 서버로 보내지 않아 접속 로그·Referer에 남지 않는다.
 * 비밀번호 재설정·가입 인증 메일이 같은 규칙을 쓰도록 한 곳에 둔다.
 */
@Component
@Profile("!demo")
public class FrontendLinks {
    private final String baseUrl;

    public FrontendLinks(@Value("${lancerpick.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.baseUrl = validatedBaseUrl(frontendBaseUrl);
    }

    /** 예: tokenLink("/password-reset", t) → http://localhost:5173/password-reset#token=t */
    public String tokenLink(String path, String token) {
        return baseUrl + path + "#token=" + token;
    }

    /** 토큰 없는 화면 주소. 예: pageLink("/login") → http://localhost:5173/login. path는 코드에 고정된 값만 쓴다. */
    public String pageLink(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//"))
            throw new IllegalArgumentException("path must be an absolute site path");
        return baseUrl + path;
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
