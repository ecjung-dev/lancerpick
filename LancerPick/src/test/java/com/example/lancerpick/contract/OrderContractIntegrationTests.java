package com.example.lancerpick.contract;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.entity.OrderContract;
import com.example.lancerpick.contract.repository.*;
import com.example.lancerpick.contract.service.OrderContractService;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.notification.service.OutboxDispatcher;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.order.service.OrderPaymentService;
import com.example.lancerpick.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@Transactional(isolation = Isolation.READ_COMMITTED)
class OrderContractIntegrationTests {
  @Autowired OrderContractService service;
  @Autowired OrderContractRepository contracts;
  @Autowired ContractSignatureRepository signatures;
  @Autowired MemberRepository members;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired OrderPaymentService payments;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager em;
  @Autowired Clock clock;
  @Autowired com.example.lancerpick.order.service.OrderQueryService queries;
  @Autowired com.example.lancerpick.order.service.OrderCancellationService cancellations;
  @Autowired com.example.lancerpick.order.service.OrderConfirmationService confirmations;
  @Autowired com.example.lancerpick.order.service.OrderDecisionService decisions;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired JsonMapper mapper;
  @MockitoSpyBean OrderEventPublisher eventPublisher;

  @Test
  void contractOrderReportsPolicyAndBlocksLegacyMutations() {
    var detail = queries.detail(buyer, Long.toString(orderId));
    assertThat(detail.policyVersion()).isEqualTo("CONTRACT_V2");
    assertThat(detail.status()).isEqualTo("AWAITING_CONTRACT");
    assertThat(detail.allowedActions()).containsExactly("CANCEL");
    assertThat(
            queries
                .list(buyer, com.example.lancerpick.order.dto.OrderSide.BUYER, null, 0, 20)
                .items())
        .allSatisfy(row -> assertThat(row.policyVersion()).isEqualTo("CONTRACT_V2"));
    assertThatThrownBy(() -> confirmations.confirm(buyer, Long.toString(orderId)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("ORDER_POLICY_NOT_READY"));
    assertThatThrownBy(() -> decisions.accept(seller, Long.toString(orderId)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("ORDER_STATE_CONFLICT"));
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_CONTRACT);
  }

  @Test
  void sellerAcceptsContractOrderIntoAwaitingContractAndCanReject() {
    long accepted = requestedContractOrder(100001L);
    var detail = decisions.accept(seller, Long.toString(accepted));
    assertThat(detail.status()).isEqualTo("AWAITING_CONTRACT");
    var history = histories.findByOrderIdOrderByCreatedAtAscIdAsc(accepted).getLast();
    assertThat(history.getAction()).isEqualTo(OrderHistoryAction.ACCEPTED);
    assertThat(history.getToStatus()).isEqualTo(OrderStatus.AWAITING_CONTRACT);
    assertThat(outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", accepted))
        .extracting(e -> e.getType())
        .containsExactly("ORDER_ACCEPTED");
    assertThat(queries.detail(buyer, Long.toString(accepted)).allowedActions())
        .containsExactly("CANCEL");

    long rejected = requestedContractOrder(100001L);
    assertThat(queries.detail(seller, Long.toString(rejected)).allowedActions())
        .containsExactly("ACCEPT", "REJECT");
    assertThat(queries.detail(buyer, Long.toString(rejected)).allowedActions())
        .containsExactly("CANCEL");
    assertThat(decisions.reject(seller, Long.toString(rejected), "일정 불가").status())
        .isEqualTo("REJECTED");
  }

  @Test
  void contractOrderCanBeCancelledBeforeDeposit() {
    long requested = requestedContractOrder(100001L);
    assertThatThrownBy(() -> cancellations.cancel(seller, Long.toString(requested), "취소"))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("ORDER_STATE_CONFLICT"));
    assertThat(cancellations.cancel(buyer, Long.toString(requested), "구매 취소").status())
        .isEqualTo("CANCELLED");

    var draft = service.prepare(seller, orderId, 0, "");
    ready(draft.id());
    var cancelled = cancellations.cancel(buyer, Long.toString(orderId), "계약 전 취소");
    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    assertThat(cancelled.allowedActions()).isEmpty();
    var last = histories.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).getLast();
    assertThat(last.getAction()).isEqualTo(OrderHistoryAction.CANCELLED);
    assertThat(last.getFromStatus()).isEqualTo(OrderStatus.AWAITING_CONTRACT);
    // 취소된 주문의 계약은 더 이상 서명·수정할 수 없다.
    String file = fileHash(draft.id());
    assertThatThrownBy(() -> service.sign(buyer, orderId, 1, draft.contentHash(), file))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("CONTRACT_CONFLICT"));
    assertThatThrownBy(() -> service.prepare(seller, orderId, 1, "changed"))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("CONTRACT_CONFLICT"));
    // 같은 취소 재요청은 현재 결과를 그대로 돌려준다.
    assertThat(cancellations.cancel(buyer, Long.toString(orderId), "계약 전 취소").status())
        .isEqualTo("CANCELLED");
  }

  @Test
  void signedContractCanBeCancelledBeforeDepositButNotAfterDeposit() {
    long second = requestedContractOrder(100001L);
    decisions.accept(seller, Long.toString(second));
    signBoth(second);
    assertThat(queries.detail(buyer, Long.toString(second)).allowedActions())
        .containsExactly("CANCEL");
    assertThat(cancellations.cancel(seller, Long.toString(second), "선금 전 취소").status())
        .isEqualTo("CANCELLED");

    signBoth(orderId);
    em.flush();
    em.clear();
    var order = orders.findById(orderId).orElseThrow();
    // 선금 결제 경로(J4)가 아직 없어 엔티티 전이로 결제 후 상태를 만든다.
    order.pay(
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
    orders.saveAndFlush(order);
    em.clear();
    for (long actor : new long[] {buyer, seller}) {
      assertThatThrownBy(() -> cancellations.cancel(actor, Long.toString(orderId), "선금 후"))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.code()).isEqualTo("ORDER_POLICY_NOT_READY"));
    }
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.IN_PROGRESS);
  }

  /**
   * 10,000원 미만 계약 주문은 V3 ck_orders_v2_price가 저장 단계에서 막는다. 서비스의 초안·확정 시 하한 검사는 이 제약을 409 업무 오류로 한 번
   * 더 지키는 방어선이라 저장된 데이터로는 도달할 수 없다.
   */
  @Test
  void databaseRejectsContractOrderBelowMinimumAmount() {
    em.flush();
    assertThatThrownBy(() -> jdbc.update("UPDATE orders SET price_amount=9999 WHERE id=?", orderId))
        .hasMessageContaining("ck_orders_v2_price");
    assertThatThrownBy(() -> requestedContractOrder(9999L))
        .hasMessageContaining("ck_orders_v2_price");
  }

  @Autowired OrderHistoryRepository histories;
  @Autowired com.example.lancerpick.notification.repository.OutboxEventRepository outbox;

  long requestedContractOrder(long price) {
    long product =
        jdbc.queryForObject("SELECT product_id FROM orders WHERE id=?", Long.class, orderId);
    var order =
        Order.create(
            buyer,
            seller,
            product,
            UUID.randomUUID(),
            price,
            1,
            at,
            com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V2);
    long id = orders.saveAndFlush(order).getId();
    snapshots.saveAndFlush(
        OrderSnapshot.create(id, 0L, "title", "desc", "scope", "prep", "terms", "requirements"));
    em.clear();
    return id;
  }

  void signBoth(long id) {
    var draft = service.prepare(seller, id, 0, "");
    ready(draft.id());
    String file = fileHash(draft.id());
    service.sign(buyer, id, 1, draft.contentHash(), file);
    service.sign(seller, id, 1, draft.contentHash(), file);
  }

  String fileHash(String contractId) {
    return HexFormat.of()
        .formatHex(contracts.findById(Long.parseLong(contractId)).orElseThrow().getFileHash());
  }

  @Autowired PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanupCommittedFixture() {
    if (TransactionSynchronizationManager.isActualTransactionActive() || orderId == 0) return;
    Long product =
        jdbc.queryForObject("SELECT product_id FROM orders WHERE id=?", Long.class, orderId);
    Long category =
        jdbc.queryForObject("SELECT category_id FROM products WHERE id=?", Long.class, product);
    jdbc.update("DELETE FROM notifications WHERE order_id=?", orderId);
    jdbc.update(
        "DELETE FROM contract_signatures WHERE contract_id IN (SELECT id FROM order_contracts WHERE"
            + " order_id=?)",
        orderId);
    jdbc.update("DELETE FROM order_contracts WHERE order_id=?", orderId);
    jdbc.update(
        "DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", orderId);
    jdbc.update("DELETE FROM order_histories WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM orders WHERE id=?", orderId);
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, other);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousSameDraftCreatesOneCurrentVersion() throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(() -> {
            gate.await();
            return service.prepare(seller, orderId, 0, "same");
          });
      var b =
          pool.submit(() -> {
            gate.await();
            return service.prepare(seller, orderId, 0, "same");
          });
      gate.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS).id()).isEqualTo(b.get(20, TimeUnit.SECONDS).id());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_contracts WHERE order_id=?", Long.class, orderId))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousSignaturesAndReplayCommitExactlyTwoRows() throws Exception {
    var draft = service.prepare(seller, orderId, 0, "");
    new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> ready(draft.id()));
    String file =
        HexFormat.of()
            .formatHex(contracts.findById(Long.parseLong(draft.id())).orElseThrow().getFileHash());
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var jobs = new ArrayList<Future<?>>();
      for (long actor : new long[] {buyer, seller, buyer})
        jobs.add(
            pool.submit(() -> {
              gate.await();
              return service.sign(actor, orderId, 1, draft.contentHash(), file);
            }));
      gate.countDown();
      for (var job : jobs) job.get(20, TimeUnit.SECONDS);
    }
    assertThat(contracts.findById(Long.parseLong(draft.id())).orElseThrow().getStatus())
        .isEqualTo("SIGNED");
    assertThat(signatures.findByContractIdOrderByIdAsc(Long.parseLong(draft.id()))).hasSize(2);
    assertThat(outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId))
        .extracting(OutboxEvent::getType)
        .containsExactly(
            "ORDER_CONTRACT_CREATED",
            "ORDER_CONTRACT_SIGNATURE_REQUESTED",
            "ORDER_CONTRACT_SIGNED");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void firstSignerNotifiesOnlyUnsignedCounterpartAndReplaysNeverDuplicate(boolean buyerFirst) {
    var draft = service.prepare(seller, orderId, 0, "");
    new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> ready(draft.id()));
    String file = fileHash(draft.id());
    long first = buyerFirst ? buyer : seller;
    long second = buyerFirst ? seller : buyer;
    assertThatThrownBy(() -> service.sign(other, orderId, 1, draft.contentHash(), file))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    service.sign(first, orderId, 1, draft.contentHash(), file);
    service.sign(first, orderId, 1, draft.contentHash(), file);
    var signature = signatures.findByContractIdOrderByIdAsc(Long.parseLong(draft.id())).getFirst();
    var requests =
        outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId).stream()
            .filter(e -> e.getType().equals(OrderEventPublisher.CONTRACT_SIGNATURE_REQUESTED))
            .toList();
    assertThat(requests).hasSize(1);
    var request = requests.getFirst();
    assertThat(request.getEventId())
        .isEqualTo(OutboxEvent.nameBasedId("CONTRACT_SIGNATURE_REQUESTED", signature.getId()));
    var payload = mapper.readTree(request.getPayload());
    assertThat(payload.get("recipientIds").size()).isEqualTo(1);
    assertThat(payload.get("recipientIds").get(0).asString()).isEqualTo(Long.toString(second));
    assertThat(request.getPayload()).doesNotContain(file, draft.contentHash(), "requirements");
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_CONTRACT);
    assertThat(contracts.findById(Long.parseLong(draft.id())).orElseThrow().getStatus())
        .isEqualTo("READY");
    assertThat(dispatcher.dispatch(request.getId())).isTrue();
    assertThat(dispatcher.dispatch(request.getId())).isFalse();
    assertThat(
            jdbc.queryForList(
                "SELECT recipient_id FROM notifications WHERE event_id=?",
                Long.class,
                request.getEventId()))
        .containsExactly(second);
    // 저장된 서명의 이벤트를 직접 다시 발행해도 중복되지 않는다.
    boolean republished =
        new TransactionTemplate(transactionManager)
            .execute(ignored ->
                eventPublisher.publishFirstContractSignature(
                    orders.findById(orderId).orElseThrow(),
                    contracts.findById(Long.parseLong(draft.id())).orElseThrow(),
                    signature));
    assertThat(republished).isFalse();
    service.sign(second, orderId, 1, draft.contentHash(), file);
    service.sign(first, orderId, 1, draft.contentHash(), file);
    service.sign(second, orderId, 1, draft.contentHash(), file);
    var events = outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId);
    assertThat(events)
        .extracting(OutboxEvent::getType)
        .containsExactly(
            "ORDER_CONTRACT_CREATED",
            "ORDER_CONTRACT_SIGNATURE_REQUESTED",
            "ORDER_CONTRACT_SIGNED");
    var completed = events.getLast();
    assertThat(dispatcher.dispatch(completed.getId())).isTrue();
    assertThat(
            jdbc.queryForList(
                "SELECT recipient_id FROM notifications WHERE event_id=?",
                Long.class,
                completed.getEventId()))
        .containsExactly(first);
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_PAYMENT);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failureToSaveFirstSignatureEventRollsBackSignatureAndCanRetry() {
    var draft = service.prepare(seller, orderId, 0, "");
    new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> ready(draft.id()));
    String file = fileHash(draft.id());
    OrderEventPublisher target = AopTestUtils.getUltimateTargetObject(eventPublisher);
    doThrow(new IllegalStateException("injected event storage failure"))
        .doCallRealMethod()
        .when(target)
        .publishFirstContractSignature(any(), any(), any());
    assertThatThrownBy(() -> service.sign(buyer, orderId, 1, draft.contentHash(), file))
        .isInstanceOf(IllegalStateException.class);
    assertThat(signatures.findByContractIdOrderByIdAsc(Long.parseLong(draft.id()))).isEmpty();
    assertThat(outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId))
        .extracting(OutboxEvent::getType)
        .containsExactly("ORDER_CONTRACT_CREATED");
    assertThat(contracts.findById(Long.parseLong(draft.id())).orElseThrow().getStatus())
        .isEqualTo("READY");
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_CONTRACT);
    service.sign(buyer, orderId, 1, draft.contentHash(), file);
    assertThat(signatures.findByContractIdOrderByIdAsc(Long.parseLong(draft.id()))).hasSize(1);
    assertThat(outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId))
        .extracting(OutboxEvent::getType)
        .containsExactly("ORDER_CONTRACT_CREATED", "ORDER_CONTRACT_SIGNATURE_REQUESTED");
  }

  long buyer, seller, other, orderId;
  LocalDateTime at;

  @BeforeEach
  void setup() {
    at =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MICROS)
            .minusMinutes(1);
    buyer = member();
    seller = member();
    other = member();
    String name = "contract-" + UUID.randomUUID();
    jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
    long category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
    jdbc.update(
        "INSERT INTO"
            + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
            + " VALUES (?,?,'current-title','desc','scope','prep',100001,1,?,?)",
        seller,
        category,
        at,
        at);
    long product =
        jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
    var order =
        Order.create(
            buyer,
            seller,
            product,
            UUID.randomUUID(),
            100001L,
            1,
            at,
            com.example.lancerpick.common.policy.TradePolicyVersion.CONTRACT_V2);
    order.accept(at);
    orderId = orders.saveAndFlush(order).getId();
    snapshots.saveAndFlush(
        OrderSnapshot.create(
            orderId,
            0L,
            "frozen-title",
            "desc",
            "scope",
            "prep",
            "original-terms",
            "requirements"));
    em.clear();
  }

  long member() {
    return members
        .saveAndFlush(Member.create(UUID.randomUUID() + "@contract.test", "fixture", "name", at))
        .getId();
  }

  void ready(String id) {
    // Renderer result fixture only; no real PDF generation is asserted here.
    var c = contracts.findById(Long.parseLong(id)).orElseThrow();
    c.startGeneration(c.getCreatedAt());
    c.completeGeneration(
        c.getGenerationKey(),
        "private/" + c.getGenerationKey() + ".pdf",
        "LOCAL",
        20,
        OrderContract.hash("pdf fixture"),
        c.getCreatedAt());
    contracts.flush();
  }

  @Test
  void frozenSnapshotAndBothSignaturesCompleteContractExactlyOnce() {
    var draft = service.prepare(seller, orderId, 0, "additional scope detail");
    assertThat(draft.canonicalContent())
        .contains("frozen-title", "50000", "50001")
        .doesNotContain("current-title");
    ready(draft.id());
    var c = contracts.findById(Long.parseLong(draft.id())).orElseThrow();
    String file = HexFormat.of().formatHex(c.getFileHash());
    var first = service.sign(buyer, orderId, 1, draft.contentHash(), file);
    assertThat(first.status()).isEqualTo("READY");
    service.sign(buyer, orderId, 1, draft.contentHash(), file);
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_CONTRACT);
    var second = service.sign(seller, orderId, 1, draft.contentHash(), file);
    assertThat(second.status()).isEqualTo("SIGNED");
    assertThat(second.signatures()).hasSize(2);
    assertThat(orders.findById(orderId).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.AWAITING_PAYMENT);
    // 계약 성립 후(선금 대기)에도 같은 서명 재요청은 기존 결과를 돌려준다.
    assertThat(service.sign(seller, orderId, 1, draft.contentHash(), file).signatures()).hasSize(2);
    assertThat(service.sign(buyer, orderId, 1, draft.contentHash(), file).status())
        .isEqualTo("SIGNED");
    var actions =
        histories.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
            .map(h -> h.getAction() + ":" + h.getFromStatus() + ">" + h.getToStatus())
            .toList();
    assertThat(actions)
        .containsExactly(
            "CONTRACT_CREATED:AWAITING_CONTRACT>AWAITING_CONTRACT",
            "CONTRACT_SIGNED:AWAITING_CONTRACT>AWAITING_PAYMENT");
    assertThat(histories.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).getLast().getActorId())
        .isEqualTo(seller);
    assertThat(outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId))
        .extracting(e -> e.getType())
        .containsExactly(
            "ORDER_CONTRACT_CREATED",
            "ORDER_CONTRACT_SIGNATURE_REQUESTED",
            "ORDER_CONTRACT_SIGNED");
    assertThatThrownBy(() -> service.prepare(seller, orderId, 1, "changed"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void replacementPreservesOldSignatureButRejectsItForNewVersion() {
    var first = service.prepare(seller, orderId, 0, "first");
    ready(first.id());
    String file =
        HexFormat.of()
            .formatHex(contracts.findById(Long.parseLong(first.id())).orElseThrow().getFileHash());
    service.sign(buyer, orderId, 1, first.contentHash(), file);
    var second = service.prepare(seller, orderId, 1, "second");
    assertThat(second.version()).isEqualTo(2);
    assertThat(second.signatures()).isEmpty();
    assertThat(contracts.findById(Long.parseLong(first.id())).orElseThrow().getStatus())
        .isEqualTo("SUPERSEDED");
    assertThat(signatures.findByContractIdOrderByIdAsc(Long.parseLong(first.id()))).hasSize(1);
    assertThatThrownBy(() -> service.sign(buyer, orderId, 1, first.contentHash(), file))
        .isInstanceOf(BusinessException.class);
    ready(second.id());
    service.sign(buyer, orderId, second.version(), second.contentHash(), fileHash(second.id()));
    assertThat(
            outbox.findByAggregateTypeAndAggregateIdOrderByIdAsc("ORDER", orderId).stream()
                .filter(e -> e.getType().equals(OrderEventPublisher.CONTRACT_SIGNATURE_REQUESTED))
                .map(OutboxEvent::getEventId)
                .toList())
        .hasSize(2)
        .doesNotHaveDuplicates();
  }

  @Test
  void thirdPartyAndBuyerCannotPrepareDraft() {
    assertThatThrownBy(() -> service.prepare(other, orderId, 0, ""))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    assertThatThrownBy(() -> service.prepare(buyer, orderId, 0, ""))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
  }

  @Test
  void retrySameDraftDoesNotCreateVersionAndStaleChangeFails() {
    var first = service.prepare(seller, orderId, 0, "same");
    em.flush();
    em.clear();
    assertThat(service.prepare(seller, orderId, 0, "same").id()).isEqualTo(first.id());
    assertThatThrownBy(() -> service.prepare(seller, orderId, 0, "different"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void fileHashMismatchAndUnreadyDocumentCannotBeSigned() {
    var first = service.prepare(seller, orderId, 0, "");
    String fake = HexFormat.of().formatHex(OrderContract.hash("fake"));
    assertThatThrownBy(() -> service.sign(buyer, orderId, 1, first.contentHash(), fake))
        .isInstanceOf(BusinessException.class);
    ready(first.id());
    assertThatThrownBy(() -> service.sign(buyer, orderId, 1, first.contentHash(), fake))
        .isInstanceOf(BusinessException.class);
    assertThat(signatures.findByContractIdOrderByIdAsc(Long.parseLong(first.id()))).isEmpty();
  }

  @Test
  void legacyPaymentApiCannotChargeContractOrder() {
    assertThatThrownBy(() -> payments.pay(buyer, Long.toString(orderId), true))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo("CONTRACT_PAYMENT_NOT_READY"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id=?", Long.class, orderId))
        .isZero();
  }

  @Test
  void supersededRendererCannotPublishLateResult() {
    var first = service.prepare(seller, orderId, 0, "one");
    var old = contracts.findById(Long.parseLong(first.id())).orElseThrow();
    old.startGeneration(old.getCreatedAt());
    service.prepare(seller, orderId, 1, "two");
    assertThatThrownBy(() ->
            old.completeGeneration(
                old.getGenerationKey(),
                "private/old.pdf",
                "LOCAL",
                20,
                OrderContract.hash("x"),
                at))
        .isInstanceOf(IllegalStateException.class);
  }
}
