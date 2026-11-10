package com.example.lancerpick.order.service;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderDetailResponse.CancellationRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse.Review;
import com.example.lancerpick.order.dto.OrderDetailResponse.RevisionRequest;
import java.util.ArrayList;
import java.util.List;

/** 버튼 표시용 정책이다. 실제 업무 명령은 상태·권한을 다시 검증해야 한다. */
final class OrderAllowedActions {
  private OrderAllowedActions() {}

  /** 참여자 상세에 버튼 표시용 행동을 붙인다. 판매자 제재에 따른 ACCEPT 숨김은 상세 조회({@link OrderQueryService})만 한다. */
  static OrderDetailResponse attach(OrderDetailResponse detail, boolean buyer) {
    return detail.withAllowedActions(actions(detail, buyer));
  }

  static List<String> actions(OrderDetailResponse detail, boolean buyer) {
    return actions(
        detail.policyVersion(),
        detail.status(),
        buyer,
        detail.revisionRequest(),
        detail.cancellationRequests(),
        detail.review());
  }

  static List<String> actions(
      String policyVersion,
      String status,
      boolean buyer,
      RevisionRequest revision,
      List<CancellationRequest> cancellations,
      Review review) {
    if (TradePolicyVersion.CONTRACT_V3.name().equals(policyVersion)) {
      if ("AWAITING_PAYMENT".equals(status) && buyer) {
        return List.of("PAY", "CANCEL");
      }
      if (List.of("REQUESTED", "AWAITING_CONTRACT", "AWAITING_PAYMENT").contains(status)) {
        return contractActions(status, buyer);
      }
      return actions(
              TradePolicyVersion.LEGACY_V1.name(), status, buyer, revision, cancellations, review)
          .stream()
          .filter(action -> buyer || !"CANCEL".equals(action))
          .toList();
    }
    if (TradePolicyVersion.isContract(policyVersion)) {
      return contractActions(status, buyer);
    }
    if (!TradePolicyVersion.LEGACY_V1.name().equals(policyVersion)) {
      return List.of();
    }
    boolean pending =
        cancellations.stream().anyMatch(request -> "PENDING".equals(request.status()));
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
   * 계약 주문의 결제 전 공통 행동. 수락은 공통 ACCEPT(POST /api/orders/{id}/accept) 경로를 사용한다. 초안/서명은 계약 API가 처리한다.
   * V2 금융 명령은 차단하고 V3 결제/결제 후 행동은 위 분기에서 별도 허용한다.
   */
  private static List<String> contractActions(String status, boolean buyer) {
    return switch (status) {
      case "REQUESTED" -> buyer ? List.of("CANCEL") : List.of("ACCEPT", "REJECT");
      case "AWAITING_CONTRACT", "AWAITING_PAYMENT" -> List.of("CANCEL");
      default -> List.of();
    };
  }
}
