package com.example.lancerpick.auth.service;

import com.example.lancerpick.common.security.AuthVersionLookup;
import com.example.lancerpick.member.repository.MemberRepository;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** SessionAuthVersionFilter가 쓰는 현재 인증 버전 조회. 잠금 없이 auth_version 한 값만 읽는다. */
@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 만들지 않는다(필터도 등록되지 않음)
public class MemberAuthVersionLookup implements AuthVersionLookup {

    private final MemberRepository memberRepository;

    public MemberAuthVersionLookup(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public Optional<Long> currentAuthVersion(long memberId) {
        return memberRepository.findAuthVersionById(memberId);
    }
}
