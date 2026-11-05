package com.example.lancerpick.auth.entity;

/**
 * account_challenges.purpose (V5 CHECK ck_challenge_purpose). 목적을 엄격히 나눠, 가입 인증 증명을
 * 비밀번호 재설정 토큰으로 쓰지 못하게 한다(3인 설계 7.1).
 * 이번 작업은 PASSWORD_RESET만 만든다. 나머지 두 값은 이메일 인증 가입·기존 회원 전환 작업에서 쓴다.
 */
public enum ChallengePurpose {
    SIGNUP_EMAIL,
    EXISTING_EMAIL,
    PASSWORD_RESET
}
