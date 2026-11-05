package com.example.lancerpick.order.service;

import com.example.lancerpick.order.dto.OrderDetailResponse.*;
import java.util.ArrayList;
import java.util.List;

/** 버튼 표시용 정책이다. 실제 업무 명령은 상태·권한을 다시 검증해야 한다. */
final class OrderAllowedActions {
  private OrderAllowedActions() {}

  static List<String> actions(
      String policyVersion,
      String status,
      boolean buyer,
      RevisionRequest revision,
      List<CancellationRequest> cancellations,
      Review review) {
    if (com.example.lancerpick.common.policy.TradePolicyVersion.isContract(policyVersion)) return contractActions(status, buyer);
    if (!com.example.lancerpick.common.policy.TradePolicyVersion.LEGACY_V1
        .name()
        .equals(policyVersion)) return List.of();
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

  /**
   * 계약 주문은 선금 전 수락·거절·취소만 연결돼 있다. 계약 확인·선금 결제·이후 거래 명령은 전용 API(E5·E6·J4)가 생길 때
   * 추가한다.
   */
  private static List<String> contractActions(String status, boolean buyer) {
    return switch (status) {
      case "REQUESTED" -> buyer ? List.of("CANCEL") : List.of("ACCEPT", "REJECT");
      case "AWAITING_CONTRACT", "AWAITING_PAYMENT" -> List.of("CANCEL");
      default -> List.of();
    };
  }
}
