package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminReviewTimerResponses.ActiveTimer;
import com.example.lancerpick.admin.repository.AdminReviewTimerQueries;
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
 * 관리자 타이머 해제(K5) 화면용 조회. 세션 역할만 믿지 않고 현재 DB 역할을 다시 확인한다. 해제 명령은
 * ReviewTimerService.adminStop(권한·사유·주문 잠금·만료 우선·이력·알림)을 컨트롤러가 그대로 호출한다.
 */
@Service
@Profile("!demo")
public class AdminReviewTimerService {
    private final AdminReviewTimerQueries queries;
    private final MemberRepository members;

    public AdminReviewTimerService(AdminReviewTimerQueries queries, MemberRepository members) {
        this.queries = queries;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActiveTimer> active(long adminId, int page, int size) {
        var admin = members.findById(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        if (admin.getRole() != MemberRole.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 타이머를 조회할 수 있습니다.");
        long total = queries.countActive();
        long offset = (long) page * size;
        List<ActiveTimer> items = offset >= total ? List.of() : queries.findActive(size, offset);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }
}
