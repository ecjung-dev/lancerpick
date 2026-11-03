package com.example.lancerpick.wallet.repository;
import com.example.lancerpick.wallet.entity.WalletEntry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface WalletEntryRepository extends JpaRepository<WalletEntry,Long>,WalletEntryHistory {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from WalletEntry e where e.eventKey=:eventKey")
    Optional<WalletEntry> findByEventKeyForUpdate(@Param("eventKey")String eventKey);
    long countByMemberId(long memberId);
    boolean existsByTopupId(long topupId);
    boolean existsByEventKey(String eventKey);
    default int insertTopup(long member,long topup,long amount,long balance,String key,Instant at){saveAndFlush(WalletEntry.topup(member,topup,amount,balance,key,LocalDateTime.ofInstant(at,ZoneOffset.UTC)));return 1;}
}
