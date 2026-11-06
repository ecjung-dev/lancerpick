package com.example.lancerpick.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 커밋된 데이터와 별도 스레드로 주문 잠금·순번·멱등·롤백을 검사한다. */
@IntegrationTest
@AutoConfigureMockMvc
class OrderChatIntegrationTests {
  @Autowired OrderChatService chatService;
  @Autowired OrderRepository orderRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  long buyerId, sellerId, outsiderId, productId, categoryId, orderId;
  final List<Long> orderIds = new ArrayList<>();

  record Principal(Long memberId) implements AuthenticatedMember {}

  UsernamePasswordAuthenticationToken auth(long memberId, String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(memberId), null, AuthorityUtils.createAuthorityList(role));
  }

  @BeforeEach
  void fixture() {
    var createdAt =
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
    buyerId = member(createdAt);
    sellerId = member(createdAt);
    outsiderId = member(createdAt);
    String category = "chat-" + UUID.randomUUID();
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", category);
    categoryId =
        jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, category);
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'상품','설명','범위','준비',10000,1,?,?)",
        sellerId,
        categoryId,
        createdAt,
        createdAt);
    productId =
        jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, sellerId);
    orderId = order(createdAt);
  }

  long member(LocalDateTime createdAt) {
    return memberRepository
        .saveAndFlush(Member.create(UUID.randomUUID() + "@chat.test", "fixture", "회원", createdAt))
        .getId();
  }

  long order(LocalDateTime createdAt) {
    return order(createdAt, TradePolicyVersion.LEGACY_V1);
  }

  long order(LocalDateTime createdAt, TradePolicyVersion policy) {
    var order =
        orderRepository.saveAndFlush(
            Order.create(
                buyerId, sellerId, productId, UUID.randomUUID(), 10000L, 1, createdAt, policy));
    orderIds.add(order.getId());
    return order.getId();
  }

  @AfterEach
  void cleanup() {
    for (long id : orderIds) {
      jdbc.update("DELETE FROM order_chat_messages WHERE order_id=?", id);
      jdbc.update("DELETE FROM orders WHERE id=?", id);
    }
    jdbc.update("DELETE FROM products WHERE id=?", productId);
    jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyerId, sellerId, outsiderId);
  }

  @Test
  void httpSendUsesPrincipalAndReplayDoesNotConsumeAnotherSequence() throws Exception {
    String key = UUID.randomUUID().toString();
    var originalUpdatedAt = orderRepository.findById(orderId).orElseThrow().getUpdatedAt();
    var body =
        json.writeValueAsString(
            java.util.Map.of(
                "clientMessageId",
                key,
                "content",
                "  <script>일반 텍스트</script>\r\n두 줄  ",
                "senderId",
                outsiderId,
                "sequence",
                777));
    mvc.perform(
            post(path())
                .with(authentication(auth(buyerId, "ROLE_USER")))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.senderId").value(Long.toString(buyerId)))
        .andExpect(jsonPath("$.sequence").value("1"));
    mvc.perform(
            post(path())
                .with(authentication(auth(buyerId, "ROLE_USER")))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sequence").value("1"));
    mvc.perform(get(path()).with(authentication(auth(sellerId, "ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].content").value("<script>일반 텍스트</script>\n두 줄"))
        .andExpect(jsonPath("$.lastSequence").value("1"));
    error(
        () -> chatService.send(buyerId, orderId, new SendChatMessageRequest(key, "다른 내용")),
        "IDEMPOTENCY_KEY_REUSED");
    assertThat(counter()).isEqualTo(1);
    var order = orderRepository.findById(orderId).orElseThrow();
    assertThat(order.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    assertThat(order.getStatus().name()).isEqualTo("REQUESTED");
  }

  @Test
  void outsiderAndUnrelatedAdminCannotReadOrSendAndCsrfIsRequired() throws Exception {
    for (String role : List.of("ROLE_USER", "ROLE_ADMIN")) {
      mvc.perform(get(path()).with(authentication(auth(outsiderId, role))))
          .andExpect(status().isNotFound());
      mvc.perform(
              post(path())
                  .with(authentication(auth(outsiderId, role)))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(input("private"))))
          .andExpect(status().isNotFound());
    }
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path())
                .with(authentication(auth(buyerId, "ROLE_USER")))
                .contentType("application/json")
                .content(json.writeValueAsString(input("private"))))
        .andExpect(status().isForbidden());
    assertThat(counter()).isZero();
  }

  @Test
  void concurrentReplayPersistsOneMessageAndTwoPartiesCanReuseTheirOwnKey() throws Exception {
    String key = UUID.randomUUID().toString();
    var request = new SendChatMessageRequest(key, "한 번");
    var results =
        parallel(
            () -> chatService.send(buyerId, orderId, request).message().id(),
            () -> chatService.send(buyerId, orderId, request).message().id());
    assertThat(results.getFirst()).isEqualTo(results.getLast());
    assertThat(counter()).isEqualTo(1);
    var next =
        parallel(
            () -> chatService.send(sellerId, orderId, request).message().sequence(),
            () -> chatService.send(buyerId, orderId, input("두 번째")).message().sequence());
    assertThat(next).containsExactlyInAnyOrder("2", "3");
    assertThat(counter()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_chat_messages WHERE order_id=?", Long.class, orderId))
        .isEqualTo(3);
  }

  @Test
  void rollbackRestoresCounterAndMessageAndRetryStartsAtOne() {
    var request = input("롤백");
    assertThatThrownBy(() ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> {
                  chatService.send(buyerId, orderId, request);
                  throw new IllegalStateException("Force transaction rollback");
                }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(counter()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_chat_messages WHERE order_id=?", Long.class, orderId))
        .isZero();
    assertThat(chatService.send(buyerId, orderId, request).message().sequence()).isEqualTo("1");
  }

  @Test
  void closedOrdersStayReadableAndAnExistingRequestCanStillBeReplayed() {
    for (String state : List.of("COMPLETED", "REJECTED", "CANCELLED", "TERMINATED")) {
      var occurredAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
      long targetId =
          order(
              occurredAt.minusMinutes(1),
              "TERMINATED".equals(state)
                  ? TradePolicyVersion.CONTRACT_V2
                  : TradePolicyVersion.LEGACY_V1);
      var request = input("보존");
      var message = chatService.send(buyerId, targetId, request);
      boolean paid = "COMPLETED".equals(state) || "TERMINATED".equals(state);
      jdbc.update(
          "UPDATE orders SET"
              + " status=?,accepted_at=?,buyer_confirmed_terms_at=?,paid_at=?,due_at=?,completed_at=?,cancelled_at=?,terminated_at=?,updated_at=?"
              + " WHERE id=?",
          state,
          paid ? occurredAt : null,
          paid ? occurredAt : null,
          paid ? occurredAt : null,
          paid ? occurredAt.plusDays(1) : null,
          "COMPLETED".equals(state) ? occurredAt : null,
          "CANCELLED".equals(state) ? occurredAt : null,
          "TERMINATED".equals(state) ? occurredAt : null,
          occurredAt,
          targetId);
      assertThat(chatService.list(sellerId, targetId, null, null, 50).canSend()).isFalse();
      assertThat(chatService.list(sellerId, targetId, null, null, 50).items()).hasSize(1);
      assertThat(chatService.send(buyerId, targetId, request).message().id())
          .isEqualTo(message.message().id());
      error(() -> chatService.send(buyerId, targetId, input("금지")), "CHAT_CLOSED");
      assertThat(orderRepository.findById(targetId).orElseThrow().getChatSequence()).isEqualTo(1);
    }
  }

  @Test
  void cursorPagesRecoverInOrderWithoutDuplicatesAndRejectMixedCursors() {
    for (int number = 1; number <= 5; number++)
      chatService.send(buyerId, orderId, input("메시지 " + number));
    var latest = chatService.list(buyerId, orderId, null, null, 2);
    assertThat(latest.items().stream().map(message -> message.sequence()).toList())
        .containsExactly("4", "5");
    assertThat(latest.nextCursor()).isEqualTo("4");
    assertThat(
            chatService.list(buyerId, orderId, 4L, null, 2).items().stream()
                .map(message -> message.sequence())
                .toList())
        .containsExactly("2", "3");
    assertThat(chatService.list(buyerId, orderId, 2L, null, 2).hasMore()).isFalse();
    var recovery = chatService.list(sellerId, orderId, null, 0L, 2);
    assertThat(recovery.items().stream().map(message -> message.sequence()).toList())
        .containsExactly("1", "2");
    assertThat(recovery.nextCursor()).isEqualTo("2");
    assertThat(chatService.list(sellerId, orderId, null, 4L, 2).items()).hasSize(1);
    error(() -> chatService.list(buyerId, orderId, 1L, 0L, 2), "VALIDATION_FAILED");
    error(() -> chatService.list(buyerId, orderId, null, -1L, 2), "VALIDATION_FAILED");
    error(() -> chatService.list(buyerId, orderId, null, null, 101), "VALIDATION_FAILED");
  }

  @Test
  void unicodeLimitsAndWhitespaceAreValidatedOnServer() {
    assertThat(chatService.send(buyerId, orderId, input("😀".repeat(2000))).message().content())
        .hasSize(4000);
    error(() -> chatService.send(buyerId, orderId, input("😀".repeat(2001))), "VALIDATION_FAILED");
    error(() -> chatService.send(buyerId, orderId, input("\u2003\n ")), "VALIDATION_FAILED");
    error(
        () -> chatService.send(buyerId, orderId, new SendChatMessageRequest("bad", "본문")),
        "VALIDATION_FAILED");
  }

  String path() {
    return "/api/orders/" + orderId + "/chat/messages";
  }

  long counter() {
    return orderRepository.findById(orderId).orElseThrow().getChatSequence();
  }

  SendChatMessageRequest input(String content) {
    return new SendChatMessageRequest(UUID.randomUUID().toString(), content);
  }

  static void error(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, failure -> assertThat(failure.code()).isEqualTo(code));
  }

  @SafeVarargs
  final List<String> parallel(Callable<String>... actions) throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(actions.length)) {
      var futures = new ArrayList<Future<String>>();
      for (var action : actions)
        futures.add(
            pool.submit(() -> {
              gate.await();
              return action.call();
            }));
      gate.countDown();
      var results = new ArrayList<String>();
      for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
      return results;
    }
  }
}
