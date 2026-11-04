package com.example.lancerpick.order.service;

import com.example.lancerpick.order.dto.OrderDetailResponse.*;
import java.util.ArrayList;
import java.util.List;

/** 버튼 표시용 정책이다. 실제 업무 명령은 상태·권한을 다시 검증해야 한다. */
final class OrderAllowedActions {
  private OrderAllowedActions() {}

  static List<String> actions(
      String status,
      boolean buyer,
      RevisionRequest revision,
      List<CancellationRequest> cancellations,
      Review review) {
    boolean pending = cancellations.stream().anyMatch(c -> "PENDING".equals(c.status()));
    var actions = new ArrayList<String>();
    if (buyer) {
      switch (status) {
        case "REQUESTED" -> actions.add("CANCEL");
        case "AWAITING_PAYMENT" -> actions.addAll(List.of("PAY", "CANCEL"));
        case "IN_PROGRESS", "AWAITING_CONFIRMATION", "REVISION_IN_PROGRESS" -> {
          if (pending) actions.add("WITHDRAW_CANCELLATION");
          else {
            actions.add("REQUEST_CANCELLATION");
            if ("AWAITING_CONFIRMATION".equals(status)) {
              actions.add("CONFIRM");
              if (revision == null) actions.add("REQUEST_REVISION");
            }
          }
        }
        case "COMPLETED" -> {
          if (review == null) actions.add("WRITE_REVIEW");
        }
        default -> {}
      }
    } else {
      switch (status) {
        case "REQUESTED" -> actions.addAll(List.of("ACCEPT", "REJECT"));
        case "AWAITING_PAYMENT" -> actions.add("CANCEL");
        case "IN_PROGRESS", "AWAITING_CONFIRMATION", "REVISION_IN_PROGRESS" -> {
          actions.add("CANCEL");
          if (pending) actions.addAll(List.of("APPROVE_CANCELLATION", "REJECT_CANCELLATION"));
          else if (!"AWAITING_CONFIRMATION".equals(status)) actions.add("DELIVER");
        }
        default -> {}
      }
    }
    return List.copyOf(actions);
  }
}
