package com.example.lancerpick.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 요청 제한 키(auth_request_limits.subject_hmac)를 만든다. HMAC-SHA256(서버 비밀키, 목적 + 이메일).
 * HMAC은 비밀키를 섞은 해시라서, DB가 유출돼도 키 없이는 어떤 이메일의 행인지 맞춰 볼 수 없다(3인 설계 7.3).
 *
 * 키는 lancerpick.auth.request-limit-key(환경변수 AUTH_REQUEST_LIMIT_KEY)로 각자 설정한다. 저장소에 넣지 않는다.
 * 설정하지 않으면 서버가 켜질 때 임의 키를 만든다. 이때는 서버를 다시 켜면 기존 제한 행과 이어지지 않는다(개발용 한계).
 */
@Component
@Profile("!demo")
public class AuthSubjectHasher {
    private static final Logger log = LoggerFactory.getLogger(AuthSubjectHasher.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public AuthSubjectHasher(@Value("${lancerpick.auth.request-limit-key:}") String configuredKey) {
        byte[] keyBytes;
        if (configuredKey == null || configuredKey.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            // 키 값은 로그에 남기지 않는다.
            log.warn("lancerpick.auth.request-limit-key is not set; using a random key until restart");
        } else {
            keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /** 목적(예: PASSWORD_RESET)과 정규화된 이메일로 32바이트 키를 만든다. 목적이 다르면 다른 키가 된다. */
    public byte[] subject(String purpose, String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);   // Mac은 여러 스레드가 함께 쓰면 안 되므로 호출마다 만든다
            mac.init(key);
            return mac.doFinal((purpose + "\n" + normalizedEmail).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
