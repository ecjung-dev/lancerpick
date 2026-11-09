package com.example.lancerpick.inquiry.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.inquiry.dto.InquiryResponse;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.example.lancerpick.inquiry.repository.SupportInquiryQueries;
import com.example.lancerpick.inquiry.repository.SupportInquiryRepository;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.service.OrderCommandLocks;
import java.time.Clock;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 운영 문의(K4). 판매자가 대기 중인 취소 요청에 응답하지 않을 때 구매자가 관리자 검토를 요청한다.
 *
 * <p>잠금 순서는 관리자 승인/거절(`AdminCancellationReviewService`)과 같다: 실행 회원 공유 잠금 → 주문 배타 잠금 → (문의 저장). 같은
 * 취소 요청의 열린 문의 확인과 저장을 주문 잠금 아래에서 하므로 DB UNIQUE 없이도 동시 요청이 문의를 두 개 만들지 않는다.
 */
@Service
@Profile("!demo")
public class InquiryService {
  /** 관리자 취소 검토 대상과 같은 범위: V3 결제 후 아직 끝나지 않은 주문. */
  private static final Set<OrderStatus> PAID_OPEN =
      Set.of(
          OrderStatus.IN_PROGRESS, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.REVISION_IN_PROGRESS);

  private final OrderCommandLocks locks;
  private final CancellationRequestRepository requests;
  private final SupportInquiryRepository inquiries;
  private final SupportInquiryQueries queries;
  private final Clock clock;

  public InquiryService(
      OrderCommandLocks locks,
      CancellationRequestRepository requests,
      SupportInquiryRepository inquiries,
      SupportInquiryQueries queries,
      Clock clock) {
    this.locks = locks;
    this.requests = requests;
    this.inquiries = inquiries;
    this.queries = queries;
    this.clock = clock;
  }

  /** 접수 결과. created=false는 같은 요청에 이미 열린 문의가 있어 그 문의를 돌려준 경우(안전한 재전송). */
  public record Submitted(InquiryResponse inquiry, boolean created) {}

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Submitted requestCancellationReview(
      long memberId, long orderId, long cancellationRequestId, String rawSubject) {
    // 입력 형식은 잠그기 전에 확인한다. 엔티티 규칙(공백 정리 후 1~150자)을 그대로 쓴다.
    final String subject;
    try {
      subject = SupportInquiry.subject(rawSubject);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "문의 제목은 공백 정리 후 1~150자여야 합니다.");
    }
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (order.getBuyerId() != memberId)
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 관리자 취소 검토를 요청할 수 있습니다.");
    var request =
        requests
            .findById(cancellationRequestId)
            .filter(candidate -> candidate.getOrderId() == orderId)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "취소 요청을 찾을 수 없습니다."));
    // 재전송(버튼 두 번 누름·응답 유실): 이미 열린 문의가 있으면 새로 만들지 않고 그대로 돌려준다.
    var existing = queries.findOpenCancellationReview(cancellationRequestId);
    if (existing.isPresent()) return new Submitted(InquiryResponse.from(existing.get()), false);
    if (request.getStatus() != CancellationRequestStatus.PENDING)
      throw conflict("REQUEST_ALREADY_RESOLVED", "이미 처리된 취소 요청입니다. 주문 상세를 다시 확인해 주세요.");
    if (!order.isFullFunding())
      throw conflict("ORDER_POLICY_NOT_READY", "결제 금액 보관 정책 주문만 관리자 취소 검토를 요청할 수 있습니다.");
    if (order.getPaidAt() == null || !PAID_OPEN.contains(order.getStatus()))
      throw conflict("ORDER_STATE_CONFLICT", "결제 후 진행 중인 주문만 관리자 취소 검토를 요청할 수 있습니다.");
    var saved =
        inquiries.saveAndFlush(
            SupportInquiry.cancellationReview(
                memberId, orderId, cancellationRequestId, subject, UtcTimes.nowMicros(clock)));
    return new Submitted(InquiryResponse.from(saved), true);
  }

  /** 내 문의 목록. orderId를 주면 그 주문의 문의만(주문 상세의 검토 요청 상태 표시용). */
  @Transactional(readOnly = true)
  public PageResponse<InquiryResponse> mine(long memberId, Long orderId, int page, int size) {
    long total = queries.countMine(memberId, orderId);
    var items =
        (long) page * size >= total
            ? java.util.List.<InquiryResponse>of()
            : queries.findMine(memberId, orderId, size, (long) page * size).stream()
                .map(InquiryResponse::from)
                .toList();
    return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
