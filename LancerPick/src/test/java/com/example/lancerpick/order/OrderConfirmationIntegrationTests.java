package com.example.lancerpick.order;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.lancerpick.order.service.OrderConfirmationService;
import com.example.lancerpick.settlement.SettlementQueryService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class OrderConfirmationIntegrationTests extends OrderCancellationIntegrationTests {
    @Test void querydslTotalsAndTieOrderingUseOnlyOwnedSettlements() {
        jdbc.update("UPDATE wallets SET balance=200000 WHERE member_id=?",buyer);
        long other=newOrder();
        for(long id:List.of(order,other)) {
            payments.pay(buyer,""+id,true);
            jdbc.update("UPDATE orders SET status='AWAITING_CONFIRMATION' WHERE id=?",id);
            confirmations.confirm(buyer,""+id);
        }
        assertThat(settlements.summary(seller).grossAmount()).isEqualTo(100000);
        assertThat(settlements.summary(seller).feeAmount()).isEqualTo(10000);
        assertThat(settlements.summary(seller).sellerAmount()).isEqualTo(90000);
        assertThat(settlements.list(seller,0,1).items().getFirst().orderId()).isEqualTo(Long.toString(other));
        assertThat(settlements.list(seller,1,1).items().getFirst().orderId()).isEqualTo(Long.toString(order));
        assertThat(settlements.summary(third).completedCount()).isZero();
        assertThat(settlements.summary(third).sellerAmount()).isZero();
        assertThat(settlements.list(third,0,20).items()).isEmpty();
    }
    @Autowired OrderConfirmationService confirmations;
    @Autowired SettlementQueryService settlements;
    @AfterEach void removeSettlements(){for(long id:ids)jdbc.update("DELETE FROM settlements WHERE order_id=?",id);}
    void awaiting(){payments.pay(buyer,""+order,true);jdbc.update("UPDATE orders SET status='AWAITING_CONFIRMATION' WHERE id=?",order);}
    @Test void createsOneSettlementWithFloorFeeAndNeverCreditsPurchaseWallet(){
        jdbc.update("UPDATE orders SET price_amount=999 WHERE id=?",order);awaiting();
        confirmations.confirm(buyer,""+order);confirmations.confirm(buyer,""+order);
        assertThat(settlements.summary(seller).sellerAmount()).isEqualTo(900);
        assertThat(settlements.summary(seller).feeAmount()).isEqualTo(99);
        assertThat(settlements.summary(seller).completedCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(69001);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE member_id=?",Long.class,seller)).isZero();
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') FROM settlements WHERE order_id=?",String.class,order)).isEqualTo("2026-11-03 01:00:00.123456");
    }
    @Test void onlySellerSeesHisRevenueAndPaginationIsValidated()throws Exception{
        awaiting();confirmations.confirm(buyer,""+order);
        assertThat(settlements.list(seller,0,20).totalElements()).isEqualTo(1);
        assertThat(settlements.list(buyer,0,20).items()).isEmpty();
        assertThat(settlements.list(seller,10,20).items()).isEmpty();
        assertThatThrownBy(()->settlements.list(seller,0,101)).isInstanceOf(com.example.lancerpick.common.exception.BusinessException.class);
        mvc.perform(get("/api/me/settlements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/settlements/summary").with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Principal(seller),null,List.of())))).andExpect(status().isOk()).andExpect(jsonPath("$.completedCount").value(1)).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void pendingCancellationAndRevisionPreventConfirmation(){
        awaiting();jdbc.update("INSERT INTO cancellation_requests(order_id,requested_by,request_key,reason,requested_at) VALUES (?,?,?,'reason',?)",order,buyer,UUID.randomUUID().toString(),AT);
        assertThatThrownBy(()->confirmations.confirm(buyer,""+order)).isInstanceOfSatisfying(com.example.lancerpick.common.exception.BusinessException.class,e->assertThat(e.code()).isEqualTo("CANCELLATION_PENDING"));
        jdbc.update("DELETE FROM cancellation_requests WHERE order_id=?",order);
        jdbc.update("UPDATE orders SET status='REVISION_IN_PROGRESS' WHERE id=?",order);
        assertThatThrownBy(()->confirmations.confirm(buyer,""+order)).isInstanceOf(com.example.lancerpick.common.exception.BusinessException.class);
    }
    @Test void sellerAndThirdPartyCannotConfirm()throws Exception{
        awaiting();
        mvc.perform(post("/api/orders/"+order+"/confirm").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Principal(seller),null,List.of())))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/"+order+"/confirm").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Principal(third),null,List.of())))).andExpect(status().isNotFound());
    }
    @Test void historyFailureRollsBackCompletionAndSettlement(){
        awaiting();doAnswer(i->{jdbc.update("INSERT INTO order_histories(order_id,actor_id,action,from_status,to_status,created_at) VALUES (?,-1,'COMPLETED','AWAITING_CONFIRMATION','COMPLETED',?)",order,AT);return null;}).when(histories).saveAndFlush(any());
        assertThatThrownBy(()->confirmations.confirm(buyer,""+order)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order)).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(settlements.summary(seller).completedCount()).isZero();
    }
    @Test void confirmationAndCancellationCannotBothSucceed()throws Exception{
        awaiting();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();try{confirmations.confirm(buyer,""+order);return "CONFIRMED";}catch(com.example.lancerpick.common.exception.BusinessException e){return e.code();}});
            var b=pool.submit(()->{start.await();try{cancellations.cancel(seller,""+order,"reason");return "CANCELLED";}catch(com.example.lancerpick.common.exception.BusinessException e){return e.code();}});
            start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).contains("ORDER_STATE_CONFLICT");
        }
        String status=jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order);
        assertThat(status).isIn("COMPLETED","CANCELLED");
        assertThat(settlements.summary(seller).completedCount()).isEqualTo(status.equals("COMPLETED")?1:0);
        assertThat(balance()).isEqualTo(status.equals("COMPLETED")?20000:70000);
    }
    @Test void concurrentConfirmationCreatesOneSettlement()throws Exception{
        awaiting();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)){
            var jobs=new ArrayList<Future<?>>();for(int i=0;i<3;i++)jobs.add(pool.submit(()->{start.await();return confirmations.confirm(buyer,""+order);}));
            start.countDown();for(var job:jobs)job.get(30,TimeUnit.SECONDS);
        }
        assertThat(settlements.summary(seller).completedCount()).isEqualTo(1);
    }
}
