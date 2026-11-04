package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.dto.LoginRequest;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.LoginMember;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 로그인·로그아웃 (API 2절). formLogin을 쓰지 않으므로 인증 정보를 직접 세션에 저장한다.
 */
@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 인증 기능을 만들지 않는다
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final CsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
    /** 없는 이메일일 때도 비밀번호 비교 시간을 비슷하게 맞추기 위한 값. 실제 회원 비밀번호가 아니다. */
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("lancerpick-timing-dummy-not-a-password");
    }

    @Transactional(readOnly = true)
    public MemberResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        // 가입 때와 같은 이메일 정규화. 형식 검사는 하지 않는다(틀린 형식은 어차피 회원이 없어 401).
        String email = TextNormalizer.normalize(loginRequest.email()).toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmail(email).orElse(null);

        if (member == null) {
            // 이메일이 없을 때 바로 응답하면 응답 속도 차이로 가입 여부가 드러날 수 있어 비교를 한 번 수행한다.
            passwordEncoder.matches(loginRequest.password(), dummyHash);
            throw invalidCredentials();
        }
        // 비밀번호 원문은 정규화하지 않고 그대로 비교한다(API 10절).
        if (!passwordEncoder.matches(loginRequest.password(), member.getPasswordHash())) {
            throw invalidCredentials();
        }

        // 응답에 필요한 DB 조회는 세션을 바꾸기 전에 끝낸다.
        // 세션 저장 뒤에 조회가 실패하면 "화면은 실패, 서버는 로그인 상태"가 되어 앞뒤가 맞지 않는다.
        MemberResponse result = MemberResponse.from(member, memberRepository.countSellerProfiles(member.getId()) > 0);

        // 세션 고정 공격 방지: 로그인 전에 쓰던 세션 ID를 새 ID로 바꾼다.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }

        String role = member.getRole().name();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(member.getId(), role),
                null,   // 인증 후에는 비밀번호를 보관하지 않는다
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));   // hasRole("ADMIN")은 ROLE_ADMIN을 찾는다

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);   // 다음 요청부터 세션으로 로그인 유지

        // 로그인 후 CSRF 토큰 교체. 프런트는 GET /api/auth/csrf로 새 토큰을 다시 받는다(API 2절).
        csrfTokenRepository.saveToken(null, request, response);

        return result;
    }

    /** 세션을 무효화하고 인증 정보를 지운다. 세션에 있던 CSRF 토큰도 함께 사라진다. */
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private static BusinessException invalidCredentials() {
        // 이메일 없음·비밀번호 불일치를 구분하지 않는다(가입 여부 노출 방지).
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
