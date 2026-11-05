package com.example.lancerpick.auth.dto;

/**
 * POST /api/auth/password-reset-requests의 202 본문. 가입 여부·요청 제한·발송 결과와 상관없이 항상 같은 값이다.
 * "메일을 보냈다"가 아니라 "안내 가능한 경우 보낸다"고 말한다(3인 설계 7.2: 202를 전달 성공으로 표현하지 않음).
 */
public record PasswordResetAcceptedResponse(String message) {
    public static final PasswordResetAcceptedResponse INSTANCE = new PasswordResetAcceptedResponse(
            "입력한 주소로 안내할 수 있는 경우 비밀번호 재설정 메일을 보냅니다.");
}
