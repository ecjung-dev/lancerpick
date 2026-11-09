package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminOutboxResponses.OutboxIssue;
import com.example.lancerpick.admin.repository.AdminOutboxQueries;
import com.example.lancerpick.admin.repository.AdminOutboxQueries.Issue;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 알림 처리 실패 조회(K6). 조회만 하며 재처리(FAILED → PENDING)는 관리자 작업 감사 이력
 * (admin-action-audit-design.md) 확정 뒤 별도 명령으로 연결한다. 세션 역할만 믿지 않고 현재 DB 역할을 다시 확인한다.
 */
@Service
@Profile("!demo")
public class AdminOutboxService {
    private final AdminOutboxQueries queries;
    private final MemberRepository members;

    public AdminOutboxService(AdminOutboxQueries queries, MemberRepository members) {
        this.queries = queries;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxIssue> issues(long adminId, String status, Long orderId, int page, int size) {
        var admin = members.findById(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        if (admin.getRole() != MemberRole.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 알림 처리 상태를 조회할 수 있습니다.");
        Issue issue = parse(status);
        if (orderId != null && orderId <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 번호를 확인해 주세요.");
        long total = queries.count(issue, orderId);
        long offset = (long) page * size;
        List<OutboxIssue> items = offset >= total ? List.of() : queries.find(issue, orderId, size, offset);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    /** 대소문자 그대로의 문서화된 값만 허용한다. 생략하면 FAILED. */
    private static Issue parse(String status) {
        if (status == null || status.equals("FAILED")) return Issue.FAILED;
        if (status.equals("RETRYING")) return Issue.RETRYING;
        throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "조회 상태를 확인해 주세요.");
    }
}
