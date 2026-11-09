package com.example.lancerpick.admin.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

/** JSON 형식과 업무 값은 분리해 검증한다. 날짜는 오프셋을 포함한 ISO 시각만 허용한다. */
public record AdminMemberSanctionRequest(
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String kind,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String endsAt,
    @JsonDeserialize(using = AdminRequestJson.StringValue.class) String requestKey) {
  public record Revoke(
      @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason) {}
}
