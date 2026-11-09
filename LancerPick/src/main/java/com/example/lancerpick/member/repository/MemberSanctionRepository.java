package com.example.lancerpick.member.repository;

import com.example.lancerpick.member.entity.MemberSanction;
import com.example.lancerpick.member.entity.SanctionKind;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberSanctionRepository extends JpaRepository<MemberSanction, Long> {
    interface Snapshot {
        Long getId();

        SanctionKind getKind();

        long getAppliedBy();

        String getReason();

        LocalDateTime getStartsAt();

        LocalDateTime getEndsAt();
    }

    @Query(
            "select s.id as id,s.kind as kind,s.appliedBy as appliedBy,s.reason as"
                + " reason,s.startsAt as startsAt,s.endsAt as endsAt from MemberSanction s where"
                + " s.memberId=:member and s.kind=:kind and s.revokedAt is null and"
                + " s.startsAt<=:now and s.endsAt>:now order by s.id desc")
    List<Snapshot> current(
            @Param("member") long member,
            @Param("kind") SanctionKind kind,
            @Param("now") LocalDateTime now,
            Pageable page);

    @Query(
            "select s.id as id,s.kind as kind,s.appliedBy as appliedBy,s.reason as"
                + " reason,s.startsAt as startsAt,s.endsAt as endsAt from MemberSanction s where"
                + " s.memberId=:member and"
                + " s.kind=com.example.lancerpick.member.entity.SanctionKind.SECURITY_LOCK order by"
                + " s.id desc")
    List<Snapshot> latestSecurityLock(@Param("member") long member, Pageable page);

    Optional<MemberSanction> findByMemberIdAndRequestKey(long memberId, String requestKey);

    @Query(
            "select s from MemberSanction s where s.memberId=:member and s.kind=:kind and"
                + " s.revokedAt is null and s.endsAt>:now")
    List<MemberSanction> activeForMutation(
            @Param("member") long member,
            @Param("kind") SanctionKind kind,
            @Param("now") LocalDateTime now);

    @Query(
            "select s.id from MemberSanction s where"
                    + " s.kind=com.example.lancerpick.member.entity.SanctionKind.SECURITY_LOCK and"
                    + " s.timerStopDeliveredAt is null order by s.id")
    List<Long> pendingSecurityLocks(Pageable page);
}
