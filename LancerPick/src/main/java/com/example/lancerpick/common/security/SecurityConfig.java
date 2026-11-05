package com.example.lancerpick.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;

/** 공개 조회·가입·로그인과 인증 필요 API, 관리자 전용 API의 접근 정책. */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errors,
            Environment environment, ObjectProvider<AuthVersionLookup> authVersionLookup) throws Exception {
        // 비밀번호 변경으로 버전이 바뀐 세션을 폐기한다. DB가 없는 demo·HTTP 단위 테스트에는 조회 Bean이 없어 넣지 않는다.
        // CsrfFilter 다음에 두어, 폐기된 세션의 변경 요청이 403(CSRF)이 아니라 401(로그인 필요)로 끝나게 한다.
        AuthVersionLookup lookup = authVersionLookup.getIfAvailable();
        if (lookup != null) {
            http.addFilterAfter(new SessionAuthVersionFilter(lookup), CsrfFilter.class);
        }
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // 세션 기반 기본 CSRF 보호를 유지한다. /api/auth/csrf에서 토큰을 받아 헤더로 보낸다.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(request, response, 401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(request, response, 403,
                                exception instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                                exception instanceof CsrfException ? "요청 인증 정보를 갱신해 주세요." : "접근 권한이 없습니다.")))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/categories", "/api/products", "/api/products/*", "/api/products/*/reviews", "/api/sellers/*").permitAll();
                    // 기본 로컬 실행에도 메인 예시 조회를 제공한다. 보호 API의 인증 규칙은 유지한다.
                    if (environment.acceptsProfiles(Profiles.of("(default | demo) & !prod"))) {
                        auth.requestMatchers(HttpMethod.GET, "/api/demo/**").permitAll();
                    }
                    // 이 경로를 여는 것은 로그인/가입 구현을 대신하지 않는다. CSRF는 적용된다.
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/members").permitAll();
                    // 비밀번호 재설정은 로그아웃 상태에서 쓴다. CSRF는 그대로 적용된다(비로그인 API라도 해제하지 않음).
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/password-reset-requests", "/api/auth/password-resets").permitAll();
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }
}
