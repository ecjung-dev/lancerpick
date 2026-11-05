package com.example.lancerpick.contract.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.contract.dto.*;
import com.example.lancerpick.contract.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders/{orderId}/contract")
public class ContractCommandController {
  private final OrderContractService commands;
  private final ContractDocumentService documents;
  private final CurrentMemberId members;

  // 계약 주문 수락은 일반 주문과 같은 POST /api/orders/{id}/accept(OrderController) 하나만 쓴다.
  public ContractCommandController(
      OrderContractService commands, ContractDocumentService documents, CurrentMemberId members) {
    this.commands = commands;
    this.documents = documents;
    this.members = members;
  }

  @PostMapping("/draft")
  public ResponseEntity<ContractResponse> prepare(
      Authentication auth,
      @PathVariable @Min(1) long orderId,
      @Valid @RequestBody PrepareContractRequest body) {
    return ok(
        commands.prepareHttp(
            members.require(auth),
            orderId,
            body.expectedVersion().intValueExact(),
            body.specialTerms()));
  }

  @PostMapping("/sign")
  public ResponseEntity<ContractResponse> sign(
      Authentication auth,
      @PathVariable @Min(1) long orderId,
      @Valid @RequestBody SignContractRequest body) {
    long actor = members.require(auth);
    // File IO is outside the signing transaction; the locked command rechecks version and both
    // hashes.
    documents.download(actor, orderId, body.version().intValueExact());
    return ok(
        commands.signHttp(
            actor, orderId, body.version().intValueExact(), body.contentHash(), body.fileHash()));
  }

  private static <T> ResponseEntity<T> ok(T value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
