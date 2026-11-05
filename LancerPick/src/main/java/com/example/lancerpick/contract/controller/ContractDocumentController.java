package com.example.lancerpick.contract.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.contract.dto.ContractResponse;
import com.example.lancerpick.contract.dto.GenerateContractPdfRequest;
import com.example.lancerpick.contract.service.ContractDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders/{orderId}/contract")
public class ContractDocumentController {
  private final ContractDocumentService documents;
  private final CurrentMemberId members;

  public ContractDocumentController(ContractDocumentService documents, CurrentMemberId members) {
    this.documents = documents;
    this.members = members;
  }

  @GetMapping
  public ResponseEntity<ContractResponse> get(
      Authentication authentication,
      @PathVariable @Min(1) long orderId,
      @RequestParam(required = false) @Min(1) Integer version) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(documents.get(members.require(authentication), orderId, version));
  }

  @PostMapping("/generate")
  public ResponseEntity<ContractResponse> generate(
      Authentication authentication,
      @PathVariable @Min(1) long orderId,
      @Valid @RequestBody GenerateContractPdfRequest body) {
    var result =
        documents.generate(
            members.require(authentication), orderId, body.version().intValueExact());
    return ResponseEntity.status(
            "GENERATING".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK)
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping(value = "/file", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> file(
      Authentication authentication,
      @PathVariable @Min(1) long orderId,
      @RequestParam(required = false) @Min(1) Integer version) {
    byte[] bytes = documents.download(members.require(authentication), orderId, version);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"contract-" + orderId + ".pdf\"")
        .header("X-Content-Type-Options", "nosniff")
        .contentLength(bytes.length)
        .body(bytes);
  }
}
