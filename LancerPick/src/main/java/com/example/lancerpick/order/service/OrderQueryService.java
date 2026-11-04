package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderSide;
import com.example.lancerpick.order.dto.OrderSummaryResponse;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderListQueries;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
// 전체 건수/목록과 여러 테이블의 상세를 같은 읽기 스냅샷으로 조립한다. 쓰기 잠금은 사용하지 않는다.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OrderQueryService {
  private final OrderDetailQueries details;
  private final OrderListQueries lists;

  public OrderQueryService(OrderDetailQueries details, OrderListQueries lists) {
    this.details = details;
    this.lists = lists;
  }

  public PageResponse<OrderSummaryResponse> list(
      long memberId, OrderSide side, String status, int page, int size) {
    if (side == null || page < 0 || size < 1 || size > 100) throw invalidInput();
    OrderStatus filter = null;
    if (status != null) {
      try {
        filter = OrderStatus.valueOf(status);
      } catch (IllegalArgumentException exception) {
        throw invalidInput();
      }
    }
    long total = lists.count(memberId, side, filter);
    long offset = (long) page * size;
    var items =
        offset >= total
            ? List.<OrderSummaryResponse>of()
            : lists.find(memberId, side, filter, size, offset);
    return new PageResponse<>(
        items, page, size, total, Math.toIntExact(total / size + (total % size == 0 ? 0 : 1)));
  }

  public OrderDetailResponse detail(long memberId, String id) {
    long orderId;
    try {
      if (id == null || !id.matches("[0-9]+")) throw invalidInput();
      orderId = Long.parseLong(id);
      if (orderId <= 0) throw invalidInput();
    } catch (NumberFormatException exception) {
      throw invalidInput();
    }
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.status(),
            detail.buyer().id().equals(Long.toString(memberId)),
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static BusinessException invalidInput() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 조회 조건을 확인해 주세요.");
  }
}
