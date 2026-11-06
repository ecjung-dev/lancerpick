package com.example.lancerpick.inquiry.entity;

import com.example.lancerpick.common.text.TextNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 구매자 운영 문의(`support_inquiries`, V5). 취소 검토 문의(CANCELLATION_REVIEW)는 주문과 그 주문의 취소 요청을 반드시
 * 참조하고, 일반 문의(GENERAL)는 취소 요청을 참조하지 않는다. 종료(CLOSED)는 처리 시각을 함께 남기며 되돌리지 않는다. 상태
 * 조합은 DB CHECK와 같게 엔티티에서 먼저 지킨다.
 *
 * <p>신청자가 주문 구매자인지, 연결한 취소 요청이 대기 중인지 같은 업무 검증은 문의 접수 서비스가 한다. 취소 검토 문의의 종료는
 * 주문 잠금 아래에서 관리자 승인/거절과 같은 트랜잭션으로 처리한다(`AdminCancellationReviewService`).
 */
@Getter
@Entity
@Table(name = "support_inquiries")
public class SupportInquiry {
  public static final String CANCELLATION_REVIEW = "CANCELLATION_REVIEW";
  public static final String GENERAL = "GENERAL";
  public static final String OPEN = "OPEN";
  public static final String IN_REVIEW = "IN_REVIEW";
  public static final String CLOSED = "CLOSED";
  public static final int SUBJECT_MAX_LENGTH = 150;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "requester_id", nullable = false, updatable = false)
  private long requesterId;

  @Column(name = "order_id", updatable = false)
  private Long orderId;

  @Column(name = "cancellation_request_id", updatable = false)
  private Long cancellationRequestId;

  @Column(nullable = false, updatable = false, length = 30)
  private String type;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, updatable = false, length = SUBJECT_MAX_LENGTH)
  private String subject;

  @Column(name = "assigned_admin_id")
  private Long assignedAdminId;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected SupportInquiry() {}

  /** 판매자 무응답 등으로 대기 중인 취소 요청의 관리자 검토를 요청하는 문의. */
  public static SupportInquiry cancellationReview(
      long requesterId,
      long orderId,
      long cancellationRequestId,
      String subject,
      LocalDateTime createdAt) {
    if (orderId < 1 || cancellationRequestId < 1)
      throw new IllegalArgumentException("Cancellation review requires order and request");
    return create(requesterId, orderId, cancellationRequestId, CANCELLATION_REVIEW, subject, createdAt);
  }

  /** 주문과 무관하거나 주문만 참조하는 일반 문의. */
  public static SupportInquiry general(
      long requesterId, Long orderId, String subject, LocalDateTime createdAt) {
    if (orderId != null && orderId < 1) throw new IllegalArgumentException("Invalid order id");
    return create(requesterId, orderId, null, GENERAL, subject, createdAt);
  }

  private static SupportInquiry create(
      long requesterId,
      Long orderId,
      Long cancellationRequestId,
      String type,
      String rawSubject,
      LocalDateTime createdAt) {
    if (requesterId < 1 || createdAt == null)
      throw new IllegalArgumentException("Invalid support inquiry");
    var inquiry = new SupportInquiry();
    inquiry.requesterId = requesterId;
    inquiry.orderId = orderId;
    inquiry.cancellationRequestId = cancellationRequestId;
    inquiry.type = type;
    inquiry.status = OPEN;
    inquiry.subject = subject(rawSubject);
    inquiry.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    inquiry.updatedAt = inquiry.createdAt;
    return inquiry;
  }

  /** 공백 정리 후 1~150자. 호출자는 범위를 벗어난 입력을 400으로 바꿔 응답한다. */
  public static String subject(String raw) {
    String subject = raw == null ? "" : TextNormalizer.normalize(raw);
    if (subject.isEmpty() || subject.codePointCount(0, subject.length()) > SUBJECT_MAX_LENGTH)
      throw new IllegalArgumentException("Inquiry subject must be 1 to 150 characters");
    return subject;
  }

  public boolean isOpen() {
    return OPEN.equals(status) || IN_REVIEW.equals(status);
  }

  public boolean isCancellationReview() {
    return CANCELLATION_REVIEW.equals(type);
  }

  /** 관리자가 문의를 종료한다. 종료한 관리자를 처리 담당자로 남기고, 이미 종료된 문의는 다시 종료하지 않는다. */
  public void close(long adminId, LocalDateTime at) {
    if (!isOpen() || adminId < 1 || at == null)
      throw new IllegalStateException("Invalid inquiry close");
    var closedAt = at.truncatedTo(ChronoUnit.MICROS);
    if (closedAt.isBefore(createdAt) || closedAt.isBefore(updatedAt))
      throw new IllegalStateException("Inquiry close clock moved backwards");
    status = CLOSED;
    assignedAdminId = adminId;
    resolvedAt = closedAt;
    updatedAt = closedAt;
  }
}
