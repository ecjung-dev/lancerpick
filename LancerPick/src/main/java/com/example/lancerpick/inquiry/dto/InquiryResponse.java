package com.example.lancerpick.inquiry.dto;

import com.example.lancerpick.inquiry.entity.SupportInquiry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 문의 신청자에게 보여 주는 문의 요약. ID는 JSON 문자열, 시각은 UTC다. 담당 관리자 ID는 신청자에게 노출하지 않는다.
 *
 * @param type CANCELLATION_REVIEW·GENERAL
 * @param status OPEN·IN_REVIEW·CLOSED
 */
public record InquiryResponse(
    String id,
    String type,
    String status,
    String subject,
    String orderId,
    String cancellationRequestId,
    OffsetDateTime createdAt,
    OffsetDateTime resolvedAt) {

  public static InquiryResponse from(SupportInquiry inquiry) {
    return new InquiryResponse(
        inquiry.getId().toString(),
        inquiry.getType(),
        inquiry.getStatus(),
        inquiry.getSubject(),
        id(inquiry.getOrderId()),
        id(inquiry.getCancellationRequestId()),
        utc(inquiry.getCreatedAt()),
        utc(inquiry.getResolvedAt()));
  }

  private static String id(Long value) {
    return value == null ? null : value.toString();
  }

  private static OffsetDateTime utc(java.time.LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
