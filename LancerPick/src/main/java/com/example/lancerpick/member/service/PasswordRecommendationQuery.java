package com.example.lancerpick.member.service;

import com.example.lancerpick.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal notification contract. The caller owns authorization and notification deduplication. */
@Service
@Profile("!demo")
@Transactional(readOnly = true)
public class PasswordRecommendationQuery {
    public record Basis(long memberId, LocalDateTime passwordChangedAt,
            LocalDateTime basisAt, LocalDateTime firstRecommendedAt) {}

    private final MemberRepository members;

    public PasswordRecommendationQuery(MemberRepository members) {
        this.members = members;
    }

    public Optional<Basis> find(long memberId) {
        if (memberId < 1) throw new IllegalArgumentException("Invalid member ID");
        return members.findPasswordTimingById(memberId).map(row -> new Basis(memberId,
                row.getPasswordChangedAt(), row.getPasswordRecommendationBaseAt(),
                MemberChangePolicy.sixMonthsAfter(row.getPasswordRecommendationBaseAt())));
    }
}
