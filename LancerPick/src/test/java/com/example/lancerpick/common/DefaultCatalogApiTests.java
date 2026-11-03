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
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class DefaultCatalogApiTests extends CommonApiTests {
    @MockitoBean
    OrderCancellationRequestService orderCancellationRequestService;

    @MockitoBean
    CancellationRequestQueries cancellationRequestQueries;
    @MockitoBean
    com.example.lancerpick.settlement.SettlementQueryService settlementQueryService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderConfirmationService orderConfirmationService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderCancellationService orderCancellationService;
    @MockitoBean
    com.example.lancerpick.order.service.OrderPaymentService orderPaymentService;
    @MockitoBean
    com.example.lancerpick.payment.repository.PaymentRepository paymentRepository;
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
}
