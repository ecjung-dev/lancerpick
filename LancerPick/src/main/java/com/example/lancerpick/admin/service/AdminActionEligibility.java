package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 기존 운영 조회의 후보 판단을 재사용한다. 실행 처리권은 각 업무가 다시 검사해야 한다. */
@Service
@Profile("!demo")
public class AdminActionEligibility {
  private final AdminFinanceQueryService finance;
  private final AdminContractPdfQueryService pdf;
  private final OrderContractRepository contracts;

  public AdminActionEligibility(
      AdminFinanceQueryService finance,
      AdminContractPdfQueryService pdf,
      OrderContractRepository contracts) {
    this.finance = finance;
    this.pdf = pdf;
    this.contracts = contracts;
  }

  /** PDF의 orderId는 요청 본문이 아닌 실제 계약에서 얻는다. 나머지 대상은 주문과 무관하다. */
  public Long requireEligible(long adminId, AdminAction action, long targetId) {
    switch (action) {
      case TOPUP_RECHECK -> {
        var decision = finance.topup(adminId, targetId).manualRecheck();
        require(decision.eligible());
        return null;
      }
      case TOPUP_RECOVERY_RESTART -> {
        var decision = finance.recoveryJob(adminId, targetId).restart();
        require(decision.eligible());
        return null;
      }
      case CONTRACT_PDF_REGENERATE -> {
        var contract =
            contracts
                .findById(targetId)
                .orElseThrow(() ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ADMIN_ACTION_TARGET_NOT_FOUND",
                        "재처리 대상을 찾을 수 없습니다."));
        var inspected = pdf.inspect(adminId, contract.getOrderId(), contract.getVersion());
        require(inspected.generationCandidate().eligible());
        return contract.getOrderId();
      }
    }
    throw new IllegalArgumentException("Unsupported admin action");
  }

  private void require(boolean eligible) {
    if (!eligible)
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "ADMIN_ACTION_NOT_ELIGIBLE",
          "현재 재처리할 수 없는 대상입니다. 대상 상태를 다시 조회해 주세요.");
  }
}
