package com.example.lancerpick.common.security;

import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 회원 ID는 검증된 인증 객체에서만 얻는다. username·요청 파라미터로 추정하지 않는다. */
@Component
public class CurrentMemberId {
    public long require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            throw authenticationRequired();
        }
        Long memberId = member.memberId();
        if (memberId == null || memberId <= 0) throw authenticationRequired();
        return memberId;
    }

    private static BusinessException authenticationRequired() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
}
