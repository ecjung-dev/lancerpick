package com.example.lancerpick.common;

import com.example.lancerpick.product.service.CategoryService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import com.example.lancerpick.wallet.service.WalletQueryService;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.order.service.OrderCreationApiService;
import com.example.lancerpick.order.repository.OrderCreationQueries;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderListQueries;
import com.example.lancerpick.order.service.OrderQueryService;
import com.example.lancerpick.order.service.OrderDecisionService;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderRevisionService;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.repository.CancellationRequestQueries;
import com.example.lancerpick.wallet.service.WalletTopupService;
import com.example.lancerpick.wallet.service.TopupApprovalTransactions;

/** 기본 프로필에도 공개 메인이 동작하는지 동일한 HTTP·Security 계약을 검사한다. */
@ActiveProfiles(value = "default", inheritProfiles = false)
@SpringBootTest(properties = {
        // 개인 접속 파일은 읽지 않는다. DB 제외는 이 테스트 컨텍스트에만 적용한다.
        "spring.config.location=optional:classpath:/default-catalog-test.properties",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "lancerpick.notifications.dispatch-enabled=false",
        "lancerpick.order.review-timer.expiry-enabled=false"
})
class DefaultCatalogApiTests extends CommonApiTests {
    @MockitoBean
    com.example.lancerpick.admin.service.AdminMemberService adminMemberService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminMemberQueries adminMemberQueries;
    @MockitoBean
    com.example.lancerpick.member.service.PasswordRecommendationQuery passwordRecommendationQuery;
    @MockitoBean
    com.example.lancerpick.chat.repository.OrderChatThreadQueries orderChatThreadQueries;
    @MockitoBean
    com.example.lancerpick.member.service.MemberSanctionCommandService memberSanctionCommandService;
    @MockitoBean
    com.example.lancerpick.member.service.MemberSanctionQuery memberSanctionQuery;
    @MockitoBean
    com.example.lancerpick.member.service.SecurityLockDelivery securityLockDelivery;
    @MockitoBean
    com.example.lancerpick.admin.service.AdminContractPdfQueryService adminContractPdfQueryService;
    @MockitoBean
    com.example.lancerpick.admin.service.AdminFinanceQueryService adminFinanceQueryService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminFinanceQueries adminFinanceQueries;
    @MockitoBean
    com.example.lancerpick.chat.service.OrderChatService orderChatService;
    @MockitoBean
    com.example.lancerpick.chat.repository.OrderChatMessageQueries orderChatMessageQueries;
    @MockitoBean
    com.example.lancerpick.order.service.FullFundingService fullFundingService;
    @MockitoBean
    com.example.lancerpick.order.service.AdminCancellationReviewService adminCancellationReviewService;
    // 운영 문의(K4) 접수·관리자 조회도 DB를 사용하므로 대체한다. 실제 처리는 CancellationReviewInquiryIntegrationTests(MySQL).
    @MockitoBean
    com.example.lancerpick.inquiry.service.InquiryService inquiryService;
    @MockitoBean
    com.example.lancerpick.inquiry.repository.SupportInquiryQueries supportInquiryQueries;
    @MockitoBean
    com.example.lancerpick.admin.service.AdminInquiryService adminInquiryService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminInquiryQueries adminInquiryQueries;
    // 관리자 타이머 해제(K5) 목록도 DB를 사용하므로 대체한다. 실제 처리는 CancellationReviewInquiryIntegrationTests(MySQL).
    // 관리자 알림 처리 실패 조회(K6)도 DB를 사용하므로 대체한다. 실제 조회는 AdminOutboxApiIntegrationTests(MySQL).
    @MockitoBean
    com.example.lancerpick.admin.service.AdminOutboxService adminOutboxService;
    // 관리자 운영 현황(K3)도 DB 조회를 모으므로 대체한다. 실제 조회는 AdminDashboardApiIntegrationTests(MySQL).
    @MockitoBean
    com.example.lancerpick.admin.service.AdminDashboardService adminDashboardService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminOutboxQueries adminOutboxQueries;
    @MockitoBean
    com.example.lancerpick.admin.service.AdminReviewTimerService adminReviewTimerService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminReviewTimerQueries adminReviewTimerQueries;
    @MockitoBean
    com.example.lancerpick.order.service.ReviewTimerService reviewTimerService;
    @MockitoBean
    com.example.lancerpick.order.service.SecurityLockTimerStops securityLockTimerStops;
    @MockitoBean
    com.example.lancerpick.order.service.DeadlineNotices deadlineNotices;
    @MockitoBean
    com.example.lancerpick.order.service.ReviewTimers reviewTimers;
    @MockitoBean
    com.example.lancerpick.order.service.OrderCommandLocks orderCommandLocks;
    @MockitoBean
    com.example.lancerpick.contract.service.ContractDocumentTransactions contractDocumentTransactions;
    // This HTTP-only context excludes JPA; contract persistence is tested against MySQL separately.
    @MockitoBean
    com.example.lancerpick.contract.service.OrderContractService orderContractService;
    @MockitoBean
    com.example.lancerpick.notification.service.OrderEventPublisher orderEventPublisher;
    @MockitoBean
    com.example.lancerpick.notification.service.OutboxDispatcher outboxDispatcher;
    @MockitoBean
    com.example.lancerpick.notification.service.PasswordChangeRecommendations passwordChangeRecommendations;
    @MockitoBean
    com.example.lancerpick.notification.service.NotificationService notificationService;
    @MockitoBean
    com.example.lancerpick.notification.repository.NotificationQueries notificationQueries;
    @MockitoBean
    com.example.lancerpick.review.service.ReviewService reviewService;
    @MockitoBean
    com.example.lancerpick.review.repository.ReviewQueries reviewQueries;
    @MockitoBean
    com.example.lancerpick.product.service.MyProductService myProductService;
    @MockitoBean
    com.example.lancerpick.product.repository.MyProductQueries myProductQueries;
    @MockitoBean
    com.example.lancerpick.product.service.ProductManagementService productManagementService;
    @MockitoBean
    com.example.lancerpick.seller.service.SellerProfileService sellerProfileService;
    @MockitoBean
    com.example.lancerpick.product.service.ProductSearchService productSearchService;
    @MockitoBean
    com.example.lancerpick.product.service.ProductFavoriteService productFavoriteService;
    @MockitoBean
    com.example.lancerpick.product.repository.ProductFavoriteQueries productFavoriteQueries;
    @MockitoBean
    com.example.lancerpick.product.repository.ProductSearchQueries productSearchQueries;
    @MockitoBean
    com.example.lancerpick.settlement.repository.SettlementQueryRepository settlementQueryRepository;
    @MockitoBean
    OrderCancellationRequestService orderCancellationRequestService;

    @MockitoBean
    CancellationRequestQueries cancellationRequestQueries;
    @MockitoBean
    com.example.lancerpick.settlement.service.SettlementQueryService settlementQueryService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderConfirmationService orderConfirmationService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderCancellationService orderCancellationService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderPaymentService orderPaymentService;
    @MockitoBean
    com.example.lancerpick.payment.repository.PaymentRepository paymentRepository;
    @MockitoBean
    com.example.lancerpick.payment.repository.OrderFundsRepository orderFundsRepository;
    @MockitoBean
    OrderRevisionService orderRevisionService;
    @MockitoBean
    OrderDeliveryService orderDeliveryService;
    @MockitoBean
    OrderDecisionService orderDecisionService;
    @MockitoBean
    OrderQueryService orderQueryService;

    @MockitoBean
    OrderListQueries orderListQueries;
    @MockitoBean
    OrderCreationApiService orderCreationApiService;

    @MockitoBean
    OrderDetailQueries orderDetailQueries;
    // DB 없는 공통 HTTP 검사에서는 실제 카테고리 DB 조회를 사용하지 않는다.
    @MockitoBean
    private CategoryService categoryService;
    // 관리자 상품 운영도 DB(JdbcTemplate)를 사용하므로 DB 없는 이 컨텍스트에서는 대체한다.
    @MockitoBean
    com.example.lancerpick.admin.service.AdminProductService adminProductService;
    @MockitoBean
    com.example.lancerpick.admin.repository.AdminProductQueries adminProductQueries;
    // 이 컨텍스트는 DB 없는 HTTP 검사다. DB를 사용하는 내부 서비스만 대체한다.
    // 실제 지갑 처리는 별도 MySQL 통합 테스트에서 검증한다.
    @MockitoBean
    WalletInitializationService walletInitializationService;

    @MockitoBean
    WalletQueryService walletQueryService;

    @MockitoBean
    OrderCreationService orderCreationService;

    @MockitoBean
    OrderCreationQueries orderCreationQueries;

    @MockitoBean
    WalletTopupService walletTopupService;

    @MockitoBean
    TopupApprovalTransactions topupApprovalTransactions;

    // 회원가입 서비스도 DB(MemberRepository)를 사용하므로 이 컨텍스트에서는 대체한다.
    // 실제 회원가입 처리는 MySQL을 사용하는 별도 검증에서 확인한다.
    @MockitoBean
    MemberService memberService;

    // 로그인 서비스도 DB(MemberRepository)를 사용하므로 이 컨텍스트에서는 대체한다.
    @MockitoBean
    com.example.lancerpick.auth.service.AuthService authService;

    // 세션 폐기 확인(auth_version 조회)도 DB를 사용하므로 대체한다. 실제 폐기는 PasswordChangeIntegrationTests에서 검증한다.
    @MockitoBean
    com.example.lancerpick.common.security.AuthVersionLookup authVersionLookup;

    // 비밀번호 재설정·요청 제한·발송 결과 기록도 DB(Repository)를 사용하므로 대체한다.
    // 실제 처리는 PasswordResetIntegrationTests(MySQL)에서 검증한다.
    @MockitoBean
    com.example.lancerpick.auth.service.PasswordResetService passwordResetService;
    @MockitoBean
    com.example.lancerpick.auth.service.AuthRequestLimiter authRequestLimiter;
    // 가입 이메일 인증도 DB를 사용하므로 대체한다. 실제 처리는 SignupEmailIntegrationTests(MySQL)에서 검증한다.
    @MockitoBean
    com.example.lancerpick.auth.service.SignupEmailService signupEmailService;
    @MockitoBean
    com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder accountMailDeliveryRecorder;
}
