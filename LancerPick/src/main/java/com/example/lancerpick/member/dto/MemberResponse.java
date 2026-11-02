package com.example.lancerpick.member.dto;

import com.example.lancerpick.member.entity.Member;

/** API 명세의 Member 응답: id, email, displayName, role, hasSellerProfile */
public record MemberResponse(
        String id,
        String email,
        String displayName,
        String role,
        boolean hasSellerProfile
) {
    public static MemberResponse from(Member member, boolean hasSellerProfile) {
        return new MemberResponse(
                String.valueOf(member.getId()),   // 팀 규칙: ID는 JSON에서 문자열
                member.getEmail(),
                member.getDisplayName(),
                member.getRole().name(),
                hasSellerProfile);
    }
}
