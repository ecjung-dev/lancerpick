package com.example.lancerpick.admin.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

/** 관리자 취소 검토 승인/거절 요청. 사유 공백 정리·1~1000자 검사는 서비스(AdminCancellationReviewService)가 한다. */
public record AdminInquiryDecisionRequest(
        @JsonDeserialize(using = AdminRequestJson.StringValue.class) String reason) {}
