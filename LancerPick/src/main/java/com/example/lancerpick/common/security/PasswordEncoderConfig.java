package com.example.lancerpick.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더. 정책은 docs/api-spec.md 10절 "비밀번호 원문 정책"을 따른다.
 * DelegatingPasswordEncoder(기본 BCrypt)를 사용하며 저장값에 {bcrypt} 접두어가 붙는다.
 */
@Configuration
public class PasswordEncoderConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
