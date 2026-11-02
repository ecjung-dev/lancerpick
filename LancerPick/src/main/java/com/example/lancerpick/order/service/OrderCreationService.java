package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.repository.OrderCreationQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.sql.Connection;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Profile("!demo")
public class OrderCreationService {
    private final OrderCreationQueries source;
    private final OrderRepository orders;
    private final OrderSnapshotRepository snapshots;
    private final OrderHistoryRepository histories;
    private final OrderTerms terms;
    private final Clock clock;

    public OrderCreationService(OrderCreationQueries source, OrderRepository orders,
            OrderSnapshotRepository snapshots, OrderHistoryRepository histories, OrderTerms terms, Clock clock) {
        this.source = source;
        this.orders = orders;
        this.snapshots = snapshots;
        this.histories = histories;
        this.terms = terms;
        this.clock = clock;
    }

    /** buyerId는 인증 정보에서만 제공한다. 이 반환값은 HTTP OrderDetail DTO가 아니다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result create(Long buyerId, CreateOrderRequest request) {
        // 없는 요청 키의 RR 간격 잠금이 서로 다른 구매자의 INSERT를 막지 않도록 한다.
        if (!Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED).equals(
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException("Order creation requires a READ_COMMITTED transaction");
        }
        if (buyerId == null || buyerId <= 0) throw authenticationRequired();
        if (request == null || request.expectedProductVersion() == null || request.expectedProductVersion() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 입력값을 확인해 주세요.");
        }
        long productId = request.productIdAsLong();
        String key = request.requestKeyAsUuid().toString();
        String requirements = request.normalizedRequirements();

        // 판매자 ID만 사전 조회해 회원 잠금 순서를 정한다. 상품 상태·버전 검사는 기존 키 확인 후다.
        Long sellerIdForLock = source.findProductSellerId(productId).orElse(null);
        if (!source.lockParticipants(buyerId, sellerIdForLock)) throw authenticationRequired();
        var existing = orders.findForCreation(buyerId, key);
        if (existing.isPresent()) {
            Order order = existing.get();
            var snapshot = snapshots.findForCreation(order.getId()).orElseThrow(() ->
                    new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 조건 기록을 확인해야 합니다."));
            if (order.getProductId() != productId
                    || !snapshot.getProductVersionSnapshot().equals(request.expectedProductVersion())
                    || !snapshot.getRequirements().equals(requirements)) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "다른 주문 입력에 사용된 요청 키입니다.");
            }
            return new Result(order.getId(), false);
        }

        var product = source.lockProduct(productId).orElseThrow(OrderCreationService::productNotFound);
        if (product.hidden()) throw productNotFound();
        // 사전 조회 후 소유자가 바뀌면 잠그지 않은 회원의 FK를 참조하지 않는다.
        if (sellerIdForLock == null || product.sellerId() != sellerIdForLock) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT", "변경된 상품 정보를 다시 확인해 주세요.");
        }
        if (!"ON_SALE".equals(product.saleStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_NOT_ORDERABLE", "현재 주문할 수 없는 상품입니다.");
        }
        if (product.sellerId() == buyerId) {
            throw new BusinessException(HttpStatus.CONFLICT, "SELF_PURCHASE_NOT_ALLOWED", "자신의 상품은 주문할 수 없습니다.");
        }
        if (product.version() != request.expectedProductVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT", "변경된 상품 정보를 다시 확인해 주세요.");
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        Order order = orders.saveAndFlush(Order.create(buyerId, product.sellerId(), product.id(),
                request.requestKeyAsUuid(), product.price(), product.durationDays(), now));
        snapshots.saveAndFlush(OrderSnapshot.create(order.getId(), product.version(), product.title(),
                product.description(), product.scope(), product.preparation(), terms.currentText(), requirements));
        histories.saveAndFlush(OrderHistory.created(order.getId(), buyerId, now));
        return new Result(order.getId(), true);
    }

    public record Result(Long orderId, boolean created) {}

    private static BusinessException authenticationRequired() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private static BusinessException productNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }
}
