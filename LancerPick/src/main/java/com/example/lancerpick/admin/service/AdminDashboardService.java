package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminDashboardResponse;
import com.example.lancerpick.admin.repository.AdminFinanceQueries;
import com.example.lancerpick.admin.repository.AdminInquiryQueries;
import com.example.lancerpick.admin.repository.AdminInquiryQueries.InquiryFilter;
import com.example.lancerpick.admin.repository.AdminOutboxQueries;
import com.example.lancerpick.admin.repository.AdminOutboxQueries.Issue;
import com.example.lancerpick.admin.repository.AdminReviewTimerQueries;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 운영 현황(K3). 각 관리자 화면이 쓰는 기존 조회(문의·타이머·알림·충전)의 건수만 재사용해 모은다.
 * 조회만 하며 행 잠금을 걸지 않는다. 세션 역할만 믿지 않고 현재 DB 역할을 다시 확인한다.
 */
@Service
@Profile("!demo")
public class AdminDashboardService {
    private final MemberRepository members;
    private final AdminInquiryQueries inquiries;
    private final AdminReviewTimerQueries timers;
    private final AdminOutboxQueries outbox;
    private final AdminFinanceQueries finance;
    private final Clock clock;

    public AdminDashboardService(MemberRepository members, AdminInquiryQueries inquiries, AdminReviewTimerQueries timers,
            AdminOutboxQueries outbox, AdminFinanceQueries finance, Clock clock) {
        this.members = members;
        this.inquiries = inquiries;
        this.timers = timers;
        this.outbox = outbox;
        this.finance = finance;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse summary(long adminId) {
        var admin = members.findById(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        if (admin.getRole() != MemberRole.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 운영 현황을 조회할 수 있습니다.");
        var now = UtcTimes.nowMicros(clock);
        return new AdminDashboardResponse(
                new AdminDashboardResponse.Inquiries(
                        inquiries.count(new InquiryFilter(SupportInquiry.OPEN, null)),
                        inquiries.count(new InquiryFilter(SupportInquiry.IN_REVIEW, null))),
                new AdminDashboardResponse.Timers(
                        timers.countActive(),
                        timers.countActiveExpiringBefore(now.plusHours(24))),
                new AdminDashboardResponse.Notifications(
                        outbox.count(Issue.FAILED, null),
                        outbox.count(Issue.RETRYING, null)),
                new AdminDashboardResponse.Topups(
                        finance.countTopups(new AdminFinanceQueries.TopupFilter(null, WalletTopupStatus.UNKNOWN))),
                now.atOffset(ZoneOffset.UTC));
    }
}
