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
    boolean pending = cancellations.stream().anyMatch(c -> "PENDING".equals(c.status()));
    if (com.example.lancerpick.common.policy.TradePolicyVersion.isContract(policyVersion))
      return contractActions(policyVersion, status, buyer, revision, pending);
    if (!com.example.lancerpick.common.policy.TradePolicyVersion.LEGACY_V1
        .name()
        .equals(policyVersion)) return List.of();
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
   * 계약 주문은 결제 전 수락·거절·취소만 연결돼 있다. 수락은 일반 주문과 같은 ACCEPT(POST /api/orders/{id}/accept)다.
   * 계약 초안·서명은 주문 상세의 계약 영역이 계약 API로 처리한다. CONTRACT_V3의 AWAITING_PAYMENT는 전액 결제 대기다.
   * V3 결제 후에는 금액이 움직이지 않는 납품·수정 요청만 연결돼 있다. 결제(PAY)·구매 확정·결제 후 취소는 V3 금액
   * 처리(J4)가 연결될 때 추가한다. CONTRACT_V2는 결제 후 명령을 열지 않는다.
   */
  private static List<String> contractActions(
      String policyVersion,
      String status,
      boolean buyer,
      RevisionRequest revision,
      boolean pendingCancellation) {
    switch (status) {
      case "REQUESTED":
        return buyer ? List.of("CANCEL") : List.of("ACCEPT", "REJECT");
      case "AWAITING_CONTRACT", "AWAITING_PAYMENT":
        return List.of("CANCEL");
      default:
        break;
    }
    if (!com.example.lancerpick.common.policy.TradePolicyVersion.isFullFunding(policyVersion)
        || pendingCancellation) return List.of();
    if (buyer)
      return "AWAITING_CONFIRMATION".equals(status) && revision == null
          ? List.of("REQUEST_REVISION")
          : List.of();
    return "IN_PROGRESS".equals(status) || "REVISION_IN_PROGRESS".equals(status)
        ? List.of("DELIVER")
        : List.of();
  }
}
