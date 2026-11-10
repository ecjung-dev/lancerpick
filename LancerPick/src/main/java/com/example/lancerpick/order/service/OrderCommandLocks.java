package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.repository.OrderRepository;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 명령의 공통 잠금 순서: 실행 회원 공유 잠금 → 당사자 주문 배타 잠금. 정책 검증은 하지 않으며 호출자가 명시적으로 수행한다.
 * 주문·문의·계약·채팅 명령이 함께 쓴다. 계약 경로는 V2도 다루므로 {@link #lockFulfillmentOrder}를 쓰지 않는다.
 * 잠금이 호출 서비스의 트랜잭션에 묶이도록 기존 트랜잭션 안에서만 실행한다.
 */
@Component
@Profile("!demo")
@Transactional(propagation = Propagation.MANDATORY)
public class OrderCommandLocks {
  private final OrderRepository orders;
  private final MemberSanctionQuery sanctions;

  public OrderCommandLocks(OrderRepository orders, MemberSanctionQuery sanctions) {
    this.orders = orders;
    this.sanctions = sanctions;
  }

  /** 실행 회원을 공유 잠금한다. 회원이 없으면 401. */
  public void lockActor(long memberId) {
    if (orders.lockDecisionActor(memberId).isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
  }

  /**
   * 실행 회원이 현재 보안 잠금 중이면 403 MEMBER_LOCKED(로그인 거절과 같은 코드·문구). 반드시 {@link #lockActor} 계열로 회원을
   * 공유 잠금한 뒤 부른다. 제재 적용의 회원 배타 잠금과 직렬화되므로, 인증 필터를 이미 통과한 요청도 잠금 커밋 뒤에는 막힌다.
   * 구매자의 구매 확정과 판매자의 거래 진전 명령(계약 작성·서명, 납품·재납품, 취소 요청 거절, 자동 확정 타이머 시작)만 쓴다.
   * 구매자 지갑으로만 반환하는 취소·환불, 결제 전 거절, 문의, 계약 PDF 내부 처리는 대상이 아니다.
   */
  public void requireNotSecurityLocked(long memberId) {
    if (sanctions.restriction(memberId).securityLocked()) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "MEMBER_LOCKED", "보안 잠금 중인 계정입니다.");
    }
  }

  /** 실행 회원 잠금 후 당사자 주문을 배타 잠금한다. 당사자가 아니거나 주문이 없으면 404. 정책은 검사하지 않는다. */
  public Order lockParticipantOrder(long memberId, long orderId) {
    return lockParticipantOrder(memberId, orderId, OrderCommandLocks::orderNotFound);
  }

  /** {@link #lockParticipantOrder(long, long)}와 같고, 당사자 주문이 없을 때의 404 응답만 호출자가 정한다(채팅 등). */
  public Order lockParticipantOrder(
      long memberId, long orderId, Supplier<BusinessException> notFound) {
    lockActor(memberId);
    return orders.findForDecision(orderId, memberId).orElseThrow(notFound);
  }

  /**
   * 잠금 순서는 {@link #lockParticipantOrder(long, long)}와 같지만 실행 회원이 없을 때도 401 대신 {@code notFound}로
   * 숨긴다. 계약 문서(PDF) 경로처럼 존재 여부를 드러내지 않는 기존 응답 계약을 유지할 때만 쓴다.
   */
  public Order lockParticipantOrderHidingActor(
      long memberId, long orderId, Supplier<BusinessException> notFound) {
    if (orders.lockDecisionActor(memberId).isEmpty()) {
      throw notFound.get();
    }
    return orders.findForDecision(orderId, memberId).orElseThrow(notFound);
  }

  /** {@link #lockParticipantOrder}와 같고, 납품·수정 요청 정책(LEGACY_V1·CONTRACT_V3)을 저장소에서 함께 검증한다. */
  public Order lockFulfillmentOrder(long memberId, long orderId) {
    lockActor(memberId);
    return orders
        .findForFulfillment(orderId, memberId)
        .orElseThrow(OrderCommandLocks::orderNotFound);
  }

  private static BusinessException orderNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다.");
  }
}
