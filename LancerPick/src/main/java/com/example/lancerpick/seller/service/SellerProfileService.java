package com.example.lancerpick.seller.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import com.example.lancerpick.seller.dto.SellerProfileResponse;
import com.example.lancerpick.seller.entity.SellerProfile;
import com.example.lancerpick.seller.repository.SellerProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@Transactional(readOnly = true)
public class SellerProfileService {

    private final SellerProfileRepository profiles;
    private final MemberRepository members;
    private final EntityManager entityManager;
    private final Clock clock;

    public SellerProfileService(
            SellerProfileRepository profiles,
            MemberRepository members,
            EntityManager entityManager,
            Clock clock) {

        this.profiles = profiles;
        this.members = members;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public SellerProfileResponse getMine(long memberId) {
        return findProfile(memberId);
    }

    public SellerProfileResponse getPublic(String memberId) {
        return findProfile(parseMemberId(memberId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SaveResult save(long memberId, SellerProfileRequest request) {
        var input = SellerProfileInput.from(request);

        // 같은 회원의 동시 등록·수정을 순서대로 처리한다.
        Member member = entityManager.find(
                Member.class,
                memberId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (member == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        LocalDateTime now = LocalDateTime
                .ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        var existing = profiles.findById(memberId);
        boolean created = existing.isEmpty();

        SellerProfile profile;

        if (created) {
            profile = SellerProfile.create(
                    memberId,
                    input.introduction(),
                    input.specialty(),
                    input.portfolioUrl(),
                    now
            );

            // 회원 번호를 PK로 쓰는 신규 Entity를 명시적으로 등록한다.
            entityManager.persist(profile);
        } else {
            profile = existing.orElseThrow();

            profile.updateProfile(
                    input.introduction(),
                    input.specialty(),
                    input.portfolioUrl(),
                    now
            );
        }

        entityManager.flush();

        return new SaveResult(
                toResponse(profile, member),
                created
        );
    }

    private SellerProfileResponse findProfile(long memberId) {
        var profile = profiles.findById(memberId)
                .orElseThrow(SellerProfileService::notFound);

        var member = members.findById(memberId)
                .orElseThrow(SellerProfileService::notFound);

        return toResponse(profile, member);
    }

    private static SellerProfileResponse toResponse(
            SellerProfile profile, Member member) {

        return new SellerProfileResponse(
                profile.getMemberId().toString(),
                member.getDisplayName(),
                profile.getIntroduction(),
                profile.getSpecialty(),
                profile.getPortfolioUrl()
        );
    }

    private static long parseMemberId(String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) {
            throw invalidId();
        }

        try {
            long id = Long.parseLong(value);

            if (id <= 0) {
                throw invalidId();
            }

            return id;
        } catch (NumberFormatException exception) {
            throw invalidId();
        }
    }

    private static BusinessException invalidId() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "판매자 번호는 양의 정수 범위여야 합니다."
        );
    }

    private static BusinessException notFound() {
        return new BusinessException(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "판매자 프로필을 찾을 수 없습니다."
        );
    }

    public record SaveResult(
            SellerProfileResponse profile,
            boolean created
    ) {
    }
}