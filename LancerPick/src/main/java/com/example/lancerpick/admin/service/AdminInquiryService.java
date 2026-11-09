package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminInquiryResponses.CancellationSummary;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Detail;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.OrderSummary;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Party;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Summary;
import com.example.lancerpick.admin.repository.AdminInquiryQueries;
import com.example.lancerpick.admin.repository.AdminInquiryQueries.InquiryFilter;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.example.lancerpick.inquiry.repository.SupportInquiryRepository;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.CancellationRequest;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 운영 문의(K4) 목록·상세와 처리할 수 없게 된 문의 종료. 승인/거절 자체는 주문 영역의 AdminCancellationReviewService가
 * 처리한다(컨트롤러가 그대로 호출).
 *
 * <p>세션의 관리자 역할만 믿지 않고 현재 DB 역할을 다시 확인한다(권한이 강등된 세션 차단). 문의 종료는 승인/거절과 같은 잠금 순서(관리자
 * 공유 잠금 → 주문 배타 잠금 → 문의 배타 잠금)를 지켜 교착을 피한다.
 */
@Service
@Profile("!demo")
public class AdminInquiryService {
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String CLOSE = "CLOSE";
    private static final Set<String> STATUSES =
            Set.of(SupportInquiry.OPEN, SupportInquiry.IN_REVIEW, SupportInquiry.CLOSED);
    private static final Set<String> TYPES =
            Set.of(SupportInquiry.CANCELLATION_REVIEW, SupportInquiry.GENERAL);

    private final AdminInquiryQueries queries;
    private final SupportInquiryRepository inquiries;
    private final OrderRepository orders;
    private final CancellationRequestRepository requests;
    private final MemberRepository members;
    private final Clock clock;

    public AdminInquiryService(
            AdminInquiryQueries queries,
            SupportInquiryRepository inquiries,
            OrderRepository orders,
            CancellationRequestRepository requests,
            MemberRepository members,
            Clock clock) {
        this.queries = queries;
        this.inquiries = inquiries;
        this.orders = orders;
        this.requests = requests;
        this.members = members;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(long adminId, String status, String type, int page, int size) {
        requireAdmin(adminId);
        var filter = new InquiryFilter(option(status, STATUSES, "status"), option(type, TYPES, "type"));
        long total = queries.count(filter);
        long offset = (long) page * size;
        List<Summary> items = offset >= total ? List.of() : queries.find(filter, size, offset);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public Detail detail(long adminId, long inquiryId) {
        requireAdmin(adminId);
        return detailOf(inquiries.findById(inquiryId).orElseThrow(AdminInquiryService::notFound));
    }

    /**
     * 승인·거절할 수 없게 된 열린 문의를 닫는다(판매자가 먼저 처리했거나 구매자가 철회한 취소 검토, 또는 일반 문의). 연결된 취소 요청이 아직
     * 대기 중이면 승인/거절로 처리해야 하므로 409. 이미 닫힌 문의는 현재 상태를 그대로 돌려준다(재전송 안전).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Detail close(long adminId, long inquiryId) {
        var admin = orders.findActorForShare(adminId).orElseThrow(AdminInquiryService::unauthorized);
        if (admin.getRole() != MemberRole.ADMIN) throw forbidden();
        // 문의의 주문 ID는 바뀌지 않으므로 잠금 없이 projection으로 읽고, 주문을 먼저 잠근 뒤 문의를 잠근다(문의 → 주문 순서는 교착 위험).
        var target = inquiries.findTarget(inquiryId).orElseThrow(AdminInquiryService::notFound);
        if (target.getOrderId() != null) orders.findForSystemUpdate(target.getOrderId());
        var inquiry = inquiries.findForUpdate(inquiryId).orElseThrow(AdminInquiryService::notFound);
        if (!inquiry.isOpen()) return detailOf(inquiry);
        var request = request(inquiry);
        if (request != null && request.getStatus() == CancellationRequestStatus.PENDING)
            throw new BusinessException(HttpStatus.CONFLICT, "REQUEST_STILL_PENDING",
                    "대기 중인 취소 요청은 승인 또는 거절로 처리해 주세요.");
        LocalDateTime at = UtcTimes.nowMicros(clock);
        inquiry.close(adminId, at);
        inquiries.flush();
        return detailOf(inquiry);
    }

    private Detail detailOf(SupportInquiry inquiry) {
        var request = request(inquiry);
        OrderSummary order = inquiry.getOrderId() == null ? null
                : orders.findById(inquiry.getOrderId())
                        .map(o -> new OrderSummary(
                                o.getId().toString(),
                                o.getStatus().name(),
                                o.getPolicyVersion(),
                                o.getPriceAmount(),
                                at(o.getPaidAt()),
                                party(o.getBuyerId()),
                                party(o.getSellerId())))
                        .orElse(null);
        CancellationSummary cancellation = request == null ? null
                : new CancellationSummary(
                        request.getId().toString(),
                        request.getStatus().name(),
                        request.getReason(),
                        at(request.getRequestedAt()),
                        at(request.getResolvedAt()),
                        request.getResolutionSource(),
                        request.getResponseReason());
        return new Detail(
                inquiry.getId().toString(),
                inquiry.getType(),
                inquiry.getStatus(),
                inquiry.getSubject(),
                party(inquiry.getRequesterId()),
                inquiry.getAssignedAdminId() == null ? null : inquiry.getAssignedAdminId().toString(),
                at(inquiry.getCreatedAt()),
                at(inquiry.getUpdatedAt()),
                at(inquiry.getResolvedAt()),
                order,
                cancellation,
                allowedActions(inquiry, request));
    }

    private static List<String> allowedActions(SupportInquiry inquiry, CancellationRequest request) {
        if (!inquiry.isOpen()) return List.of();
        if (inquiry.isCancellationReview() && request != null
                && request.getStatus() == CancellationRequestStatus.PENDING) return List.of(APPROVE, REJECT);
        return List.of(CLOSE);
    }

    private CancellationRequest request(SupportInquiry inquiry) {
        return inquiry.getCancellationRequestId() == null ? null
                : requests.findById(inquiry.getCancellationRequestId()).orElse(null);
    }

    private Party party(long memberId) {
        return new Party(Long.toString(memberId),
                members.findById(memberId).map(m -> m.getDisplayName()).orElse(null));
    }

    private void requireAdmin(long adminId) {
        var admin = members.findById(adminId).orElseThrow(AdminInquiryService::unauthorized);
        if (admin.getRole() != MemberRole.ADMIN) throw forbidden();
    }

    private static String option(String raw, Set<String> allowed, String field) {
        if (raw == null) return null;
        if (!allowed.contains(raw))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    field + " 값이 올바르지 않습니다.");
        return raw;
    }

    private static OffsetDateTime at(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "문의를 찾을 수 없습니다.");
    }

    private static BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private static BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 운영 문의를 처리할 수 있습니다.");
    }
}
