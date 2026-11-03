package com.example.lancerpick.member.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.UpdateMeRequest;
import com.example.lancerpick.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    public MeController(MemberService memberService, CurrentMemberId currentMemberId) {
        this.memberService = memberService;
        this.currentMemberId = currentMemberId;
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
}
