package com.example.lancerpick.admin.dto;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.UuidText;
import com.example.lancerpick.member.entity.SanctionKind;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.annotation.JsonDeserialize;

/** JSON 형식과 업무 값은 분리해 검증한다. 날짜는 오프셋을 포함한 ISO 시각만 허용한다. */
public record AdminMemberSanctionRequest(
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String kind,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String endsAt,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String requestKey) {
  public record Revoke(
      @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason) {}

  /** 형식 검증을 통과한 값. 종료 시각은 UTC로 바꾼 값이며, 미래 시각·권한 같은 업무 검증은 서비스가 한다. */
  public record Parsed(SanctionKind kind, LocalDateTime endsAt, UUID requestKey) {}

  /** 요청 키(UUID)·종류(NONE 제외)·종료 시각(오프셋 포함, 연도 1000~9999) 형식을 검사한다. 하나라도 틀리면 같은 400을 낸다. */
  public Parsed parse() {
    UUID key = UuidText.parse(requestKey).orElseThrow(AdminMemberSanctionRequest::invalid);
    SanctionKind parsedKind;
    LocalDateTime end;
    try {
      parsedKind = SanctionKind.valueOf(kind == null ? "" : kind);
      end =
          OffsetDateTime.parse(endsAt == null ? "" : endsAt)
              .withOffsetSameInstant(ZoneOffset.UTC)
              .toLocalDateTime();
    } catch (IllegalArgumentException | DateTimeException failure) {
      throw invalid();
    }
    if (parsedKind == SanctionKind.NONE || end.getYear() < 1000 || end.getYear() > 9999) {
      throw invalid();
    }
    return new Parsed(parsedKind, end, key);
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "제재 종류·종료 시각·요청 키를 확인해 주세요.");
  }
}
