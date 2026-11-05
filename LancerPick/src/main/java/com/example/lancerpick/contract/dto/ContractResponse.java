package com.example.lancerpick.contract.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ContractResponse(
    String id,
    int version,
    String status,
    String specialTerms,
    String canonicalContent,
    String contentHash,
    String fileHash,
    List<Signature> signatures) {
  public record Signature(String memberId, String role, OffsetDateTime signedAt) {}
}
