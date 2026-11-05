package com.example.lancerpick.contract.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.document.*;
import com.example.lancerpick.contract.dto.ContractResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class ContractDocumentService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ContractDocumentService.class);
  private final ContractDocumentTransactions transactions;
  private final ContractPdfRenderer renderer;
  private final PrivateContractStorage storage;

  public ContractDocumentService(
      ContractDocumentTransactions transactions,
      ContractPdfRenderer renderer,
      PrivateContractStorage storage) {
    this.transactions = transactions;
    this.renderer = renderer;
    this.storage = storage;
  }

  public ContractResponse get(long memberId, long orderId) {
    return transactions.read(memberId, orderId);
  }

  public ContractResponse get(long memberId, long orderId, Integer version) {
    return transactions.read(memberId, orderId, version);
  }

  public ContractResponse generate(long memberId, long orderId, int version) {
    var claim = transactions.claim(memberId, orderId, version);
    if (claim.job() == null) return claim.response();
    var job = claim.job();
    byte[] bytes;
    String key;
    try {
      bytes = renderer.render(job.contractId(), job.version(), job.canonical());
      key = storage.store(job.key(), bytes);
    } catch (Exception e) {
      transactions.fail(job);
      log.warn(
          "Contract generation failed contractId={} failureType={}",
          job.contractId(),
          e.getClass().getSimpleName());
      throw unavailable("CONTRACT_GENERATION_FAILED", "계약서 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
    // If the commit outcome is unknown, retain the file: it may already be referenced.
    var applied = transactions.complete(job, key, bytes.length, PrivateContractStorage.hash(bytes));
    if (!applied.stored()) {
      if (applied.discardObsoleteFile()) {
        try {
          storage.discardObsolete(key);
        } catch (java.io.IOException e) {
          log.warn("Obsolete contract file retained contractId={}", job.contractId());
        }
      }
      throw new BusinessException(
          HttpStatus.CONFLICT, "CONTRACT_GENERATION_STALE", "주문 또는 계약이 변경되었습니다. 최신 계약을 확인해 주세요.");
    }
    return applied.response();
  }

  public byte[] download(long memberId, long orderId) {
    return download(memberId, orderId, null);
  }

  public byte[] download(long memberId, long orderId, Integer version) {
    var file = transactions.file(memberId, orderId, version);
    try {
      return storage.read(file.key(), file.size(), file.hash());
    } catch (java.io.IOException e) {
      throw unavailable("CONTRACT_FILE_UNAVAILABLE", "계약서 파일을 확인할 수 없습니다. 관리자에게 문의해 주세요.");
    }
  }

  private static BusinessException unavailable(String code, String text) {
    return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, text);
  }
}
