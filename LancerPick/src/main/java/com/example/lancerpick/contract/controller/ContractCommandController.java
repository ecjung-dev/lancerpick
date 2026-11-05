package com.example.lancerpick.contract.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.contract.dto.*;
import com.example.lancerpick.contract.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Map;
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
  private final com.example.lancerpick.order.service.OrderDecisionService decisions;

  public ContractCommandController(
      OrderContractService commands,
      ContractDocumentService documents,
      CurrentMemberId members,
      com.example.lancerpick.order.service.OrderDecisionService decisions) {
    this.commands = commands;
    this.documents = documents;
    this.members = members;
    this.decisions = decisions;
  }

  @PostMapping("/accept")
  public ResponseEntity<Map<String, String>> accept(
      Authentication auth, @PathVariable @Min(1) long orderId) {
    var result = decisions.accept(members.require(auth), Long.toString(orderId));
    return ok(Map.of("orderId", result.id(), "status", result.status()));
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
