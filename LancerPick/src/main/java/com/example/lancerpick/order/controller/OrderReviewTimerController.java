package com.example.lancerpick.order.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.order.dto.ReviewTimerResponse;
import com.example.lancerpick.order.service.ReviewTimerService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V3 검수 무응답 자동 구매 확정 타이머 조회(당사자)와 시작(판매자). 본문 없이 서버 시각과 주문 기록으로만 판단한다. */
@RestController
@Profile("!demo")
@RequiredArgsConstructor
@RequestMapping("/api/orders/{id}/review-timer")
public class OrderReviewTimerController {
  private final ReviewTimerService reviewTimerService;
  private final CurrentMemberId currentMemberId;

  @GetMapping
  public ResponseEntity<ReviewTimerResponse> status(
      Authentication authentication, @PathVariable("id") @Min(1) long orderId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviewTimerService.status(currentMemberId.require(authentication), orderId));
  }

  @PostMapping
  public ResponseEntity<ReviewTimerResponse> start(
      Authentication authentication, @PathVariable("id") @Min(1) long orderId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reviewTimerService.start(currentMemberId.require(authentication), orderId));
  }
}
