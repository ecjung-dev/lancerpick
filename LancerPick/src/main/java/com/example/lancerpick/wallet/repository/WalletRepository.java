package com.example.lancerpick.wallet.repository;
import com.example.lancerpick.wallet.entity.Wallet;
import com.example.lancerpick.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface WalletRepository extends JpaRepository<Wallet,Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from Member m where m.id=:memberId")
    Optional<Member> lockInitializationMember(@Param("memberId")long memberId);
    default Optional<Long> lockMemberForInitialization(long memberId){return lockInitializationMember(memberId).map(Member::getId);}
    // Hibernate HQL conflict handling preserves insert-first semantics without a native SQL query.
    @Modifying(flushAutomatically=true)
    @Query("insert into Wallet (memberId,balance,updatedAt) values (:memberId,:balance,:at) on conflict do nothing")
    int insertInitial(@Param("memberId")long memberId,@Param("balance")long balance,@Param("at")LocalDateTime at);
    default int insertInitialWalletIfAbsent(long member,long balance,Instant at){return insertInitial(member,balance,LocalDateTime.ofInstant(at,ZoneOffset.UTC));}
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from Wallet w where w.memberId=:memberId")
    Optional<Wallet> findByMemberIdForUpdate(@Param("memberId")long memberId);
    default int updateTopupBalance(long member,long balance,Instant at){var wallet=findById(member);if(wallet.isEmpty())return 0;wallet.get().applyTopupBalance(balance,LocalDateTime.ofInstant(at,ZoneOffset.UTC));flush();return 1;}
}
