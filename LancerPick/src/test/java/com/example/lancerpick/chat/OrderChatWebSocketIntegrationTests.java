package com.example.lancerpick.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lancerpick.chat.dto.SendChatMessageRequest;
import com.example.lancerpick.chat.service.OrderChatService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 임시 MySQL·실제 HTTP 로그인 쿠키·네이티브 WebSocket으로 커밋 전달과 세션 폐기를 검증한다. */
@IntegrationTest
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "lancerpick.chat.session-check-ms=100")
class OrderChatWebSocketIntegrationTests {
  @Value("${local.server.port}")
  int port;

  @Autowired MemberRepository members;
  @Autowired OrderRepository orders;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper json;
  @Autowired OrderChatService chat;
  @Autowired PlatformTransactionManager transactionManager;
  long buyerId, sellerId, outsiderId, productId, categoryId, orderId;
  String buyerEmail, sellerEmail, outsiderEmail;
  static final String PASSWORD = "ChatTest123!";
  final List<SocketClient> sockets = new ArrayList<>();
  final List<HttpClient> clients = new ArrayList<>();

  @BeforeEach
  void fixture() {
    LocalDateTime now =
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
    String hash = passwords.encode(PASSWORD);
    buyerEmail = UUID.randomUUID() + "@socket.test";
    sellerEmail = UUID.randomUUID() + "@socket.test";
    outsiderEmail = UUID.randomUUID() + "@socket.test";
    buyerId = members.saveAndFlush(Member.create(buyerEmail, hash, "구매자", now)).getId();
    sellerId = members.saveAndFlush(Member.create(sellerEmail, hash, "판매자", now)).getId();
    outsiderId = members.saveAndFlush(Member.create(outsiderEmail, hash, "다른 회원", now)).getId();
    String category = "socket-" + UUID.randomUUID();
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", category);
    categoryId =
        jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, category);
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'상품','설명','범위','준비',10000,1,?,?)",
        sellerId,
        categoryId,
        now,
        now);
    productId =
        jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, sellerId);
    orderId =
        orders
            .saveAndFlush(
                Order.create(buyerId, sellerId, productId, UUID.randomUUID(), 10000L, 1, now))
            .getId();
  }

  @AfterEach
  void cleanup() {
    for (SocketClient socket : sockets) socket.socket.abort();
    for (HttpClient client : clients) client.close();
    jdbc.update("DELETE FROM order_chat_messages WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM orders WHERE id=?", orderId);
    jdbc.update("DELETE FROM products WHERE id=?", productId);
    jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyerId, sellerId, outsiderId);
  }

  @Test
  void restAndSocketSendReachBothParticipantsAndReplayKeepsOneRow() throws Exception {
    HttpClient buyer = login(buyerEmail), seller = login(sellerEmail);
    SocketClient buyerSocket = connect(buyer, buyerId), sellerSocket = connect(seller, sellerId);
    String firstKey = UUID.randomUUID().toString();
    assertThat(
            post(buyer, messagesPath(), Map.of("clientMessageId", firstKey, "content", "HTTP에서 전송"))
                .statusCode())
        .isEqualTo(201);
    assertMessage(buyerSocket, firstKey, "1");
    assertMessage(sellerSocket, firstKey, "1");
    String nextKey = UUID.randomUUID().toString();
    sellerSocket.send(orderId, nextKey, "소켓에서 전송 😀");
    assertMessage(buyerSocket, nextKey, "2");
    assertMessage(sellerSocket, nextKey, "2");
    sellerSocket.send(orderId, nextKey, "소켓에서 전송 😀");
    assertMessage(sellerSocket, nextKey, "2");
    assertThat(count()).isEqualTo(2);
    assertThat(buyerSocket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void rollbackIsNotDeliveredAndHttpCursorRecoversMessagesWithoutSocket() throws Exception {
    HttpClient buyer = login(buyerEmail);
    SocketClient socket = connect(buyer, buyerId);
    var transaction = new TransactionTemplate(transactionManager);
    assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
              chat.send(
                  buyerId,
                  orderId,
                  new SendChatMessageRequest(UUID.randomUUID().toString(), "롤백 메시지"));
              throw new IllegalStateException("rollback");
            }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count()).isZero();
    assertThat(socket.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
    socket.socket.abort();
    String key = UUID.randomUUID().toString();
    chat.send(sellerId, orderId, new SendChatMessageRequest(key, "연결 중단 중 저장"));
    var response = get(buyer, messagesPath() + "?afterSequence=0");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json.readTree(response.body()).path("items").get(0).path("clientMessageId").asText())
        .isEqualTo(key);
    assertThat(json.readTree(response.body()).path("lastSequence").asText()).isEqualTo("1");
  }

  @Test
  void rejectsAnonymousCrossOriginInvalidCsrfAndAccountMismatch() throws Exception {
    HttpClient anonymous = httpClient();
    assertThatThrownBy(() -> rawSocket(anonymous, "http://localhost:" + port))
        .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
    HttpClient buyer = login(buyerEmail);
    assertThatThrownBy(() -> rawSocket(buyer, "https://foreign.invalid"))
        .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
    SocketClient csrfInvalid = rawSocket(buyer, "http://localhost:" + port);
    csrfInvalid.frame(
        "CONNECT\naccept-version:1.2\nhost:localhost\nX-CSRF-TOKEN:invalid\nX-Chat-Member-Id:"
            + buyerId
            + "\n\n");
    assertThat(csrfInvalid.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4403);
    assertThat(csrfInvalid.closeReason).isEqualTo("CSRF_INVALID");
    SocketClient changed = rawSocket(buyer, "http://localhost:" + port);
    JsonNode token = csrf(buyer);
    changed.frame(
        "CONNECT\naccept-version:1.2\nhost:localhost\n"
            + token.path("headerName").asText()
            + ":"
            + token.path("token").asText()
            + "\nX-Chat-Member-Id:"
            + sellerId
            + "\n\n");
    assertThat(changed.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4401);
  }

  @Test
  void defaultDenyDestinationsAndParticipantCheckBlockOtherMembersAndAdmin() throws Exception {
    HttpClient buyer = login(buyerEmail);
    SocketClient forbidden = connect(buyer, buyerId);
    forbidden.frame("SUBSCRIBE\nid:foreign\ndestination:/queue/chat\n\n");
    assertThat(forbidden.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4403);
    SocketClient spoof = connect(buyer, buyerId);
    spoof.frame("SEND\ndestination:/queue/chat\ncontent-type:application/json\n\n{}");
    assertThat(spoof.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4403);
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", outsiderId);
    SocketClient admin = connect(login(outsiderEmail), outsiderId);
    admin.send(orderId, UUID.randomUUID().toString(), "참여자 아님");
    assertThat(admin.await("MESSAGE")).contains("RESOURCE_NOT_FOUND");
    assertThat(count()).isZero();
  }

  @Test
  void closedOrderRejectsNewWritesButAcknowledgesPreviouslyStoredKey() throws Exception {
    SocketClient socket = connect(login(buyerEmail), buyerId);
    String key = UUID.randomUUID().toString();
    socket.send(orderId, key, "종료 전 메시지");
    assertMessage(socket, key, "1");
    jdbc.update(
        "UPDATE orders SET status='CANCELLED', cancelled_at=UTC_TIMESTAMP(6),"
            + " updated_at=UTC_TIMESTAMP(6) WHERE id=?",
        orderId);
    socket.send(orderId, UUID.randomUUID().toString(), "새 메시지");
    assertThat(socket.await("MESSAGE")).contains("CHAT_CLOSED");
    socket.send(orderId, key, "종료 전 메시지");
    assertMessage(socket, key, "1");
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void logoutClosesLiveConnectionBeforeFurtherDelivery() throws Exception {
    HttpClient buyer = login(buyerEmail);
    SocketClient socket = connect(buyer, buyerId);
    assertThat(post(buyer, "/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    assertThat(socket.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4401);
    chat.send(
        sellerId, orderId, new SendChatMessageRequest(UUID.randomUUID().toString(), "로그아웃 후"));
    assertThat(socket.frames.poll(250, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void passwordChangeRevokesOtherHttpSessionsSocketButKeepsOtherMember() throws Exception {
    HttpClient first = login(buyerEmail), second = login(buyerEmail), seller = login(sellerEmail);
    SocketClient old = connect(second, buyerId), retained = connect(seller, sellerId);
    assertThat(
            post(
                    first,
                    "/api/me/password",
                    Map.of("currentPassword", PASSWORD, "newPassword", "Changed123!"))
                .statusCode())
        .isEqualTo(204);
    assertThat(old.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4401);
    assertThat(get(second, "/api/me").statusCode()).isEqualTo(401);
    String key = UUID.randomUUID().toString();
    retained.send(orderId, key, "다른 회원은 계속 사용");
    assertMessage(retained, key, "1");
    assertThat(old.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
  }

  long count() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM order_chat_messages WHERE order_id=?", Long.class, orderId);
  }

  String messagesPath() {
    return "/api/orders/" + orderId + "/chat/messages";
  }

  HttpClient httpClient() {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    clients.add(client);
    return client;
  }

  HttpClient login(String email) throws Exception {
    HttpClient client = httpClient();
    assertThat(
            post(client, "/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .statusCode())
        .isEqualTo(200);
    return client;
  }

  JsonNode csrf(HttpClient client) throws Exception {
    return json.readTree(get(client, "/api/auth/csrf").body());
  }

  HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(HttpClient client, String path, Object body) throws Exception {
    JsonNode token = csrf(client);
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header(token.path("headerName").asText(), token.path("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  SocketClient rawSocket(HttpClient client, String origin) throws Exception {
    var listener = new SocketClient();
    listener.socket =
        client
            .newWebSocketBuilder()
            .header("Origin", origin)
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener)
            .get(10, TimeUnit.SECONDS);
    sockets.add(listener);
    return listener;
  }

  SocketClient connect(HttpClient client, long memberId) throws Exception {
    JsonNode token = csrf(client);
    SocketClient socket = rawSocket(client, "http://localhost:" + port);
    socket.frame(
        "CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n"
            + token.path("headerName").asText()
            + ":"
            + token.path("token").asText()
            + "\nX-Chat-Member-Id:"
            + memberId
            + "\n\n");
    assertThat(socket.await("CONNECTED")).startsWith("CONNECTED");
    socket.frame("SUBSCRIBE\nid:messages\ndestination:/user/queue/chat\n\n");
    socket.frame("SUBSCRIBE\nid:errors\ndestination:/user/queue/chat-errors\n\n");
    // 처리 순서 보존과 오류 수신으로 두 구독이 준비된 것을 확인한다. 없는 주문은 저장하지 않는다.
    socket.send(Long.MAX_VALUE, UUID.randomUUID().toString(), "준비 확인");
    assertThat(socket.await("MESSAGE")).contains("RESOURCE_NOT_FOUND");
    return socket;
  }

  void assertMessage(SocketClient socket, String key, String sequence) throws Exception {
    String frame = socket.await("MESSAGE");
    JsonNode body = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
    assertThat(body.path("clientMessageId").asText()).isEqualTo(key);
    assertThat(body.path("sequence").asText()).isEqualTo(sequence);
    assertThat(body.path("orderId").asText()).isEqualTo(Long.toString(orderId));
  }

  final class SocketClient implements WebSocket.Listener {
    WebSocket socket;
    final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();
    String closeReason;
    final StringBuilder buffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket socket) {
      socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      buffer.append(data);
      int end;
      while ((end = buffer.indexOf("\0")) >= 0) {
        String frame = buffer.substring(0, end).stripLeading();
        buffer.delete(0, end + 1);
        if (!frame.isEmpty()) frames.add(frame);
      }
      socket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
      closeReason = reason;
      closed.complete(status);
      return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable failure) {
      closed.completeExceptionally(failure);
    }

    void frame(String frame) {
      socket.sendText(frame + "\0", true).join();
    }

    void send(long order, String key, String content) {
      frame(
          "SEND\ndestination:/app/orders/"
              + order
              + "/chat/messages\ncontent-type:application/json\n\n"
              + json.writeValueAsString(Map.of("clientMessageId", key, "content", content)));
    }

    String await(String command) throws Exception {
      String value = frames.poll(8, TimeUnit.SECONDS);
      assertThat(value).as("expected STOMP %s", command).isNotNull().startsWith(command);
      return value;
    }
  }
}
