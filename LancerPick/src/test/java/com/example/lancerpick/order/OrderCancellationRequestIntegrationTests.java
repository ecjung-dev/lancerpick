package com.example.lancerpick.order;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.*;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.order.service.*;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.wallet.repository.WalletEntryRepository;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 적립금 결제·환불·확정과 요청 API를 함께 검사하며 로그인만 테스트 principal을 사용한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(OrderCancellationRequestIntegrationTests.FixedClock.class)
class OrderCancellationRequestIntegrationTests {
    private static final LocalDateTime AT = LocalDateTime.parse("2026-11-03T06:00:00.123456");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired OrderCreationService creation;
    @Autowired OrderDecisionService decisions;
    @Autowired OrderPaymentService payments;
    @Autowired OrderDeliveryService deliveries;
    @Autowired OrderRevisionService revisions;
    @Autowired OrderConfirmationService confirmations;
    @Autowired OrderCancellationService direct;
    @Autowired OrderCancellationRequestService requests;
    @Autowired WalletInitializationService initialization;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CancellationRequestQueries query;
    @MockitoSpyBean OrderHistoryRepository histories;
    @MockitoSpyBean OrderDetailQueries details;
    @MockitoSpyBean WalletEntryRepository entries;
    long buyer, seller, third, category, product, order;
    String key;
    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixtures() {
        buyer = member(); seller = member(); third = member();
        new TransactionTemplate(transactions).executeWithoutResult(status -> initialization.initializeForNewMember(buyer));
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "cr-" + UUID.randomUUID());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at) VALUES (?,?,'title','description','scope','prepare',100000,3,?,?)
                """, seller, category, AT, AT);
        order = newOrder();
        decisions.accept(seller, Long.toString(order));
        payments.pay(buyer, Long.toString(order), true);
        key = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanup() {
        reset(query, histories, details, entries);
        for (long member : List.of(buyer, seller, third)) jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", member);
        for (String table : List.of("settlements", "cancellation_requests", "revision_requests", "deliveries", "order_histories", "order_snapshots", "payments"))
            jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM orders WHERE product_id=?)", product);
        jdbc.update("DELETE FROM orders WHERE product_id=?", product);
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        for (long member : List.of(buyer, seller, third)) {
            jdbc.update("DELETE FROM wallets WHERE member_id=?", member);
            jdbc.update("DELETE FROM members WHERE id=?", member);
        }
    }

    @Test
    void createNormalizesReasonAndKeyAndReplaysWithoutWriting() throws Exception {
        String body = createBody(key.toUpperCase(Locale.ROOT), "　취소\r\n부탁합니다 ");
        var response = create(buyer, body).andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cancellationRequest.id").isString())
                .andExpect(jsonPath("$.cancellationRequest.requestKey").value(key))
                .andExpect(jsonPath("$.cancellationRequest.reason").value("취소\n부탁합니다"))
                .andExpect(jsonPath("$.cancellationRequest.status").value("PENDING"))
                .andExpect(jsonPath("$.cancellationRequest.requestedAt").value("2026-11-03T06:00:00.123456Z"))
                .andExpect(jsonPath("$.cancellationRequest.resolvedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.cancellationRequest.requestedBy").doesNotExist())
                .andExpect(jsonPath("$.order.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.order.allowedActions").value(org.hamcrest.Matchers.contains("WITHDRAW_CANCELLATION")))
                .andReturn();
        String id = requestId(response);
        create(buyer, createBody(key, "취소\n부탁합니다")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.id").value(id));
        create(buyer, createBody(key, "different")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        create(buyer, createBody(UUID.randomUUID().toString(), "another")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_PENDING"));
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(requested_at,'%Y-%m-%dT%H:%i:%s.%f') FROM cancellation_requests WHERE id=?", String.class, id))
                .isEqualTo("2026-11-03T06:00:00.123456");
        assertThat(count("cancellation_requests")).isEqualTo(1);
        assertThat(actionCount("CANCELLATION_REQUESTED")).isEqualTo(1);
        assertThat(balance()).isEqualTo(900000);
    }

    @Test
    void approvalRefundsOnceAndResolvedKeyReturnsCurrentCancelledOrder() throws Exception {
        String id = submit();
        resolve(seller, id, "approve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.status").value("APPROVED"))
                .andExpect(jsonPath("$.cancellationRequest.resolvedBy").value(Long.toString(seller)))
                .andExpect(jsonPath("$.cancellationRequest.responseReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.order.payment.status").value("REFUNDED"));
        String resolvedAt = jdbc.queryForObject("SELECT DATE_FORMAT(resolved_at,'%Y-%m-%dT%H:%i:%s.%f') FROM cancellation_requests WHERE id=?", String.class, id);
        resolve(seller, id, "approve", null).andExpect(status().isOk());
        create(buyer, createBody(key, "reason")).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"));
        create(buyer, createBody(UUID.randomUUID().toString(), "new")).andExpect(status().isConflict());
        resolve(buyer, id, "withdraw", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_ALREADY_RESOLVED"));
        assertThat(balance()).isEqualTo(1000000);
        assertThat(refunds()).isEqualTo(1);
        assertThat(actionCount("CANCELLATION_APPROVED")).isEqualTo(1);
        assertThat(actionCount("CANCELLED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(resolved_at,'%Y-%m-%dT%H:%i:%s.%f') FROM cancellation_requests WHERE id=?", String.class, id)).isEqualTo(resolvedAt);
    }

    @Test
    void rejectAndWithdrawPreserveOrderAllowNewKeyAndDoNotRefund() throws Exception {
        String id = submit();
        resolve(seller, id, "reject", "{\"responseReason\":\"　진행 중입니다 \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.responseReason").value("진행 중입니다"))
                .andExpect(jsonPath("$.order.status").value("IN_PROGRESS"));
        resolve(seller, id, "reject", "{\"responseReason\":\"different\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.responseReason").value("진행 중입니다"));
        resolve(seller, id, "approve", null).andExpect(status().isConflict());
        String next = requestId(create(buyer, createBody(UUID.randomUUID().toString(), "new reason")).andExpect(status().isCreated()).andReturn());
        resolve(buyer, next, "withdraw", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.status").value("WITHDRAWN"));
        resolve(buyer, next, "withdraw", null).andExpect(status().isOk());
        create(buyer, createBody(key, "reason")).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationRequest.status").value("REJECTED"));
        assertThat(refunds()).isZero(); assertThat(balance()).isEqualTo(900000);
        assertThat(actionCount("CANCELLATION_REJECTED")).isEqualTo(1);
        assertThat(actionCount("CANCELLATION_WITHDRAWN")).isEqualTo(1);
    }

    @Test
    void rejectionReasonIsOptional() throws Exception {
        resolve(seller, submit(), "reject", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequest.responseReason").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unpaidAndCompletedNewRequestsAreRejectedButOldKeyCanBeReadAfterCompletion() throws Exception {
        long unpaid = newOrder();
        mvc.perform(post("/api/orders/" + unpaid + "/cancellation-requests").with(csrf()).with(authentication(auth(buyer)))
                .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID().toString(), "reason")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        delivered(); String id = submit();
        resolve(seller, id, "reject", null).andExpect(status().isOk());
        confirmations.confirm(buyer, Long.toString(order));
        create(buyer, createBody(key, "reason")).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("COMPLETED"));
        create(buyer, createBody(UUID.randomUUID().toString(), "reason")).andExpect(status().isConflict());
        assertThat(refunds()).isZero();
    }

    @Test
    void pendingRequestBlocksDeliveryRevisionAndConfirmation() throws Exception {
        String id = submit();
        assertCode(() -> deliveries.submit(seller, Long.toString(order), new CreateDeliveryRequest("done", "https://example.com", 1)), "CANCELLATION_PENDING");
        resolve(buyer, id, "withdraw", null).andExpect(status().isOk());
        delivered();
        create(buyer, createBody(UUID.randomUUID().toString(), "next")).andExpect(status().isCreated());
        long delivery = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=?", Long.class, order);
        assertCode(() -> revisions.request(buyer, Long.toString(order), new CreateRevisionRequest(Long.toString(delivery), "revise")), "CANCELLATION_PENDING");
        assertCode(() -> confirmations.confirm(buyer, Long.toString(order)), "CANCELLATION_PENDING");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void confirmationAndRevisionStatesAlsoSupportRequestApproval(boolean revision) throws Exception {
        delivered();
        if(revision){
            long delivery=jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id=?",Long.class,order);
            revisions.request(buyer,Long.toString(order),new CreateRevisionRequest(Long.toString(delivery),"revise"));
        }
        resolve(seller,submit(),"approve",null).andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"));
        assertThat(balance()).isEqualTo(1000000);assertThat(refunds()).isEqualTo(1);
    }

    @Test
    void rolesRequestOwnershipAndCsrfAreEnforced() throws Exception {
        create(seller, createBody(key, "reason")).andExpect(status().isForbidden());
        create(third, createBody(key, "reason")).andExpect(status().isNotFound());
        String id = submit();
        resolve(buyer, id, "approve", null).andExpect(status().isForbidden());
        resolve(buyer, id, "reject", null).andExpect(status().isForbidden());
        resolve(seller, id, "withdraw", null).andExpect(status().isForbidden());
        resolve(third, id, "approve", null).andExpect(status().isNotFound());
        long another = newOrder();
        mvc.perform(post("/api/orders/" + another + "/cancellation-requests/" + id + "/approve").with(csrf()).with(authentication(auth(seller))))
                .andExpect(status().isNotFound());
        for (String suffix : List.of("", "/"+id+"/approve", "/"+id+"/reject", "/"+id+"/withdraw")) {
            var body = suffix.isEmpty() ? createBody(key, "reason") : "{}";
            mvc.perform(post(path()+suffix).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(post(path()+suffix).with(authentication(auth(buyer))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"{}", "{\"reason\":1,\"requestKey\":\"bad\"}", "{\"reason\":\"x\",\"requestKey\":\"1-1-1-1-1\"}", "{\"reason\":\"　\",\"requestKey\":\"00000000-0000-0000-0000-000000000000\"}"})
    void invalidCreateInputsAre400(String json) throws Exception {
        create(buyer, json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(count("cancellation_requests")).isZero();
    }

    @Test
    void reasonLimitsAndOptionalResponseReasonAreValidated() throws Exception {
        create(buyer, createBody(key, "😀".repeat(1001))).andExpect(status().isBadRequest());
        String id = requestId(create(buyer, createBody(key, " " + "😀".repeat(1000) + " ")).andExpect(status().isCreated()).andReturn());
        for (String json : List.of("{\"responseReason\":1}", "{\"responseReason\":\" \"}", mapper.writeValueAsString(Map.of("responseReason", "a".repeat(1001)))))
            resolve(seller, id, "reject", json).andExpect(status().isBadRequest());
        assertPending(id);
    }

    @ParameterizedTest
    @ValueSource(strings={"0", "-1", "bad", "9223372036854775808"})
    void invalidOrderAndRequestIdsAre400(String id) throws Exception {
        mvc.perform(post("/api/orders/"+id+"/cancellation-requests").with(csrf()).with(authentication(auth(buyer)))
                .contentType(MediaType.APPLICATION_JSON).content(createBody(key,"reason"))).andExpect(status().isBadRequest());
        resolve(seller, id, "approve", null).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void concurrentCreateHasOneRecordAndOneHistory(boolean sameKey) throws Exception {
        List<Integer> result = together(
                () -> create(buyer,createBody(key,"reason")).andReturn().getResponse().getStatus(),
                () -> create(buyer,createBody(sameKey?key:UUID.randomUUID().toString(),"reason")).andReturn().getResponse().getStatus());
        assertThat(result).containsExactlyInAnyOrder(201, sameKey?200:409);
        assertThat(count("cancellation_requests")).isEqualTo(1); assertThat(actionCount("CANCELLATION_REQUESTED")).isEqualTo(1);
    }

    @Test
    void approveAndWithdrawCannotBothWin() throws Exception {
        String id=submit();
        assertThat(together(() -> resolve(seller,id,"approve",null).andReturn().getResponse().getStatus(),
                () -> resolve(buyer,id,"withdraw",null).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
        String status=jdbc.queryForObject("SELECT status FROM cancellation_requests WHERE id=?",String.class,id);
        assertThat(refunds()).isEqualTo(status.equals("APPROVED")?1:0);
        assertThat(balance()).isEqualTo(status.equals("APPROVED")?1000000:900000);
    }

    @Test
    void approvalAndDirectCancellationRefundOnlyOnce() throws Exception {
        String id=submit();
        var result=together(() -> resolve(seller,id,"approve",null).andReturn().getResponse().getStatus(),
                () -> {direct.cancel(seller,Long.toString(order),"direct");return 200;});
        assertThat(result).containsOnly(200); assertThat(balance()).isEqualTo(1000000); assertThat(refunds()).isEqualTo(1);
        assertThat(actionCount("CANCELLED")).isEqualTo(1); assertThat(actionCount("CANCELLATION_APPROVED")).isEqualTo(1);
    }

    @Test
    void approvalAndConfirmationCannotRefundAndSettleTogether() throws Exception {
        delivered();String id=submit();
        together(() -> {requests.resolve(seller,Long.toString(order),id,OrderCancellationRequestService.Resolution.APPROVED,null);return 200;},
                () -> {try {confirmations.confirm(buyer,Long.toString(order));return 200;}catch(BusinessException e){assertThat(e.code()).isIn("CANCELLATION_PENDING","ORDER_STATE_CONFLICT");return 409;}});
        assertThat(refunds()).isEqualTo(1); assertThat(count("settlements")).isZero(); assertThat(balance()).isEqualTo(1000000);
    }

    @Test
    void requestHistoryFailureAndProjectionFailureRollBackNewRequestAndAllowSameKeyRetry() throws Exception {
        doAnswer(invocation->{jdbc.update("INSERT INTO order_histories(order_id,actor_id,action,from_status,to_status,created_at) VALUES (?,0,'CANCELLATION_REQUESTED','IN_PROGRESS','IN_PROGRESS',?)",order,AT);return null;}).when(histories).saveAndFlush(any());
        create(buyer,createBody(key,"reason")).andExpect(status().isInternalServerError());
        assertThat(count("cancellation_requests")).isZero();reset(histories);
        doThrow(new IllegalStateException("projection failure")).when(details).forParticipant(anyLong(),anyLong());
        create(buyer,createBody(key,"reason")).andExpect(status().isInternalServerError());
        assertThat(count("cancellation_requests")).isZero();reset(details);
        create(buyer,createBody(key,"reason")).andExpect(status().isCreated());
    }

    @Test
    void refundFailureRestoresPendingRequestPaymentAndBalance() throws Exception {
        String id=submit();
        doAnswer(invocation->{jdbc.update("INSERT INTO wallet_entries(member_id,payment_id,type,amount_delta,balance_after,event_key,created_at) VALUES (0,NULL,'INITIAL_GRANT',1000000,1000000,?,?)",UUID.randomUUID().toString(),AT);return null;}).when(entries).saveAndFlush(any());
        resolve(seller,id,"approve",null).andExpect(status().isInternalServerError());
        assertPending(id);assertThat(refunds()).isZero();assertThat(balance()).isEqualTo(900000);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order)).isEqualTo("PAID");
        reset(entries);resolve(seller,id,"approve",null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings={"reject","withdraw","approve"})
    void resolutionProjectionFailureRollsBackAllSideEffects(String action) throws Exception {
        String id=submit();
        doThrow(new IllegalStateException("projection failure")).when(details).forParticipant(anyLong(),anyLong());
        resolve(action.equals("withdraw")?buyer:seller,id,action,null).andExpect(status().isInternalServerError());
        reset(details);assertPending(id);assertThat(refunds()).isZero();assertThat(balance()).isEqualTo(900000);
        assertThat(actionCount("CANCELLATION_REJECTED")+actionCount("CANCELLATION_WITHDRAWN")+actionCount("CANCELLATION_APPROVED")).isZero();
    }

    private String submit() throws Exception {return requestId(create(buyer,createBody(key,"reason")).andExpect(status().isCreated()).andReturn());}
    private String requestId(MvcResult result)throws Exception{return mapper.readTree(result.getResponse().getContentAsString()).get("cancellationRequest").get("id").asString();}
    private String createBody(String key,String reason){return mapper.writeValueAsString(Map.of("requestKey",key,"reason",reason));}
    private String path(){return "/api/orders/"+order+"/cancellation-requests";}
    private ResultActions create(long member,String json)throws Exception{return mvc.perform(post(path()).with(csrf()).with(authentication(auth(member))).contentType(MediaType.APPLICATION_JSON).content(json));}
    private ResultActions resolve(long member,String id,String action,String json)throws Exception{
        var request=post(path()+"/"+id+"/"+action).with(csrf()).with(authentication(auth(member)));
        if(json!=null)request.contentType(MediaType.APPLICATION_JSON).content(json);return mvc.perform(request);
    }
    private static UsernamePasswordAuthenticationToken auth(long id){return UsernamePasswordAuthenticationToken.authenticated(new Principal(id),null,AuthorityUtils.createAuthorityList("ROLE_USER"));}
    private long balance(){return jdbc.queryForObject("SELECT balance FROM wallets WHERE member_id=?",Long.class,buyer);}
    private long refunds(){return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entries WHERE member_id=? AND type='REFUND'",Long.class,buyer);}
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE order_id=?",Long.class,order);}
    private long actionCount(String action){return jdbc.queryForObject("SELECT COUNT(*) FROM order_histories WHERE order_id=? AND action=?",Long.class,order,action);}
    private void assertPending(String id){assertThat(jdbc.queryForObject("SELECT status FROM cancellation_requests WHERE id=?",String.class,id)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order)).isIn("IN_PROGRESS","AWAITING_CONFIRMATION");}
    private void delivered(){deliveries.submit(seller,Long.toString(order),new CreateDeliveryRequest("done","https://example.com/result",1));}
    private static void assertCode(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo(code));}
    private List<Integer> together(Callable<Integer> first,Callable<Integer> second)throws Exception{
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=List.of(first,second).stream().map(call->pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");return call.call();})).toList();
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }finally{start.countDown();}
    }
    private long newOrder(){return creation.create(buyer,new CreateOrderRequest(Long.toString(product),0L,"requirements",UUID.randomUUID().toString())).orderId();}
    private long member(){return insert("INSERT INTO members(email,password_hash,display_name,created_at,updated_at) VALUES (?,'fixture','member',?,?)",UUID.randomUUID()+"@cancel-request.test",AT,AT);}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return key.getKey().longValue();}
    @TestConfiguration static class FixedClock{@Bean @Primary Clock cancellationRequestClock(){return Clock.fixed(Instant.parse("2026-11-03T06:00:00.123456789Z"),ZoneId.of("America/Los_Angeles"));}}
}
