package com.example.lancerpick.member.dto;

/**
 * 비밀번호 원문 정책(API 10절, PR #14). 가입·비밀번호 변경 DTO가 같은 정규식을 쓰도록 한 곳에 둔다.
 * 영문 1자 + 숫자 1자 이상, 공백 없는 ASCII(U+0021~U+007E) 8~64자. 원문은 정규화(trim 등)하지 않는다.
 * 어노테이션 값으로 쓰므로 컴파일 시점 상수(static final String)여야 한다.
 */
public final class PasswordRules {
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}$";

    private PasswordRules() {
    }
}
