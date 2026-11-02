package com.example.lancerpick.order.entity;

import com.example.lancerpick.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderHistoryTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 6, 0);

    @Test
    void 최초_이력은_구매자의_CREATED와_요청_상태를_기록한다() {
        OrderHistory history = OrderHistory.created(15L, 3L, NOW);

        assertThat(history.getId()).isNull();
        assertThat(history.getOrderId()).isEqualTo(15L);
        assertThat(history.getActorId()).isEqualTo(3L);
        assertThat(history.getAction()).isEqualTo(OrderHistoryAction.CREATED);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.REQUESTED);
        assertThat(history.getReason()).isNull();
        assertThat(history.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 주문ID가_없거나_양수가_아니면_거절한다() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertInvalid(() -> OrderHistory.created(id, 3L, NOW));
        }
    }

    @Test
    void 구매자ID가_없거나_양수가_아니면_거절한다() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertInvalid(() -> OrderHistory.created(15L, id, NOW));
        }
    }

    @Test
    void 시각이_없거나_DB범위를_벗어나면_거절한다() {
        for (LocalDateTime time : new LocalDateTime[]{null,
                LocalDateTime.of(999, 12, 31, 23, 59),
                LocalDateTime.of(10000, 1, 1, 0, 0)}) {
            assertInvalid(() -> OrderHistory.created(15L, 3L, time));
        }
    }

    @Test
    void DB시각_범위의_양끝을_허용한다() {
        assertThatCode(() -> OrderHistory.created(15L, 3L,
                LocalDateTime.of(1000, 1, 1, 0, 0))).doesNotThrowAnyException();
        assertThatCode(() -> OrderHistory.created(15L, 3L,
                LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999000)))
                .doesNotThrowAnyException();
    }

    @Test
    void 기록시각은_마이크로초_단위로_절삭한다() {
        OrderHistory history = OrderHistory.created(15L, 3L, NOW.withNano(123456789));

        assertThat(history.getCreatedAt()).isEqualTo(NOW.withNano(123456000));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("VALIDATION_FAILED");
                });
    }
}
