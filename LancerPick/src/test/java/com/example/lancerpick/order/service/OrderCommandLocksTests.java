package com.example.lancerpick.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** R1: 계약·채팅이 공통 잠금으로 옮겨져도 잠금 순서와 401/404 응답 계약이 그대로인지 확인한다. */
class OrderCommandLocksTests {
  private static final long MEMBER = 7L;
  private static final long ORDER = 11L;

  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderCommandLocks locks = new OrderCommandLocks(orders);

  @Test
  void locksActorBeforeParticipantOrder() {
    var order = mock(Order.class);
    when(orders.lockDecisionActor(MEMBER)).thenReturn(Optional.of(MEMBER));
    when(orders.findForDecision(ORDER, MEMBER)).thenReturn(Optional.of(order));

    assertThat(locks.lockParticipantOrder(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .isSameAs(order);
    var sequence = inOrder(orders);
    sequence.verify(orders).lockDecisionActor(MEMBER);
    sequence.verify(orders).findForDecision(ORDER, MEMBER);
  }

  @Test
  void missingActorIsUnauthorizedEvenWithCustomNotFound() {
    when(orders.lockDecisionActor(MEMBER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> locks.lockParticipantOrder(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(e.code()).isEqualTo("AUTHENTICATION_REQUIRED");
              assertThat(e.getMessage()).isEqualTo("로그인이 필요합니다.");
            });
    verify(orders, never()).findForDecision(anyLong(), anyLong());
  }

  @Test
  void missingParticipantOrderUsesDefaultOrCallerMessage() {
    when(orders.lockDecisionActor(MEMBER)).thenReturn(Optional.of(MEMBER));
    when(orders.findForDecision(ORDER, MEMBER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> locks.lockParticipantOrder(MEMBER, ORDER))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND");
              assertThat(e.getMessage()).isEqualTo("주문을 찾을 수 없습니다.");
            });
    assertThatThrownBy(() -> locks.lockParticipantOrder(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .hasMessage("custom");
  }

  @Test
  void hidingActorTurnsMissingActorIntoCallerNotFound() {
    when(orders.lockDecisionActor(MEMBER)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> locks.lockParticipantOrderHidingActor(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND))
        .hasMessage("custom");
    verify(orders, never()).findForDecision(anyLong(), anyLong());
  }

  @Test
  void hidingActorStillLocksActorBeforeOrder() {
    var order = mock(Order.class);
    when(orders.lockDecisionActor(MEMBER)).thenReturn(Optional.of(MEMBER));
    when(orders.findForDecision(ORDER, MEMBER)).thenReturn(Optional.empty()).thenReturn(Optional.of(order));

    assertThatThrownBy(
            () -> locks.lockParticipantOrderHidingActor(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .hasMessage("custom");
    assertThat(locks.lockParticipantOrderHidingActor(MEMBER, ORDER, OrderCommandLocksTests::custom))
        .isSameAs(order);
    var sequence = inOrder(orders);
    sequence.verify(orders).lockDecisionActor(MEMBER);
    sequence.verify(orders).findForDecision(ORDER, MEMBER);
  }

  private static BusinessException custom() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "custom");
  }
}
