package com.example.lancerpick.wallet.repository;
import com.example.lancerpick.wallet.entity.QWalletEntry;
import com.example.lancerpick.payment.entity.QPayment;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
public class WalletEntryHistoryImpl implements WalletEntryHistory {
    private final JPAQueryFactory query;
    public WalletEntryHistoryImpl(EntityManager em){query=new JPAQueryFactory(em);}
    public List<WalletEntryView> findHistory(long member,int size,long offset){var e=QWalletEntry.walletEntry;var p=QPayment.payment;return query.select(e,p.orderId).from(e).leftJoin(p).on(p.id.eq(e.paymentId)).where(e.memberId.eq(member)).orderBy(e.createdAt.desc(),e.id.desc()).offset(offset).limit(size).fetch().stream().<WalletEntryView>map(t->{var row=t.get(e);return new History(row.getId(),row.getType().name(),row.getAmountDelta(),row.getBalanceAfter(),t.get(p.orderId),row.getCreatedAt());}).toList();}
    private record History(Long id,String type,long amountDelta,long balanceAfter,Long orderId,LocalDateTime at) implements WalletEntryView{
        public Long getId(){return id;}public String getType(){return type;}public long getAmountDelta(){return amountDelta;}public long getBalanceAfter(){return balanceAfter;}public Long getOrderId(){return orderId;}public Instant getCreatedAt(){return at.toInstant(ZoneOffset.UTC);}
    }
}
