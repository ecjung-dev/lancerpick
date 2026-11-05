package com.example.lancerpick.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 비밀번호 변경 후 기존 세션 폐기(고도화 3인 설계 9절, 4인 설계 4절 "기존 세션 무효화").
 *
 * 세션 principal의 authVersion(로그인 당시 값)과 DB의 현재 members.auth_version을 매 요청 비교한다.
 * 다르거나 회원이 없으면 그 세션을 무효화하고 인증 정보를 지운다. 요청은 막지 않고 "비로그인"으로 계속 진행한다.
 * - 보호 API → 뒤의 인가 단계에서 401 AUTHENTICATION_REQUIRED
 * - 공개 API(상품 조회 등) → 비로그인 결과로 정상 응답
 *
 * 처음에는 버전을 캐시하지 않는다(폐기 지연을 줄이기 위해). 요청마다 기본키 조회 1회가 추가된다.
 * 이미 이 확인을 통과해 처리 중인 요청을 되돌리지는 않는다(설계 9절의 한계).
 *
 * Spring Bean이 아니다. Bean으로 만들면 Spring Boot가 일반 서블릿 필터로도 한 번 더 등록하기 때문에
 * SecurityConfig에서 직접 생성해 Security 필터 체인(CsrfFilter 다음)에만 넣는다.
 */
public class SessionAuthVersionFilter extends OncePerRequestFilter {

    private final AuthVersionLookup authVersionLookup;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public SessionAuthVersionFilter(AuthVersionLookup authVersionLookup) {
        this.authVersionLookup = authVersionLookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof LoginMember member
                && member.authVersion() != null
                && isStale(member)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();   // 세션에 있던 CSRF 토큰도 함께 사라진다
            }
            contextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private boolean isStale(LoginMember member) {
        Optional<Long> current = authVersionLookup.currentAuthVersion(member.memberId());
        if (current.isEmpty()) {
            return true;   // 회원이 없어졌으면 세션도 유효하지 않다
        }
        return !current.get().equals(member.authVersion());
    }
}
