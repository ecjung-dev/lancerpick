package com.example.lancerpick.admin.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * K6 재처리 실행 요청 본문. 관리자 ID·결과·대상 상태는 받지 않는다(세션과 서버가 정한다).
 * requestKey는 화면이 버튼을 누를 때 한 번 만든 UUID이며, 결과가 불명확해 다시 보낼 때도 같은 값·같은 사유를 보낸다.
 * version은 계약 PDF 재생성에서만 쓰며 생략하면 현재 계약 버전이다.
 */
public record AdminReprocessingRequest(
        @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason,
        @JsonDeserialize(using = AdminRequestJson.StringValue.class) String requestKey,
        @JsonDeserialize(using = AdminRequestJson.VersionValue.class) Long version) {}
