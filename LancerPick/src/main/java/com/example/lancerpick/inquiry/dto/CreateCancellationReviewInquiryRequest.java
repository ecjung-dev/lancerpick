package com.example.lancerpick.inquiry.dto;

import com.example.lancerpick.order.dto.OrderRequestJson;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 구매자의 관리자 취소 검토 요청(K4). subject는 공백 정리 후 1~150자이며 서비스에서 검사한다(null·빈 값도 서비스가 400으로 응답).
 * 숫자·boolean을 문자열로 자동 변환하지 않도록 문자열 토큰만 받는다.
 */
public record CreateCancellationReviewInquiryRequest(
    @JsonDeserialize(using = OrderRequestJson.StringValue.class) String subject) {}
