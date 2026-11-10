package com.example.lancerpick.admin.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 서비스의 현재 DB 권한 재검사. /api/admin URL 권한에 더해, 세션 이후 역할이 바뀐 회원을 막는다.
 * 회원이 없으면 401 AUTHENTICATION_REQUIRED, ADMIN이 아니면 403 FORBIDDEN.
 * <ul>
 *   <li>{@link #requireAdmin}: 조회용. 잠그지 않고 역할만 읽는다.
 *   <li>{@link #lockAdmin}: 감사 요청·재처리 실행처럼 회원 → 감사 기록 → 대상 순서로 잠그는 경로 전용. 회원을 공유 잠금한다.
 * </ul>
 * 조회 경로를 공유 잠금으로 바꾸거나 잠금 경로에서 잠금을 빼지 않는다(잠금 순서가 달라진다).
 */
@Component
@Profile("!demo")
public class AdminAuthorization {
  private static final String DEFAULT_FORBIDDEN_MESSAGE = "관리자 권한이 필요합니다.";

  private final MemberRepository members;

  public AdminAuthorization(MemberRepository members) {
    this.members = members;
  }

  public void requireAdmin(long adminId) {
    requireAdmin(adminId, DEFAULT_FORBIDDEN_MESSAGE);
  }

  /** 403 문구만 화면 문맥에 맞게 정한다(코드는 FORBIDDEN 그대로). */
  public void requireAdmin(long adminId, String forbiddenMessage) {
    var role = members.findRoleById(adminId).orElseThrow(AdminAuthorization::authenticationRequired);
    if (role != MemberRole.ADMIN) {
      throw forbidden(forbiddenMessage);
    }
  }

  /** 호출 서비스의 트랜잭션 안에서 관리자 회원을 공유 잠금한 뒤 역할을 확인한다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockAdmin(long adminId) {
    var member =
        members.findByIdForShare(adminId).orElseThrow(AdminAuthorization::authenticationRequired);
    if (member.getRole() != MemberRole.ADMIN) {
      throw forbidden(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  private static BusinessException authenticationRequired() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
  }

  private static BusinessException forbidden(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }
}
