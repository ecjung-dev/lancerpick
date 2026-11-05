package com.example.lancerpick.order.service;

import org.springframework.stereotype.Component;

/** MVP 정책에서 구성한 서버 원문. 변경해도 기존 주문의 저장된 원문은 바꾸지 않는다. */
@Component
public class OrderTerms {
  private final String fullFundingText;

  public OrderTerms() {
    try (var input =
        new org.springframework.core.io.ClassPathResource("contracts/standard-v2-full-funding.txt")
            .getInputStream()) {
      fullFundingText =
          new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .replace("\r\n", "\n")
              .replace("\r", "\n");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Missing full funding terms", e);
    }
  }

  public String forPolicy(com.example.lancerpick.common.policy.TradePolicyVersion policy) {
    return policy == com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V3
        ? fullFundingText
        : currentText();
  }

  public String currentText() {
    return """
    랜서픽 MVP 거래 규칙 v1
    주문 당시 상품·가격·작업 범위·소요일·구매자 요구사항을 보관하며 주문 후 조건 수정은 지원하지 않습니다.
    판매자 수락 후 구매자가 거래 조건을 확인하고 테스트 적립금으로 결제합니다.
    최초 납기는 결제 완료 시각에 소요일을 더하며 1일은 24시간입니다.
    최초 납품 후 원래 주문 범위 안에서 수정 요청은 1회 가능하고 재납품 후 추가 수정은 불가합니다.
    수정 납기는 수정 요청 시각에 원래 소요일을 더해 계산합니다.
    구매자는 결제 전 주문을 취소할 수 있고 결제 후에는 판매자에게 취소를 요청합니다.
    판매자는 수락 전 거절하거나 수락 후 구매 확정 전 직접 취소할 수 있습니다.
    결제 후 취소 시 전액 환불하며 부분 환불과 구매 확정 후 환불은 지원하지 않습니다.
    대기 중 취소 요청은 1건만 허용하며 납품·수정·구매 확정을 막습니다. 구매자는 요청을 철회할 수 있습니다.
    취소 요청 거절·철회 후 거래를 계속하며 요청 대기만으로 납기는 연장되지 않습니다.
    납기 초과나 구매자 무응답으로 자동 취소·환불·구매 확정하지 않습니다.
    구매 확정은 검수 대기 중에만 가능하며 수정 중·취소 요청 대기 중에는 불가합니다.
    구매 확정과 정산은 함께 처리하며 수수료는 결제 시 고정한 결제액의 10%, 원 미만 버림입니다.
    구매용 적립금과 판매 수익은 분리하며 테스트 적립금은 실제 현금 가치가 없습니다.
    """;
  }
}
