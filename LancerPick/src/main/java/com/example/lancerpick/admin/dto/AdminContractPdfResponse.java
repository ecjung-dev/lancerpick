package com.example.lancerpick.admin.dto;

import java.time.OffsetDateTime;

/** 운영 메타데이터만 반환한다. 파일 원문·경로·해시·생성 키는 포함하지 않는다. */
public record AdminContractPdfResponse(
    String orderId,
    String contractId,
    int version,
    String status,
    boolean currentVersion,
    boolean fileMetadataPresent,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime checkedAt,
    GenerationCandidate generationCandidate) {
  public enum GenerationKind {
    NONE,
    INITIAL_GENERATION,
    RETRY_FAILED,
    RETRY_EXPIRED
  }

  public enum BlockReason {
    NOT_CURRENT_VERSION,
    ORDER_NOT_WRITABLE,
    CANCELLATION_PENDING,
    DOCUMENT_READY,
    DOCUMENT_SIGNED,
    GENERATION_ACTIVE,
    CLOCK_BEFORE_UPDATE,
    UNSUPPORTED_STATE
  }

  /** 조회 시점의 후보일 뿐 실행 허가·처리권은 아니다. */
  public record GenerationCandidate(boolean eligible, GenerationKind kind, BlockReason reason) {}
}
