package com.example.lancerpick.member.controller;

import com.example.lancerpick.auth.service.AuthService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.security.LoginMember;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.UpdateMeRequest;
import com.example.lancerpick.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 본인 정보. 회원 ID는 요청값이 아니라 인증 정보에서만 꺼낸다. */
@RestController
@Profile("!demo")   // demo 프로필에서는 회원 기능을 만들지 않는다
@RequestMapping("/api/me")
public class MeController {

    private final MemberService memberService;
    private final CurrentMemberId currentMemberId;
    private final AuthService authService;

    public MeController(MemberService memberService, CurrentMemberId currentMemberId, AuthService authService) {
        this.memberService = memberService;
        this.currentMemberId = currentMemberId;
        this.authService = authService;
    }

    /** GET /api/me — 내 정보 */
    @GetMapping
    public ResponseEntity<MemberResponse> me(Authentication authentication) {
        MemberResponse member = memberService.getMe(currentMemberId.require(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(member);
    }

    /** PATCH /api/me — 표시 이름 수정 */
    @PatchMapping
    public ResponseEntity<MemberResponse> updateMe(Authentication authentication,
            @Valid @RequestBody UpdateMeRequest request) {
        MemberResponse member = memberService.updateMe(currentMemberId.require(authentication), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(member);
    }

    /**
     * POST /api/me/password — 비밀번호 변경. 성공하면 204이고 지금 세션도 끝난다(자동 로그인 없음, 새 비밀번호로 재로그인).
     * 다른 기기·탭의 세션은 auth_version이 바뀌었으므로 다음 요청에서 SessionAuthVersionFilter가 폐기한다.
     * 비밀번호 저장(트랜잭션 커밋)이 끝난 뒤에 세션을 지운다. 저장이 실패하면 세션은 그대로 남는다.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        long memberId = currentMemberId.require(authentication);
        Long expectedAuthVersion = authentication.getPrincipal() instanceof LoginMember member
                ? member.authVersion()
                : null;
        memberService.changePassword(memberId, expectedAuthVersion, body);
        authService.logout(request, response, authentication);   // 로그아웃과 같은 공통 세션 정리
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
