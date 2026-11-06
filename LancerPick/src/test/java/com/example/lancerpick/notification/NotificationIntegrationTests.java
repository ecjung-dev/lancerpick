package com.example.lancerpick.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.notification.repository.NotificationRepository;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.notification.service.OutboxDispatcher;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 주문 변경과 아웃박스의 원자성, 처리기의 1회 완료·재시도, 알림 API의 소유권을 실제 DB로 검사한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(NotificationIntegrationTests.ClockConfig.class)
class NotificationIntegrationTests {
  private static final Instant START = Instant.parse("2026-11-05T01:00:00.123456Z");
  private static final LocalDateTime AT = LocalDateTime.ofInstant(START, ZoneOffset.UTC);

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderCreationService creation;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired MutableClock clock;
  @MockitoSpyBean OutboxEventRepository events;
  @MockitoSpyBean NotificationRepository notifications;

  long buyer, seller, third, category, product, order;

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void fixtures() {
    clock.set(START);
    buyer = member();
    seller = member();
    third = member();
    category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "notification-" + UUID.randomUUID());
    product = insert("""
        INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
            price,duration_days,created_at,updated_at) VALUES (?,?,'알림 상품','description','scope','prepare',100000,3,?,?)
        """, seller, category, AT, AT);
    order = creation.create(
            buyer, new CreateOrderRequest(Long.toString(product), 0L, "requirements", UUID.randomUUID().toString()))
        .orderId();
  }

  @AfterEach
  void cleanup() {
    reset(events);
    reset(notifications);
    jdbc.update("DELETE FROM notifications WHERE recipient_id IN (?,?,?)", buyer, seller, third);
    jdbc.update("DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", order);
    jdbc.update("DELETE FROM order_histories WHERE order_id=?", order);
    jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", order);
    jdbc.update("DELETE FROM orders WHERE id=?", order);
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, third);
  }

  @Test
  void orderCreationStoresPendingEventAndDispatchNotifiesOnlyCounterpartyOnce() {
    var ids = eventIds();
    assertThat(ids).hasSize(1);
    assertThat(eventColumn(ids.getFirst(), "type")).isEqualTo("ORDER_CREATED");
    assertThat(eventColumn(ids.getFirst(), "status")).isEqualTo("PENDING");

    assertThat(dispatcher.dispatch(ids.getFirst())).isTrue();
    assertThat(dispatcher.dispatch(ids.getFirst())).isFalse();

    assertThat(eventColumn(ids.getFirst(), "status")).isEqualTo("COMPLETED");
    assertThat(notificationCount(seller)).isEqualTo(1);
    assertThat(notificationCount(buyer)).isZero();
    assertThat(jdbc.queryForObject(
            "SELECT type FROM notifications WHERE recipient_id=? AND order_id=?", String.class, seller, order))
        .isEqualTo("ORDER_CREATED");
  }

  @Test
  void acceptNotifiesBuyerAndListShowsSnapshotTitleNewestFirst() throws Exception {
    dispatchAll();
    clock.advance(Duration.ofMinutes(1));
    command("/api/orders/" + order + "/accept", seller).andExpect(status().isOk());
    dispatchAll();

    mvc.perform(get("/api/notifications").with(authentication(auth(buyer))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].type").value("ORDER_ACCEPTED"))
        .andExpect(jsonPath("$.items[0].orderId").value(Long.toString(order)))
        .andExpect(jsonPath("$.items[0].productTitle").value("알림 상품"))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-05T01:01:00.123456Z"));
    mvc.perform(get("/api/notifications").with(authentication(auth(seller))))
        .andExpect(jsonPath("$.items[0].type").value("ORDER_CREATED"));
    mvc.perform(get("/api/notifications").with(authentication(auth(third))))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void readMarksOnlyOwnNotificationAndIsIdempotent() throws Exception {
    dispatchAll();
    long id = jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id=?", Long.class, seller);
    unread(seller).andExpect(jsonPath("$.count").value(1));

    command("/api/notifications/" + id + "/read", third).andExpect(status().isNotFound());
    command("/api/notifications/" + id + "/read", buyer).andExpect(status().isNotFound());
    command("/api/notifications/abc/read", seller).andExpect(status().isBadRequest());
    unread(seller).andExpect(jsonPath("$.count").value(1));

    clock.advance(Duration.ofSeconds(5));
    command("/api/notifications/" + id + "/read", seller).andExpect(status().isNoContent());
    var firstRead = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", LocalDateTime.class, id);
    clock.advance(Duration.ofSeconds(5));
    command("/api/notifications/" + id + "/read", seller).andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", LocalDateTime.class, id))
        .isEqualTo(firstRead);
    unread(seller).andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void readAllOnlyTouchesRequester() throws Exception {
    dispatchAll();
    command("/api/orders/" + order + "/accept", seller).andExpect(status().isOk());
    dispatchAll();
    unread(buyer).andExpect(jsonPath("$.count").value(1));
    unread(seller).andExpect(jsonPath("$.count").value(1));

    command("/api/notifications/read-all", buyer).andExpect(status().isNoContent());

    unread(buyer).andExpect(jsonPath("$.count").value(0));
    unread(seller).andExpect(jsonPath("$.count").value(1));
  }

  @Test
  void readAllAndSingleReadKeepTheFirstRecordedReadTime() throws Exception {
    dispatchAll();
    long id = jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id=?", Long.class, seller);
    command("/api/notifications/read-all", seller).andExpect(status().isNoContent());
    var firstRead = jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", LocalDateTime.class, id);
    clock.advance(Duration.ofMinutes(1));
    command("/api/notifications/" + id + "/read", seller).andExpect(status().isNoContent());
    command("/api/notifications/read-all", seller).andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", LocalDateTime.class, id))
        .isEqualTo(firstRead);
  }

  @Test
  void payloadRecipientOutsideTheOrderFailsWithoutNotifyingAnyone() {
    jdbc.update("""
        INSERT INTO outbox_events(event_id,type,schema_version,aggregate_type,aggregate_id,payload,status,
            attempt_count,next_attempt_at,created_at)
        VALUES (?,'ORDER_ACCEPTED',1,'ORDER',?,JSON_OBJECT('recipientIds',JSON_ARRAY(?,?)),'PENDING',0,?,?)
        """, UUID.randomUUID().toString(), order, Long.toString(buyer), Long.toString(third), AT, AT);
    long id = eventIds().getLast();

    assertThat(dispatcher.dispatch(id)).isFalse();

    // 수신자 오류는 다시 시도해도 성공할 수 없으므로 첫 실패에서 바로 멈춘다.
    assertThat(eventColumn(id, "status")).isEqualTo("FAILED");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "last_error_code")).isEqualTo("INVALID_RECIPIENT");
    assertThat(notificationCount(third)).isZero();
    assertThat(notificationCount(buyer)).isZero();
  }

  @Test
  void malformedRecipientPayloadFailsImmediately() {
    jdbc.update("""
        INSERT INTO outbox_events(event_id,type,schema_version,aggregate_type,aggregate_id,payload,status,
            attempt_count,next_attempt_at,created_at)
        VALUES (?,'ORDER_ACCEPTED',1,'ORDER',?,JSON_OBJECT('recipientIds',JSON_ARRAY('not-a-number')),'PENDING',0,?,?)
        """, UUID.randomUUID().toString(), order, AT, AT);
    long id = eventIds().getLast();

    assertThat(dispatcher.dispatch(id)).isFalse();

    assertThat(eventColumn(id, "status")).isEqualTo("FAILED");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "last_error_code")).isEqualTo("INVALID_PAYLOAD");
  }

  @Test
  void unsupportedSchemaVersionFailsImmediatelyWithFixedCode() {
    jdbc.update("""
        INSERT INTO outbox_events(event_id,type,schema_version,aggregate_type,aggregate_id,payload,status,
            attempt_count,next_attempt_at,created_at)
        VALUES (?,'ORDER_ACCEPTED',2,'ORDER',?,JSON_OBJECT('recipientIds',JSON_ARRAY(?)),'PENDING',0,?,?)
        """, UUID.randomUUID().toString(), order, Long.toString(buyer), AT, AT);
    long id = eventIds().getLast();

    assertThat(dispatcher.dispatch(id)).isFalse();

    assertThat(eventColumn(id, "status")).isEqualTo("FAILED");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "last_error_code")).isEqualTo("UNSUPPORTED_EVENT");
    assertThat(notificationCount(buyer)).isZero();
  }

  @Test
  void missingOrderFailsImmediatelyWithFixedCode() {
    String eventId = UUID.randomUUID().toString();
    jdbc.update("""
        INSERT INTO outbox_events(event_id,type,schema_version,aggregate_type,aggregate_id,payload,status,
            attempt_count,next_attempt_at,created_at)
        VALUES (?,'ORDER_ACCEPTED',1,'ORDER',?,JSON_OBJECT('recipientIds',JSON_ARRAY(?)),'PENDING',0,?,?)
        """, eventId, Long.MAX_VALUE, Long.toString(buyer), AT, AT);
    long id = jdbc.queryForObject("SELECT id FROM outbox_events WHERE event_id=?", Long.class, eventId);

    // 고정 주문 정리 조건(aggregate_id)에 걸리지 않으므로 이 테스트가 직접 지운다.
    try {
      assertThat(dispatcher.dispatch(id)).isFalse();
      assertThat(eventColumn(id, "status")).isEqualTo("FAILED");
      assertThat(eventColumn(id, "last_error_code")).isEqualTo("AGGREGATE_NOT_FOUND");
    } finally {
      jdbc.update("DELETE FROM outbox_events WHERE id=?", id);
    }
  }

  @Test
  void lockFailureIsRecordedAsRetryableLockCode() {
    long id = eventIds().getFirst();
    var delegate = mockingDetails(events).getMockCreationSettings().getDefaultAnswer();
    var calls = new AtomicInteger();
    // 처리 잠금만 실패하고 실패 기록은 정상 잠근다.
    doAnswer(invocation -> {
          if (calls.getAndIncrement() == 0) throw new CannotAcquireLockException("simulated lock timeout");
          return delegate.answer(invocation);
        })
        .when(events)
        .lockById(id);

    assertThat(dispatcher.dispatch(id)).isFalse();
    reset(events);

    assertThat(eventColumn(id, "status")).isEqualTo("PENDING");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "last_error_code")).isEqualTo("LOCK_UNAVAILABLE");
  }

  @Test
  void failureWhileRecordingFailureDoesNotEscapeDispatch() {
    long id = eventIds().getFirst();
    // 처리와 실패 기록 모두 잠금 대기 초과를 흉내 낸다. 예외가 반복문 밖으로 새면 같은 배치의 다음 이벤트가 막힌다.
    doThrow(new CannotAcquireLockException("simulated lock timeout")).when(events).lockById(id);

    assertThat(dispatcher.dispatch(id)).isFalse();
    reset(events);

    assertThat(eventColumn(id, "status")).isEqualTo("PENDING");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("0");
    assertThat(dispatcher.dispatch(id)).as("picked up again on the next run").isTrue();
    assertThat(notificationCount(seller)).isEqualTo(1);
  }

  @Test
  void failureAfterAnotherDispatcherDeferredTheEventIsNotCountedAgain() {
    long id = eventIds().getFirst();
    doThrow(new IllegalStateException("simulated delivery failure")).when(notifications).save(any());
    assertThat(dispatcher.dispatch(id)).isFalse();
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    var deferredUntil = eventColumn(id, "next_attempt_at");

    // 경합한 다른 처리기: 잠금 대기에서 실패했지만, 실패를 기록할 때는 이미 앞 처리기가 다음 시도를 미뤄 둔 상태다.
    // 저장소 스파이는 인터페이스 프록시라 callRealMethod 대신 스파이의 기본 응답(실제 빈 위임)을 쓴다.
    var delegate = mockingDetails(events).getMockCreationSettings().getDefaultAnswer();
    var calls = new AtomicInteger();
    doAnswer(invocation -> {
          if (calls.getAndIncrement() == 0) throw new CannotAcquireLockException("simulated lock timeout");
          return delegate.answer(invocation);
        })
        .when(events)
        .lockById(id);
    assertThat(dispatcher.dispatch(id)).isFalse();
    assertThat(calls).as("process and failure recording both locked the event").hasValue(2);

    assertThat(eventColumn(id, "status")).isEqualTo("PENDING");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "next_attempt_at")).isEqualTo(deferredUntil);
  }

  @Test
  void outboxFailureRollsBackOrderChange() throws Exception {
    doThrow(new IllegalStateException("simulated outbox failure")).when(events).saveAndFlush(any());
    command("/api/orders/" + order + "/accept", seller).andExpect(status().isInternalServerError());
    reset(events);

    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo("REQUESTED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, order)).isEqualTo(1);
    assertThat(eventIds()).hasSize(1);
  }

  @Test
  void deliveryFailureKeepsOrderAndRetriesWithBackoffUntilFailed() {
    long id = eventIds().getFirst();
    doThrow(new IllegalStateException("simulated delivery failure")).when(notifications).save(any());

    assertThat(dispatcher.dispatch(id)).isFalse();
    assertThat(eventColumn(id, "status")).isEqualTo("PENDING");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("1");
    assertThat(eventColumn(id, "last_error_code")).isEqualTo("PROCESSING_FAILED");
    assertThat(dispatcher.dispatch(id)).isFalse();
    assertThat(eventColumn(id, "attempt_count")).as("not due before backoff").isEqualTo("1");

    for (int attempt = 2; attempt <= 5; attempt++) {
      clock.advance(Duration.ofHours(2));
      assertThat(dispatcher.dispatch(id)).isFalse();
    }
    assertThat(eventColumn(id, "status")).isEqualTo("FAILED");
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("5");
    clock.advance(Duration.ofHours(2));
    assertThat(dispatcher.dispatch(id)).isFalse();
    assertThat(eventColumn(id, "attempt_count")).isEqualTo("5");

    assertThat(notificationCount(seller)).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order)).isEqualTo("REQUESTED");
  }

  @Test
  void concurrentDispatchCompletesEventOnce() throws Exception {
    long id = eventIds().getFirst();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = List.of(1, 2).stream().map(i -> pool.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        return dispatcher.dispatch(id);
      })).toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      start.countDown();
    }
    assertThat(notificationCount(seller)).isEqualTo(1);
  }

  @Test
  void notificationApiRequiresAuthenticationAndCsrf() throws Exception {
    mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/notifications/read-all").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/notifications/read-all").with(authentication(auth(buyer))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    mvc.perform(get("/api/notifications?size=101").with(authentication(auth(buyer))))
        .andExpect(status().isBadRequest());
  }

  private void dispatchAll() {
    for (long id : eventIds()) dispatcher.dispatch(id);
  }

  private List<Long> eventIds() {
    return jdbc.queryForList(
        "SELECT id FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=? ORDER BY id", Long.class, order);
  }

  private String eventColumn(long id, String column) {
    return jdbc.queryForObject("SELECT " + column + " FROM outbox_events WHERE id=?", String.class, id);
  }

  private long notificationCount(long recipient) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=?", Long.class, recipient);
  }

  private ResultActions unread(long member) throws Exception {
    return mvc.perform(get("/api/notifications/unread-count").with(authentication(auth(member))))
        .andExpect(status().isOk());
  }

  private ResultActions command(String path, long actor) throws Exception {
    return mvc.perform(post(path).with(csrf()).with(authentication(auth(actor))));
  }

  private static UsernamePasswordAuthenticationToken auth(long member) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(member), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
  }

  private long member() {
    return insert(
        "INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)",
        UUID.randomUUID() + "@notification.test", AT, AT);
  }

  private long insert(String sql, Object... values) {
    var key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
      return statement;
    }, key);
    return key.getKey().longValue();
  }

  static final class MutableClock extends Clock {
    private volatile Instant now = START;

    void set(Instant instant) { now = instant; }
    void advance(Duration duration) { now = now.plus(duration); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  @TestConfiguration
  static class ClockConfig {
    @Bean @Primary MutableClock notificationClock() { return new MutableClock(); }
  }
}
