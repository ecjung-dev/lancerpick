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

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** DB 없이 MemberService의 DB 오류 분류 규칙만 확인하는 단위 테스트. */
class MemberServiceTests {
    private MemberRepository repository;
    private WalletInitializationService walletInitialization;
    private MemberService service;

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        walletInitialization = mock(WalletInitializationService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("{bcrypt}test-only");
        when(repository.existsByEmail(anyString())).thenReturn(false);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
        service = new MemberService(repository, encoder, clock, walletInitialization);
    }

    @Test
    void emailUniqueConstraintViolationBecomes409() {
        when(repository.saveAndFlush(any())).thenThrow(violation(
                "Duplicate entry 'a@example.com' for key 'members.uk_members_email'"));

        assertThatThrownBy(() -> service.signup(new SignupRequest("a@example.com", "abcd1234", "중복")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        verify(walletInitialization, never()).initializeForNewMember(anyLong());
    }

    @Test
    void otherConstraintViolationIsNotReportedAsDuplicateEmail() {
        DataIntegrityViolationException original = violation("Check constraint 'ck_members_name' is violated.");
        when(repository.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> service.signup(new SignupRequest("b@example.com", "abcd1234", "다른오류")))
                .isSameAs(original);
        verify(walletInitialization, never()).initializeForNewMember(anyLong());
    }

    @Test
    void violationWithoutConstraintNameIsNotReportedAsDuplicateEmail() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("unknown");
        when(repository.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> service.signup(new SignupRequest("c@example.com", "abcd1234", "이름없음")))
                .isSameAs(original);
    }

    @Test
    void existingEmailIsRejectedBeforeSaving() {
        when(repository.existsByEmail("d@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(new SignupRequest(" D@Example.com ", "abcd1234", "사전확인")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("EMAIL_ALREADY_EXISTS"));
        verify(repository, never()).saveAndFlush(any());
    }

    private static DataIntegrityViolationException violation(String sqlMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(sqlMessage));
    }
}
