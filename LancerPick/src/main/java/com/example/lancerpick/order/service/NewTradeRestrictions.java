package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일반 이용 제한·보안 잠금 회원의 새 거래 시작 차단(U7). 주문 생성과 판매자 수락만 막고, 이미 성립한 거래의 처리 명령은 막지 않는다.
 * 호출자는 해당 회원을 이미 잠근 READ_COMMITTED 트랜잭션 안에서 부른다(제재 적용의 회원 배타 잠금과 직렬화).
 */
@Component
@Profile("!demo")
@Transactional(propagation = Propagation.MANDATORY)
public class NewTradeRestrictions {
  private final MemberSanctionQuery sanctions;

  public NewTradeRestrictions(MemberSanctionQuery sanctions) {
    this.sanctions = sanctions;
  }

  public void requireBuyerCanOrder(long buyerId) {
    if (restricted(buyerId))
      throw restricted("이용 제한 중에는 새 주문을 신청할 수 없습니다.");
  }

  /** 구매자가 주문하려는 상품의 판매자. 판매자 제재 사유·종류는 알리지 않는다. */
  public void requireSellerCanReceiveOrder(long sellerId) {
    if (restricted(sellerId)) throw restricted("현재 이 판매자에게는 새 주문을 신청할 수 없습니다.");
  }

  public void requireSellerCanAccept(long sellerId) {
    if (restricted(sellerId))
      throw restricted("이용 제한 중에는 새 주문을 수락할 수 없습니다. 거절이나 진행 중 거래 처리는 할 수 있습니다.");
  }

  private boolean restricted(long memberId) {
    return sanctions.restriction(memberId).blocksNewActivity();
  }

  private static BusinessException restricted(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, "MEMBER_RESTRICTED", message);
  }
}
