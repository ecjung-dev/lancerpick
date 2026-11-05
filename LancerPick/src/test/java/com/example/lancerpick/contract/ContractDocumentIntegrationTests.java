package com.example.lancerpick.contract;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.contract.document.*;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.contract.service.*;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderSnapshot;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real commits/locks, actual PDF bytes and Security filters; no surrounding test transaction. */
@IntegrationTest
@AutoConfigureMockMvc
class ContractDocumentIntegrationTests {
  static final Path directory = temporaryDirectory();

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("contracts.storage-root", directory::toString);
  }

  private static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("lancerpick-contract-test-");
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Autowired ContractDocumentService documents;
  @Autowired ContractDocumentTransactions transactions;
  @Autowired OrderContractService drafts;
  @Autowired OrderContractRepository contracts;
  @Autowired MemberRepository members;
  @Autowired OrderRepository orders;
  @Autowired OrderSnapshotRepository snapshots;
  @Autowired JdbcTemplate jdbc;
  @Autowired Clock clock;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  @MockitoSpyBean ContractPdfRenderer renderer;
  @MockitoSpyBean PrivateContractStorage storage;
  @MockitoSpyBean com.example.lancerpick.notification.service.OrderEventPublisher events;
  long buyer, seller, other, orderId, product, category;

  record Principal(Long memberId) implements AuthenticatedMember {}

  private UsernamePasswordAuthenticationToken auth(long id) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new Principal(id), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
  }

  private String path() {
    return "/api/orders/" + orderId + "/contract";
  }

  @BeforeEach
  void fixture() {
    new TransactionTemplate(manager)
        .executeWithoutResult(ignored -> {
          var at =
              LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                  .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                  .minusMinutes(1);
          buyer = member(at);
          seller = member(at);
          other = member(at);
          String name = "pdf-" + UUID.randomUUID();
          jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", name);
          category =
              jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, name);
          jdbc.update(
              "INSERT INTO"
                  + " products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,created_at,updated_at)"
                  + " VALUES (?,?,'상품','설명','범위','준비',100001,1,?,?)",
              seller,
              category,
              at,
              at);
          product =
              jdbc.queryForObject("SELECT id FROM products WHERE seller_id=?", Long.class, seller);
          var order = Order.create(buyer, seller, product, UUID.randomUUID(), 100001L, 1, at);
          order.accept(at);
          orderId = orders.saveAndFlush(order).getId();
          snapshots.saveAndFlush(
              OrderSnapshot.create(
                  orderId, 0L, "보관 상품", "보관 설명", "보관 범위", "보관 준비", "보관 조건", "구매자 요청"));
          jdbc.update(
              "UPDATE orders SET policy_version='CONTRACT_V2',status='AWAITING_CONTRACT' WHERE"
                  + " id=?",
              orderId);
        });
    drafts.prepare(seller, orderId, 0, "특약: 원래 범위를 지킵니다.");
  }

  private long member(LocalDateTime at) {
    return members
        .saveAndFlush(Member.create(UUID.randomUUID() + "@pdf.test", "fixture", "표시 이름", at))
        .getId();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (orderId == 0) return;
    var files =
        jdbc.queryForList(
            "SELECT file_storage_key FROM order_contracts WHERE order_id=? AND file_storage_key IS"
                + " NOT NULL",
            String.class,
            orderId);
    jdbc.update(
        "DELETE FROM contract_signatures WHERE contract_id IN (SELECT id FROM order_contracts WHERE"
            + " order_id=?)",
        orderId);
    jdbc.update("DELETE FROM order_contracts WHERE order_id=?", orderId);
    jdbc.update(
        "DELETE FROM notifications WHERE event_id IN (SELECT event_id FROM outbox_events WHERE"
            + " aggregate_type='ORDER' AND aggregate_id=?)",
        orderId);
    jdbc.update(
        "DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", orderId);
    jdbc.update("DELETE FROM order_histories WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM orders WHERE id=?", orderId);
    jdbc.update("DELETE FROM products WHERE id=?", product);
    jdbc.update("DELETE FROM categories WHERE id=?", category);
    jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", buyer, seller, other);
    for (String key : files) storage.discardObsolete(key);
  }

  @AfterAll
  static void removeTemporaryStorage() throws Exception {
    try (var files = Files.list(directory)) {
      for (Path file : files.toList()) Files.delete(file);
    }
    Files.delete(directory);
  }

  @Test
  void pdfIsOutsideTransactionsAndBothPartiesDownloadExactlyHashedFile() throws Exception {
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return call.callRealMethod();
            })
        .when(renderer)
        .render(anyLong(), anyInt(), anyString());
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return call.callRealMethod();
            })
        .when(storage)
        .store(anyString(), any());
    var ready = documents.generate(seller, orderId, 1);
    assertThat(ready.status()).isEqualTo("READY");
    byte[] pdf = documents.download(buyer, orderId);
    assertThat(documents.download(seller, orderId)).isEqualTo(pdf);
    assertThat(HexFormat.of().formatHex(PrivateContractStorage.hash(pdf)))
        .isEqualTo(ready.fileHash());
    assertThat(documents.generate(seller, orderId, 1).fileHash()).isEqualTo(ready.fileHash());
    drafts.sign(buyer, orderId, 1, ready.contentHash(), ready.fileHash());
    assertThat(drafts.sign(seller, orderId, 1, ready.contentHash(), ready.fileHash()).status())
        .isEqualTo("SIGNED");
    assertThat(documents.download(buyer, orderId)).isEqualTo(pdf);
    verify(renderer, times(1)).render(anyLong(), anyInt(), anyString());
    var response =
        mvc.perform(get(path() + "/file").with(authentication(auth(buyer))))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"contract-" + orderId + ".pdf\""))
            .andReturn();
    assertThat(response.getResponse().getContentAsByteArray()).isEqualTo(pdf);
    mvc.perform(get(path()).with(authentication(auth(buyer))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileHash").value(ready.fileHash()))
        .andExpect(jsonPath("$.signatures[0].signedAt", org.hamcrest.Matchers.endsWith("Z")))
        .andExpect(jsonPath("$.fileStorageKey").doesNotExist())
        .andExpect(jsonPath("$.generationKey").doesNotExist());
  }

  @Test
  void authenticatedOwnerAndCsrfAndVersionAreRequired() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(get(path()).with(authentication(auth(other)))).andExpect(status().isNotFound());
    mvc.perform(get(path() + "/file").with(authentication(auth(other))))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(seller)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(buyer)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    for (String body :
        List.of(
            "{}",
            "{\"version\":0}",
            "{\"version\":null}",
            "{\"version\":1.5}",
            "{\"version\":2147483648}",
            "{\"version\":\"invalid\"}")) {
      mvc.perform(
              post(path() + "/generate")
                  .with(authentication(auth(seller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
        .andExpect(status().isConflict());
    mvc.perform(get(path()).param("version", "0").with(authentication(auth(buyer))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(renderer);
  }

  @Test
  void contractWaitingPostSucceedsAndLegacyPolicyCannotUseDocumentApi() throws Exception {
    jdbc.update("UPDATE orders SET status='AWAITING_CONTRACT' WHERE id=?", orderId);
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"));
    jdbc.update(
        "UPDATE orders SET policy_version='LEGACY_V1',status='AWAITING_PAYMENT' WHERE id=?",
        orderId);
    mvc.perform(get(path()).with(authentication(auth(buyer)))).andExpect(status().isNotFound());
  }

  @Test
  void callerCannotHoldTransactionAcrossPdfGeneration() {
    assertThatThrownBy(() ->
            new TransactionTemplate(manager)
                .executeWithoutResult(ignored -> documents.generate(seller, orderId, 1)))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("DRAFT");
  }

  @Test
  void failedGenerationIsPersistedAndRetryRotatesAttemptKey() throws Exception {
    String first =
        contracts.findByOrderIdAndActiveSlot(orderId, 1).orElseThrow().getGenerationKey();
    doThrow(new IOException("private-path-must-not-leak"))
        .when(renderer)
        .render(anyLong(), anyInt(), anyString());
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CONTRACT_GENERATION_FAILED"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-path"))));
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("FAILED");
    doCallRealMethod().when(renderer).render(anyLong(), anyInt(), anyString());
    assertThat(documents.generate(seller, orderId, 1).status()).isEqualTo("READY");
    assertThat(contracts.findByOrderIdAndActiveSlot(orderId, 1).orElseThrow().getGenerationKey())
        .isNotEqualTo(first);
  }

  @Test
  void storageFailureAlsoMarksFailedWithoutPublishingMetadata() throws Exception {
    doThrow(new IOException("disk unavailable")).when(storage).store(anyString(), any());
    expectError(() -> documents.generate(seller, orderId, 1), "CONTRACT_GENERATION_FAILED");
    var c = contracts.findByOrderIdAndActiveSlot(orderId, 1).orElseThrow();
    assertThat(c.getStatus()).isEqualTo("FAILED");
    assertThat(c.getFileHash()).isNull();
  }

  @Test
  void concurrentClaimsHaveOnlyOneWorkerAndFreshGenerationReturns202() throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<ContractDocumentTransactions.Claim> claim =
          () -> {
            gate.await();
            return transactions.claim(seller, orderId, 1);
          };
      var first = pool.submit(claim);
      var second = pool.submit(claim);
      gate.countDown();
      var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      assertThat(results.stream().filter(c -> c.job() != null).count()).isEqualTo(1);
    }
    mvc.perform(
            post(path() + "/generate")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("GENERATING"));
    verifyNoInteractions(renderer);
  }

  @Test
  void expiredGenerationCanRetryButLateOldCompletionCannotPublish() {
    var old = transactions.claim(seller, orderId, 1).job();
    jdbc.update(
        "UPDATE order_contracts SET created_at=DATE_SUB(created_at, INTERVAL 5"
            + " MINUTE),updated_at=DATE_SUB(updated_at, INTERVAL 5 MINUTE) WHERE order_id=?",
        orderId);
    var retry = transactions.claim(seller, orderId, 1).job();
    assertThat(retry).isNotNull();
    assertThat(retry.key()).isNotEqualTo(old.key());
    byte[] hash = PrivateContractStorage.hash("pdf".getBytes());
    assertThat(transactions.complete(old, old.key() + ".pdf", 3, hash).stored()).isFalse();
    transactions.fail(old);
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("GENERATING");
    transactions.fail(retry);
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("FAILED");
  }

  @Test
  void versionChangedDuringRenderingRejectsAndDeletesOnlyObsoleteFile() throws Exception {
    String key =
        contracts.findByOrderIdAndActiveSlot(orderId, 1).orElseThrow().getGenerationKey() + ".pdf";
    doAnswer(
            call -> {
              drafts.prepare(seller, orderId, 1, "새 특약");
              return call.callRealMethod();
            })
        .when(renderer)
        .render(anyLong(), anyInt(), anyString());
    expectError(() -> documents.generate(seller, orderId, 1), "CONTRACT_GENERATION_STALE");
    assertThat(documents.get(buyer, orderId).version()).isEqualTo(2);
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("DRAFT");
    assertThat(Files.exists(directory.resolve(key))).isFalse();
  }

  @Test
  void cancelledDuringRenderingCannotPublish() throws Exception {
    doAnswer(
            call -> {
              cancel();
              return call.callRealMethod();
            })
        .when(renderer)
        .render(anyLong(), anyInt(), anyString());
    expectError(() -> documents.generate(seller, orderId, 1), "CONTRACT_GENERATION_STALE");
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("FAILED");
  }

  @Test
  void finishedDocumentAndReplacedVersionRemainAvailableOnlyToParties() throws Exception {
    documents.generate(seller, orderId, 1);
    byte[] first = documents.download(buyer, orderId);
    drafts.prepare(seller, orderId, 1, "교체된 특약");
    assertThat(documents.get(buyer, orderId, 1).status()).isEqualTo("SUPERSEDED");
    assertThat(documents.download(buyer, orderId, 1)).isEqualTo(first);
    expectError(() -> documents.download(buyer, orderId), "CONTRACT_CONFLICT");
    documents.generate(seller, orderId, 2);
    cancel();
    assertThat(documents.download(seller, orderId)).isNotEmpty();
    assertThat(documents.download(buyer, orderId, 1)).isEqualTo(first);
    mvc.perform(get(path() + "/file").param("version", "1").with(authentication(auth(other))))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingAndTamperedFilesReturn503WithoutLeakingBytes() throws Exception {
    documents.generate(seller, orderId, 1);
    var file = transactions.file(buyer, orderId);
    Files.write(directory.resolve(file.key()), new byte[(int) file.size()]);
    mvc.perform(get(path() + "/file").with(authentication(auth(buyer))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CONTRACT_FILE_UNAVAILABLE"));
    Files.delete(directory.resolve(file.key()));
    expectError(() -> documents.download(buyer, orderId), "CONTRACT_FILE_UNAVAILABLE");
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("READY");
  }

  private void cancel() {
    var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    jdbc.update(
        "UPDATE orders SET status='CANCELLED',cancelled_at=?,updated_at=? WHERE id=?",
        now,
        now,
        orderId);
  }

  @Test
  void httpAcceptDraftPdfAndBothSignaturesAdvanceOrderExactlyOnce() throws Exception {
    jdbc.update("DELETE FROM order_contracts WHERE order_id=?", orderId);
    jdbc.update("DELETE FROM notifications WHERE order_id=?", orderId);
    jdbc.update(
        "DELETE FROM outbox_events WHERE aggregate_type='ORDER' AND aggregate_id=?", orderId);
    jdbc.update("DELETE FROM order_histories WHERE order_id=?", orderId);
    jdbc.update("UPDATE orders SET status='REQUESTED',accepted_at=NULL WHERE id=?", orderId);
    mvc.perform(post(path() + "/accept").with(authentication(auth(buyer))).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post(path() + "/accept").with(authentication(auth(seller))).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AWAITING_CONTRACT"));
    mvc.perform(post(path() + "/accept").with(authentication(auth(seller))).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path() + "/draft")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"specialTerms\":\"추가 특약\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
    var ready = documents.generate(seller, orderId, 1);
    String body =
        "{\"version\":1,\"contentHash\":\""
            + ready.contentHash()
            + "\",\"fileHash\":\""
            + ready.fileHash()
            + "\",\"agreed\":true}";
    mvc.perform(
            post(path() + "/sign")
                .with(authentication(auth(other)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(path() + "/sign")
                .with(authentication(auth(buyer)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("true", "false")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path() + "/sign")
                .with(authentication(auth(buyer)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"));
    assertThat(orders.findById(orderId).orElseThrow().getStatus().name())
        .isEqualTo("AWAITING_CONTRACT");
    mvc.perform(
            post(path() + "/sign")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SIGNED"));
    mvc.perform(
            post(path() + "/sign")
                .with(authentication(auth(seller)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    assertThat(orders.findById(orderId).orElseThrow().getStatus().name())
        .isEqualTo("AWAITING_PAYMENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, orderId))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='ORDER' AND"
                    + " aggregate_id=?",
                Long.class,
                orderId))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id=?", Long.class, orderId))
        .isZero();
  }

  @Test
  void failedEventInsertRollsBackContractAndOrderTransition() throws Exception {
    jdbc.update("UPDATE orders SET status='AWAITING_CONTRACT' WHERE id=?", orderId);
    var publisher =
        org.springframework.test.util.AopTestUtils
            .<com.example.lancerpick.notification.service.OrderEventPublisher>
                getUltimateTargetObject(events);
    doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(), any());
    assertThatThrownBy(() -> drafts.prepareHttp(seller, orderId, 1, "new terms"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(documents.get(buyer, orderId).version()).isEqualTo(1);
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("DRAFT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_histories WHERE order_id=?", Long.class, orderId))
        .isEqualTo(1);
    doCallRealMethod().when(publisher).publish(any(), any());
    var ready = documents.generate(seller, orderId, 1);
    drafts.signHttp(buyer, orderId, 1, ready.contentHash(), ready.fileHash());
    doThrow(new IllegalStateException("outbox unavailable")).when(publisher).publish(any(), any());
    assertThatThrownBy(
            () -> drafts.signHttp(seller, orderId, 1, ready.contentHash(), ready.fileHash()))
        .isInstanceOf(IllegalStateException.class);
    var after = documents.get(buyer, orderId);
    assertThat(after.status()).isEqualTo("READY");
    assertThat(after.signatures()).hasSize(1);
    assertThat(orders.findById(orderId).orElseThrow().getStatus().name())
        .isEqualTo("AWAITING_CONTRACT");
    assertThat(after.fileHash()).isEqualTo(ready.fileHash());
  }

  @Test
  void concurrentV2SignaturesAndReplayCreateOneContractCompletion() throws Exception {
    jdbc.update("UPDATE orders SET status='AWAITING_CONTRACT' WHERE id=?", orderId);
    var ready = documents.generate(seller, orderId, 1);
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var jobs = new ArrayList<Future<?>>();
      for (long actor : new long[] {buyer, seller, buyer})
        jobs.add(
            pool.submit(() -> {
              gate.await();
              return drafts.signHttp(actor, orderId, 1, ready.contentHash(), ready.fileHash());
            }));
      gate.countDown();
      for (var job : jobs) job.get(20, TimeUnit.SECONDS);
    }
    assertThat(documents.get(buyer, orderId).status()).isEqualTo("SIGNED");
    assertThat(documents.get(buyer, orderId).signatures()).hasSize(2);
    assertThat(orders.findById(orderId).orElseThrow().getStatus().name())
        .isEqualTo("AWAITING_PAYMENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_histories WHERE order_id=? AND"
                    + " action='CONTRACT_SIGNED'",
                Long.class,
                orderId))
        .isEqualTo(1);
  }

  @Test
  void lateDuplicateOfReplacedPublishedVersionCannotScheduleReferencedFileDeletion()
      throws Exception {
    var job = transactions.claim(seller, orderId, 1).job();
    byte[] bytes = renderer.render(job.contractId(), job.version(), job.canonical());
    String key = storage.store(job.key(), bytes);
    var first = transactions.complete(job, key, bytes.length, PrivateContractStorage.hash(bytes));
    assertThat(first.stored()).isTrue();
    drafts.prepare(seller, orderId, 1, "새 버전");
    var duplicate =
        transactions.complete(job, key, bytes.length, PrivateContractStorage.hash(bytes));
    assertThat(duplicate.stored()).isFalse();
    assertThat(duplicate.discardObsoleteFile()).isFalse();
    assertThat(documents.download(buyer, orderId, 1)).isEqualTo(bytes);
  }

  private static void expectError(Runnable task, String code) {
    assertThatThrownBy(task::run)
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
  }
}
