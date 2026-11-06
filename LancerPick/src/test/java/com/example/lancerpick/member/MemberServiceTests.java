package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** DB 없이 MemberService의 DB 오류 분류 규칙만 확인하는 단위 테스트. */
class MemberServiceTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 0, 0);

    private MemberRepository repository;
    private WalletInitializationService walletInitialization;
    private AccountChallengeRepository challenges;
    private MemberService service;

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        walletInitialization = mock(WalletInitializationService.class);
        challenges = mock(AccountChallengeRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("{bcrypt}test-only");
        when(repository.existsByEmail(anyString())).thenReturn(false);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
        service = new MemberService(repository, encoder, clock, walletInitialization, challenges);
    }

    /** DB 없이 쓰는 가입 요청: 이 이메일에 묶인 인증된 가입 증명을 저장소 대역이 돌려준다. */
    private SignupRequest request(String rawEmail, String normalizedEmail, String displayName) {
        OneTimeTokens.Issued proof = OneTimeTokens.issue();
        AccountChallenge challenge = AccountChallenge.signupEmail(normalizedEmail, new byte[32], NOW.minusMinutes(5),
                NOW.plusMinutes(10));
        challenge.verifyForSignup(proof.hash(), NOW.minusMinutes(1), NOW.plusMinutes(29));
        when(challenges.findByTokenHashForUpdate(any())).thenReturn(Optional.of(challenge));
        return new SignupRequest(rawEmail, "abcd1234", displayName, proof.value());
    }

    @Test
    void emailUniqueConstraintViolationBecomes409() {
        when(repository.saveAndFlush(any())).thenThrow(violation(
                "Duplicate entry 'a@example.com' for key 'members.uk_members_email'"));

        assertThatThrownBy(() -> service.signup(request("a@example.com", "a@example.com", "중복")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        verify(walletInitialization, never()).initializeForNewMember(anyLong());
    }

    @Test
    void otherConstraintViolationIsNotReportedAsDuplicateEmail() {
        DataIntegrityViolationException original = violation("Check constraint 'ck_members_name' is violated.");
        when(repository.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> service.signup(request("b@example.com", "b@example.com", "다른오류")))
                .isSameAs(original);
        verify(walletInitialization, never()).initializeForNewMember(anyLong());
    }

    @Test
    void violationWithoutConstraintNameIsNotReportedAsDuplicateEmail() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("unknown");
        when(repository.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> service.signup(request("c@example.com", "c@example.com", "이름없음")))
                .isSameAs(original);
    }

    @Test
    void existingEmailIsRejectedBeforeSaving() {
        when(repository.existsByEmail("d@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(request(" D@Example.com ", "d@example.com", "사전확인")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingOrForeignProofIsRejectedBeforeAnyMemberWork() {
        // 형식이 틀린 증명
        assertThatThrownBy(() -> service.signup(new SignupRequest("e@example.com", "abcd1234", "증명", "not-a-proof")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_VERIFICATION_INVALID"));
        // 다른 이메일에 묶인 증명
        SignupRequest other = request("f@example.com", "someone-else@example.com", "증명");
        assertThatThrownBy(() -> service.signup(other))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_VERIFICATION_INVALID"));
        verify(repository, never()).existsByEmail(anyString());
        verify(repository, never()).saveAndFlush(any());
        verify(walletInitialization, never()).initializeForNewMember(anyLong());
    }

    private static DataIntegrityViolationException violation(String sqlMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(sqlMessage));
    }
}
