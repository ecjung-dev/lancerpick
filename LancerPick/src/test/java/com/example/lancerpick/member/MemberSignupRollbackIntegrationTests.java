package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.example.lancerpick.member.dto.SignupRequest;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import com.example.lancerpick.wallet.service.WalletInitializationService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 지갑 초기화가 실패하면 같은 트랜잭션의 회원 INSERT와 가입 증명 사용 처리까지 롤백되는지 실제 MySQL로 확인한다.
 * 증명 사용이 롤백되므로 같은 증명으로 다시 가입을 시도할 수 있다.
 */
@IntegrationTest
class MemberSignupRollbackIntegrationTests {
    private static final String EMAIL_PREFIX = "signup-rollback-it-";

    @Autowired MemberService memberService;
    @Autowired JdbcTemplate jdbc;

    // 실패를 재현하기 위해 지갑 초기화만 대체한다. 실제 지갑 처리는 MemberSignupIntegrationTests에서 확인한다.
    @MockitoBean WalletInitializationService walletInitializationService;

    @AfterEach
    void removeOnlyThisTestsMembers() {
        jdbc.update("DELETE FROM members WHERE email LIKE ?", EMAIL_PREFIX + "%");
        TestMembers.deleteSignupProofs(jdbc, EMAIL_PREFIX + "%");
    }

    @Test
    void walletInitializationFailureRollsBackNewMemberAndProofConsumption() {
        String email = EMAIL_PREFIX + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        doThrow(new IllegalStateException("simulated wallet failure"))
                .when(walletInitializationService).initializeForNewMember(anyLong());
        SignupRequest request = TestMembers.signupRequest(jdbc, email, "abcd1234", "롤백");

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated wallet failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE email = ?", Long.class, email)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT consumed_at IS NULL FROM account_challenges WHERE purpose = 'SIGNUP_EMAIL' AND email = ?",
                Boolean.class, email)).isTrue();
    }
}
