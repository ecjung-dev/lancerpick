package com.example.lancerpick.support;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G1 금액 불변식(docs/v3-activation-gate.md 4절)을 지정한 주문에만 적용한다. 결제된 CONTRACT_V3 주문마다 결제액 = 미해결 보관액 +
 * 구매자 반환액 + 판매자 지급액 + 수수료이고, 보관 상태와 종료 기록·원장 소유자가 맞아야 한다. 보관금·원장·종료 기록 누락(NULL)도
 * 위반이다. 문서의 조회를 바꾸면 이 조회도 함께 고친다.
 */
public final class V3FundsInvariant {
  private static final String SQL =
      """
      SELECT * FROM (
        SELECT o.id, o.buyer_id, o.seller_id, p.amount AS paid,
               f.order_id AS funds_order, f.payment_id AS funds_payment, f.amount AS funds_amount,
               f.status, f.remaining_amount AS held,
               pay.member_id AS debit_member, -pay.amount_delta AS debit,
               r.member_id AS refund_member, COALESCE(r.amount_delta, 0) AS refund,
               s.member_id AS payout_member, COALESCE(s.amount_delta, 0) AS payout,
               c.order_id AS closure_order, COALESCE(c.fee_amount, 0) AS fee, c.reason, p.id AS payment_id
        FROM orders o
        JOIN payments p ON p.order_id = o.id
        LEFT JOIN order_funds f ON f.order_id = o.id
        LEFT JOIN wallet_entries pay ON pay.payment_id = p.id AND pay.type = 'PAYMENT'
        LEFT JOIN wallet_entries r ON r.payment_id = p.id AND r.type = 'REFUND'
        LEFT JOIN wallet_entries s ON s.payment_id = p.id AND s.type = 'SELLER_PAYOUT'
        LEFT JOIN order_closures c ON c.order_id = o.id
        WHERE o.policy_version = 'CONTRACT_V3' AND o.id IN (%s)
      ) t
      WHERE (
        funds_order IS NOT NULL AND funds_payment = payment_id AND funds_amount = paid
        AND debit_member = buyer_id AND debit = paid
        AND (refund_member IS NULL OR refund_member = buyer_id)
        AND (payout_member IS NULL OR payout_member = seller_id)
        AND paid = held + refund + payout + fee
        AND ( (status = 'HELD'     AND closure_order IS NULL AND refund = 0 AND payout = 0)
           OR (status = 'RELEASED' AND reason IN ('COMPLETED','AUTO_CONFIRMED') AND refund = 0 AND payout > 0)
           OR (status = 'REFUNDED' AND reason IN ('MUTUAL_CANCELLATION','ADMIN_CANCELLATION') AND payout = 0 AND fee = 0) )
      ) IS NOT TRUE
      """;

  private V3FundsInvariant() {}

  /** 위반 행 목록. 비어 있으면 통과다. */
  public static List<Map<String, Object>> violations(JdbcTemplate jdbc, Collection<Long> orderIds) {
    if (orderIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
    return jdbc.queryForList(SQL.formatted(placeholders), orderIds.toArray());
  }
}
