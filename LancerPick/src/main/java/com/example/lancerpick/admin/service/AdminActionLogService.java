package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.repository.AdminActionLogQueries;
import com.example.lancerpick.admin.repository.AdminActionLogQueries.Filter;
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
 * K6 관리자 작업 감사 이력 목록 조회. 사유에는 관리자가 쓴 내용이 들어 있으므로 현재 DB 역할이 ADMIN일 때만 돌려준다
 * (세션 역할만 믿지 않는다. 강등된 세션은 403).
 */
@Service
@Profile("!demo")
public class AdminActionLogService {
    private final AdminActionLogQueries queries;
    private final MemberRepository members;

    public AdminActionLogService(AdminActionLogQueries queries, MemberRepository members) {
        this.queries = queries;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActionReceipt> list(long adminId, Filter filter, int page, int size) {
        var admin = members.findById(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        if (admin.getRole() != MemberRole.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 작업 이력을 조회할 수 있습니다.");
        if (filter.targetId() != null && filter.action() == null)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "대상 번호는 작업 종류와 함께 조회해 주세요.");
        long total = queries.count(filter);
        long offset = (long) page * size;
        List<AdminActionReceipt> items = offset >= total ? List.of() : queries.find(filter, size, offset);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }
}
