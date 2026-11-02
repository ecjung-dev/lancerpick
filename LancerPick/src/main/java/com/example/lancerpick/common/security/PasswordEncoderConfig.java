package com.example.lancerpick.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 정책: claude/password-policy.md (DelegatingPasswordEncoder, 기본 BCrypt) */
@Configuration
public class PasswordEncoderConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
