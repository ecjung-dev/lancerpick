package com.example.lancerpick.order.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class OrderTests {

    @Test
    void 새_주문은_요청_상태와_동일한_생성수정시각으로_시작한다() {
        LocalDateTime now = LocalDateTime.of(
                2026, 11, 2, 4, 0
        );
        UUID requestKey = UUID.fromString(
                "c00af24a-391c-48d8-9754-d5591ce7df22"
        );

        Order order = Order.create(
                1L,
                2L,
                15L,
                requestKey,
                100000L,
                3,
                now
        );

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.REQUESTED);
        assertThat(order.getRequestKey())
                .isEqualTo(requestKey.toString());
        assertThat(order.getCreatedAt()).isEqualTo(now);
        assertThat(order.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void 자기_상품은_주문할_수_없다() {
        assertThatThrownBy(() -> Order.create(
                1000L,
                1000L,
                15L,
                UUID.randomUUID(),
                100000L,
                3,
                LocalDateTime.of(2026, 11, 2, 4, 0)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status())
                            .isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code())
                            .isEqualTo("SELF_PURCHASE_NOT_ALLOWED");
                });
    }

    @Test
    void 가격과_소요일의_최솟값과_최댓값을_허용한다() {
        assertThatCode(() -> Order.create(
                1L, 2L, 15L, UUID.randomUUID(),
                1L, 1,
                LocalDateTime.of(2026, 11, 2, 4, 0)
        )).doesNotThrowAnyException();

        assertThatCode(() -> Order.create(
                1L, 2L, 15L, UUID.randomUUID(),
                1_000_000L, 30,
                LocalDateTime.of(2026, 11, 2, 4, 0)
        )).doesNotThrowAnyException();
    }

    @Test
    void 범위_밖의_가격을_거절한다() {
        Long[] invalidPrices = {null, 0L, 1_000_001L};

        for (Long price : invalidPrices) {
            assertThatThrownBy(() -> Order.create(
                    1L, 2L, 15L, UUID.randomUUID(),
                    price, 3,
                    LocalDateTime.of(2026, 11, 2, 4, 0)
            ))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.status())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(exception.code())
                                .isEqualTo("VALIDATION_FAILED");
                    });
        }
    }

    @Test
    void 범위_밖의_소요일을_거절한다() {
        Integer[] invalidDays = {null, 0, 31};

        for (Integer days : invalidDays) {
            assertThatThrownBy(() -> Order.create(
                    1L, 2L, 15L, UUID.randomUUID(),
                    100000L, days,
                    LocalDateTime.of(2026, 11, 2, 4, 0)
            ))
                    .as("소요일: %s", days)
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.status())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(exception.code())
                                .isEqualTo("VALIDATION_FAILED");
                    });
        }
    }

    @Test
    void 상품ID가_없거나_양수가_아니면_거절한다() {
        Long[] invalidIds = {null, 0L, -1L};

        for (Long productId : invalidIds) {
            assertInvalidOrder(() -> Order.create(
                    1L, 2L, productId, UUID.randomUUID(),
                    100000L, 3,
                    LocalDateTime.of(2026, 11, 2, 4, 0)
            ));
        }
    }

    @Test
    void 요청키가_없으면_거절한다() {
        assertInvalidOrder(() -> Order.create(
                1L, 2L, 15L, null,
                100000L, 3,
                LocalDateTime.of(2026, 11, 2, 4, 0)
        ));
    }

    @Test
    void 생성시각이_없으면_거절한다() {
        assertInvalidOrder(() -> Order.create(
                1L, 2L, 15L, UUID.randomUUID(),
                100000L, 3, null
        ));
    }

    private void assertInvalidOrder(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code())
                            .isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void 구매자ID가_없거나_양수가_아니면_거절한다() {
        Long[] invalidIds = {null, 0L, -1L};

        for (Long buyerId : invalidIds) {
            assertInvalidOrder(() -> Order.create(
                    buyerId, 2L, 15L, UUID.randomUUID(),
                    100000L, 3,
                    LocalDateTime.of(2026, 11, 2, 4, 0)
            ));
        }
    }

    @Test
    void 판매자ID가_없거나_양수가_아니면_거절한다() {
        Long[] invalidIds = {null, 0L, -1L};

        for (Long sellerId : invalidIds) {
            assertInvalidOrder(() -> Order.create(
                    1L, sellerId, 15L, UUID.randomUUID(),
                    100000L, 3,
                    LocalDateTime.of(2026, 11, 2, 4, 0)
            ));
        }
    }

    @Test
    void 정책_버전을_생략하면_LEGACY_V1이고_지정하면_생성_시_고정한다() {
        Order legacy = Order.create(1L, 2L, 15L, UUID.randomUUID(), 100000L, 3,
                LocalDateTime.of(2026, 11, 2, 4, 0));
        Order contract = Order.create(1L, 2L, 15L, UUID.randomUUID(), 100000L, 3,
                LocalDateTime.of(2026, 11, 2, 4, 0),
                com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V2);

        assertThat(legacy.getPolicyVersion()).isEqualTo("LEGACY_V1");
        assertThat(contract.getPolicyVersion()).isEqualTo("CONTRACT_V2");
        assertThatThrownBy(() -> Order.create(1L, 2L, 15L, UUID.randomUUID(), 100000L, 3,
                LocalDateTime.of(2026, 11, 2, 4, 0), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 계약_주문은_수락_후_계약_대기이고_서명_완료로만_선금_대기가_된다() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 2, 4, 0);
        Order legacy = Order.create(1L, 2L, 15L, UUID.randomUUID(), 100000L, 3, at);
        Order contract = Order.create(1L, 2L, 15L, UUID.randomUUID(), 100000L, 3, at,
                com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V2);

        legacy.accept(at.plusMinutes(1));
        contract.accept(at.plusMinutes(1));
        assertThat(legacy.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(contract.getStatus()).isEqualTo(OrderStatus.AWAITING_CONTRACT);

        assertThatThrownBy(() -> legacy.contractSigned(at.plusMinutes(2)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> contract.contractSigned(at))
                .isInstanceOf(IllegalStateException.class);
        contract.contractSigned(at.plusMinutes(2));
        assertThat(contract.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(contract.getUpdatedAt()).isEqualTo(at.plusMinutes(2));
        assertThatThrownBy(() -> contract.contractSigned(at.plusMinutes(3)))
                .isInstanceOf(BusinessException.class);
    }
}
