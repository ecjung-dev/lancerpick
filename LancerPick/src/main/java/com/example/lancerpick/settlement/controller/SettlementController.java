package com.example.lancerpick.settlement.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.settlement.dto.SettlementResponse;
import com.example.lancerpick.settlement.dto.SettlementSummaryResponse;
import com.example.lancerpick.settlement.service.SettlementQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/me/settlements")
public class SettlementController {
  private final SettlementQueryService queries;
  private final CurrentMemberId members;

  public SettlementController(SettlementQueryService queries, CurrentMemberId members) {
    this.queries = queries;
    this.members = members;
  }

  @GetMapping
  public ResponseEntity<PageResponse<SettlementResponse>> list(
      Authentication authentication,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.list(members.require(authentication), page, size, from, to, sort));
  }

  @GetMapping("/summary")
  public ResponseEntity<SettlementSummaryResponse> summary(
      Authentication authentication,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.summary(members.require(authentication), from, to));
  }
}
