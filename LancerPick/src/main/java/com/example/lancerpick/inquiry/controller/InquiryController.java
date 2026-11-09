package com.example.lancerpick.inquiry.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.inquiry.dto.CreateCancellationReviewInquiryRequest;
import com.example.lancerpick.inquiry.dto.InquiryResponse;
import com.example.lancerpick.inquiry.service.InquiryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자 운영 문의(K4): 관리자 취소 검토 요청과 내 문의 조회. 로그인 필수(SecurityConfig의 나머지 경로), 명령은 CSRF 필수. */
@RestController
@Profile("!demo")
public class InquiryController {
  private final InquiryService inquiries;
  private final CurrentMemberId currentMemberId;

  public InquiryController(InquiryService inquiries, CurrentMemberId currentMemberId) {
    this.inquiries = inquiries;
    this.currentMemberId = currentMemberId;
  }

  /** 새로 만들면 201, 같은 취소 요청에 이미 열린 문의가 있으면 200과 그 문의(재전송 안전). */
  @PostMapping("/api/orders/{orderId}/cancellation-requests/{requestId}/review-inquiries")
  public ResponseEntity<InquiryResponse> requestCancellationReview(
      Authentication authentication,
      @PathVariable("orderId") @Min(1) long orderId,
      @PathVariable("requestId") @Min(1) long requestId,
      @RequestBody CreateCancellationReviewInquiryRequest request) {
    var result =
        inquiries.requestCancellationReview(
            currentMemberId.require(authentication), orderId, requestId, request.subject());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .cacheControl(CacheControl.noStore())
        .body(result.inquiry());
  }

  @GetMapping("/api/me/inquiries")
  public ResponseEntity<PageResponse<InquiryResponse>> mine(
      Authentication authentication,
      @RequestParam(required = false) @Min(1) Long orderId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(inquiries.mine(currentMemberId.require(authentication), orderId, page, size));
  }
}
