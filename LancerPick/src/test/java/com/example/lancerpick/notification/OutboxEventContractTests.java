package com.example.lancerpick.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.notification.entity.OutboxErrorCode;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** C1 이벤트 계약: 이벤트 ID·스키마 버전·고정 오류 코드. DB 없이 실행한다. */
class OutboxEventContractTests {
  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 0, 0);

  @Test
  void nameBasedIdKeepsTheIdsAlreadyStoredForAutoConfirmNotices() {
    // #105에서 저장된 사전 안내 이벤트와 같은 값이어야 event_id UNIQUE가 중복을 계속 막는다.
    var legacy =
        UUID.nameUUIDFromBytes("AUTO_CONFIRM_NOTICE:42".getBytes(StandardCharsets.UTF_8)).toString();
    assertThat(OrderEventPublisher.autoConfirmNoticeEventId(42)).isEqualTo(legacy);
    assertThat(OutboxEvent.nameBasedId("AUTO_CONFIRM_NOTICE", 42)).isEqualTo(legacy);
    assertThat(OutboxEvent.nameBasedId("AUTO_CONFIRM_NOTICE", 43)).isNotEqualTo(legacy);
  }

  @Test
  void nameBasedIdRejectsUnstableKindsAndSources() {
    assertThatThrownBy(() -> OutboxEvent.nameBasedId("auto", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OutboxEvent.nameBasedId("A:B", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OutboxEvent.nameBasedId("AUTO", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void schemaVersionIsStoredAsGivenAndMustBePositive() {
    var event = OutboxEvent.pending("ORDER_ACCEPTED", 3, "ORDER", 1, "{}", AT);
    assertThat(event.getSchemaVersion()).isEqualTo(3);
    assertThat(UUID.fromString(event.getEventId()).version()).isEqualTo(4);
    assertThatThrownBy(() -> OutboxEvent.pending("ORDER_ACCEPTED", 0, "ORDER", 1, "{}", AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void failureStoresOnlyTheFixedCode() {
    var event = OutboxEvent.pending("ORDER_ACCEPTED", 1, "ORDER", 1, "{}", AT);
    event.fail(OutboxErrorCode.LOCK_UNAVAILABLE, AT, 5, 30);
    assertThat(event.getLastErrorCode()).isEqualTo("LOCK_UNAVAILABLE");
    assertThat(event.getStatus()).isEqualTo(OutboxEvent.PENDING);
    assertThatThrownBy(() -> event.fail(null, AT, 5, 30)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void errorCodesFitTheColumnAndSplitPermanentFromRetryable() {
    for (var code : OutboxErrorCode.values()) assertThat(code.name().length()).isLessThanOrEqualTo(100);
    assertThat(OutboxErrorCode.INVALID_RECIPIENT.permanent()).isTrue();
    assertThat(OutboxErrorCode.LOCK_UNAVAILABLE.permanent()).isFalse();
    assertThat(OutboxErrorCode.PROCESSING_FAILED.permanent()).isFalse();
  }
}
