package com.example.lancerpick.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.chat.service.OrderChatThreadQueryService;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderSnapshotRepository;
import com.example.lancerpick.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class OrderChatThreadIntegrationTests {
  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 1, 0, 0);
  @Autowired MemberRepository members;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderChatService chat;
  @Autowired OrderChatThreadQueryService threads;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  long buyer, seller, outsider, category, first, closed, reverse;

  record Principal(Long memberId) implements AuthenticatedMember {}

  @BeforeEach
  void fixture() {
    buyer = member("구매자");
    seller = member("판매자");
    outsider = member("외부인");
    String name = "threads-" + UUID.randomUUID();
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
    long product = product(seller);
    first = order(buyer, seller, product, "표시_%! 상품", false);
    closed = order(buyer, seller, product, "같은 상대의 다른 주문", true);
    reverse = order(seller, buyer, product(buyer), "판매 거래", false);
    order(seller, outsider, product(outsider), "비밀 상품", false);
    chat.send(
        buyer, first, new SendChatMessageRequest(UUID.randomUUID().toString(), "😀".repeat(150)));
    chat.send(
        seller,
        reverse,
        new SendChatMessageRequest(UUID.randomUUID().toString(), "<script>실행하지 않음</script>"));
    jdbc.update(
        "UPDATE order_chat_messages SET created_at=? WHERE order_id=?", AT.plusDays(1), first);
    jdbc.update(
        "UPDATE order_chat_messages SET created_at=? WHERE order_id=?", AT.plusDays(2), reverse);
    jdbc.update(
        "UPDATE products SET title='변경된 상품',is_hidden=TRUE,sale_status='STOPPED' WHERE id=?",
        product);
    em.flush();
    em.clear();
  }

  long member(String name) {
    return members
        .saveAndFlush(Member.create(UUID.randomUUID() + "@fixture.test", "fixture-hash", name, AT))
        .getId();
  }

  long product(long owner) {
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'title','desc','scope','prep',10000,1,?,?)",
        owner,
        category,
        AT,
        AT);
    return jdbc.queryForObject(
        "SELECT id FROM products WHERE seller_id=? ORDER BY id DESC LIMIT 1", Long.class, owner);
  }

  long order(long customer, long owner, long product, String title, boolean cancel) {
    var order = Order.create(customer, owner, product, UUID.randomUUID(), 10000L, 1, AT);
    if (cancel) order.cancel(AT);
    orders.saveAndFlush(order);
    snapshots.saveAndFlush(
        OrderSnapshot.create(order.getId(), 0L, title, "설명", "범위", "준비", "조건", "요구"));
    return order.getId();
  }

  @Test
  void bothSidesAreOwnedDistinctOrdersIncludingEmptyAndClosedConversations() {
    var result = threads.list(buyer, null, 0, 20);
    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(result.items())
        .extracting(row -> row.orderId())
        .containsExactly("" + reverse, "" + first, "" + closed);
    assertThat(result.items().getFirst().side()).isEqualTo("SELLER");
    var empty = result.items().getLast();
    assertThat(empty.canSend()).isFalse();
    assertThat(empty.orderStatus()).isEqualTo("CANCELLED");
    assertThat(empty.lastMessagePreview()).isNull();
    assertThat(empty.lastMessageAt()).isNull();
    assertThat(empty.counterparty().id()).isEqualTo("" + seller);
    assertThat(threads.list(buyer, "비밀", 0, 20).items()).isEmpty();
    assertThat(threads.list(outsider, null, 0, 20).totalElements()).isEqualTo(1);
  }

  @Test
  void snapshotLiteralSearchNumericIdsAndPagingRemainConsistent() {
    var literal = threads.list(buyer, "_%!", 0, 1);
    assertThat(literal.totalElements()).isEqualTo(1);
    assertThat(literal.items().getFirst().productTitle()).isEqualTo("표시_%! 상품");
    assertThat(literal.items().getFirst().lastMessagePreview()).isEqualTo("😀".repeat(140) + "…");
    assertThat(threads.list(buyer, "변경된 상품", 0, 20).items()).isEmpty();
    assertThat(threads.list(buyer, " 판매자 ", 0, 20).totalElements()).isEqualTo(3);
    assertThat(threads.list(buyer, "" + first, 0, 20).items().getFirst().orderId())
        .isEqualTo("" + first);
    assertThat(threads.list(buyer, "9223372036854775808", 0, 20).items()).isEmpty();
    assertThat(threads.list(buyer, null, 1, 1).items().getFirst().orderId()).isEqualTo("" + first);
    assertThat(threads.list(buyer, null, 9, 1).items()).isEmpty();
  }

  @Test
  void authenticationIdentityAndReadOnlyCacheContractAreEnforced() throws Exception {
    mvc.perform(get("/api/me/order-chats")).andExpect(status().isUnauthorized());
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(new Principal(buyer), null, List.of());
    mvc.perform(
            get("/api/me/order-chats").with(authentication(auth)).param("memberId", "" + outsider))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.items[0].orderId").value("" + reverse))
        .andExpect(jsonPath("$.items[0].lastMessagePreview").value("<script>실행하지 않음</script>"));
    for (var values :
        List.of(
            new String[] {"page", "-1"},
            new String[] {"size", "101"},
            new String[] {"q", "가".repeat(101)})) {
      mvc.perform(get("/api/me/order-chats").with(authentication(auth)).param(values[0], values[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }
}
