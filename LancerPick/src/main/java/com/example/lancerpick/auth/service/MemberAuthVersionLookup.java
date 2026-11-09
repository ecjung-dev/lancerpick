package com.example.lancerpick.auth.service;

import com.example.lancerpick.common.security.AuthVersionLookup;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** HTTP·소켓 인증 재검사에서 보안 잠금 여부와 현재 인증 버전을 잠금 없이 조회한다. */
@Service
@Profile("!demo")   // demo 프로필은 DB를 쓰지 않으므로 만들지 않는다(필터도 등록되지 않음)
public class MemberAuthVersionLookup implements AuthVersionLookup {

    private final MemberRepository memberRepository;
    private final MemberSanctionQuery sanctions;

    public MemberAuthVersionLookup(MemberRepository memberRepository, MemberSanctionQuery sanctions) {
        this.memberRepository = memberRepository;
        this.sanctions = sanctions;
    }

    @Override
    public Optional<Long> currentAuthVersion(long memberId) {
        if (sanctions.restriction(memberId).securityLocked()) {
            return Optional.empty();
        }
        return memberRepository.findAuthVersionById(memberId);
    }
}
