package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminMemberResponses;
import com.example.lancerpick.admin.dto.AdminMemberSanctionRequest;
import com.example.lancerpick.admin.service.AdminMemberService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.service.MemberSanctionCommandService;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/admin 권한·CSRF에 더해 조회/명령 서비스가 현재 DB 관리자 권한을 재검사한다. */
@RestController
@Profile("!demo")
@RequestMapping("/api/admin/members")
public class AdminMemberController {
  private final AdminMemberService queries;
  private final MemberSanctionCommandService commands;
  private final CurrentMemberId members;

  public AdminMemberController(
      AdminMemberService queries, MemberSanctionCommandService commands, CurrentMemberId members) {
    this.queries = queries;
    this.commands = commands;
    this.members = members;
  }

  @GetMapping
  public ResponseEntity<PageResponse<AdminMemberResponses.Summary>> list(
      Authentication authentication,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String restriction,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.list(members.require(authentication), q, restriction, page, size));
  }

  @GetMapping("/{memberId}")
  public ResponseEntity<AdminMemberResponses.Detail> detail(
      Authentication authentication, @PathVariable long memberId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.detail(members.require(authentication), memberId));
  }

  @GetMapping("/{memberId}/sanctions")
  public ResponseEntity<PageResponse<AdminMemberResponses.Sanction>> history(
      Authentication authentication,
      @PathVariable long memberId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.history(members.require(authentication), memberId, page, size));
  }

  @PostMapping("/{memberId}/sanctions")
  public ResponseEntity<AdminMemberResponses.Applied> apply(
      Authentication authentication,
      @PathVariable long memberId,
      @RequestBody AdminMemberSanctionRequest request) {
    long actor = members.require(authentication);
    UUID key;
    SanctionKind kind;
    LocalDateTime end;
    try {
      if (request.requestKey() == null
          || !request
              .requestKey()
              .matches(
                  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        throw invalid();
      key = UUID.fromString(request.requestKey());
      kind = SanctionKind.valueOf(request.kind() == null ? "" : request.kind());
      end =
          OffsetDateTime.parse(request.endsAt() == null ? "" : request.endsAt())
              .withOffsetSameInstant(ZoneOffset.UTC)
              .toLocalDateTime();
    } catch (IllegalArgumentException | DateTimeException failure) {
      throw invalid();
    }
    if (kind == SanctionKind.NONE || end.getYear() < 1000 || end.getYear() > 9999) throw invalid();
    long id = commands.apply(actor, memberId, kind, request.reason(), end, key);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new AdminMemberResponses.Applied(Long.toString(memberId), Long.toString(id)));
  }

  @PostMapping("/{memberId}/sanctions/{sanctionId}/revoke")
  public ResponseEntity<Void> revoke(
      Authentication authentication,
      @PathVariable long memberId,
      @PathVariable long sanctionId,
      @RequestBody AdminMemberSanctionRequest.Revoke request) {
    commands.revoke(members.require(authentication), memberId, sanctionId, request.reason());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "제재 종류·종료 시각·요청 키를 확인해 주세요.");
  }
}
