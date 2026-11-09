package com.example.lancerpick.member.dto;

import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.service.MemberChangePolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 본인 회원 응답. 표시 이름 변경 가능 시각은 null(첫 변경 가능) 또는 UTC 오프셋 시각이다. */
public record MemberResponse(
        String id,
        String email,
        String displayName,
        String role,
        boolean hasSellerProfile,
        OffsetDateTime displayNameChangeAvailableAt
) {
    public static MemberResponse from(Member member, boolean hasSellerProfile) {
        return new MemberResponse(
                String.valueOf(member.getId()),   // 팀 규칙: ID는 JSON에서 문자열
                member.getEmail(),
                member.getDisplayName(),
                member.getRole().name(),
                hasSellerProfile,
                member.getDisplayNameChangedAt() == null ? null
                        : MemberChangePolicy.sixMonthsAfter(member.getDisplayNameChangedAt())
                                .atOffset(ZoneOffset.UTC));
    }
}
