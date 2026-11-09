package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 관리자 전용 스칼라 응답. 인증정보·비밀번호 기준일·요청 토큰은 읽지 않는다. */
public final class AdminMemberResponses {
  private AdminMemberResponses() {}

  public record Summary(
      String id,
      String email,
      String displayName,
      String role,
      String restriction,
      OffsetDateTime createdAt) {}

  public record Detail(
      Summary member,
      OffsetDateTime emailVerifiedAt,
      OffsetDateTime serverTime,
      boolean canManage,
      List<Sanction> activeSanctions) {}

  public record Sanction(
      String id,
      String kind,
      String state,
      String reason,
      String appliedBy,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      OffsetDateTime revokedAt,
      String revokedBy,
      String revokeReason,
      OffsetDateTime timerStopDeliveredAt) {}

  public record Applied(String memberId, String sanctionId) {}
}
