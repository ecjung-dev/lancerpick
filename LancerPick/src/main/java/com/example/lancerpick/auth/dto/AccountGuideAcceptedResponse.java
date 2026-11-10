package com.example.lancerpick.auth.dto;

/**
 * POST /api/auth/account-guide-requests의 202 본문. 가입 여부·요청 제한·발송 결과와 상관없이 항상 같은 값이다.
 * "메일을 보냈다"가 아니라 "안내 가능한 경우 보낸다"고 말한다(202를 전달 성공으로 표현하지 않음).
 */
public record AccountGuideAcceptedResponse(String message) {
    public static final AccountGuideAcceptedResponse INSTANCE = new AccountGuideAcceptedResponse(
            "입력한 주소가 가입한 이메일이면 아이디 안내 메일을 보냅니다.");
}
