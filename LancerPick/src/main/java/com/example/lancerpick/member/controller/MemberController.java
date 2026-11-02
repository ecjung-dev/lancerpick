package com.example.lancerpick.member.controller;

import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")   // demo 프로필에서는 회원 기능을 만들지 않는다
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** POST /api/members — 회원가입. 성공 시 201 + Member */
    @PostMapping
    public ResponseEntity<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        MemberResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}