package com.example.lancerpick.member.dto;

import com.example.lancerpick.common.text.TextNormalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이메일 정규화·형식 규칙(API 10절). 가입과 비밀번호 재설정 요청이 같은 규칙을 쓰도록 한 곳에 둔다.
 * - 앞뒤 공백 제거(TextNormalizer) → Locale.ROOT 소문자화 → 3~254자(코드 포인트) → 형식 검사
 * - 로컬 파트: 영문·숫자·허용 특수문자, 점은 맨 앞·맨 뒤·연속 불가, 최대 64자
 * - 도메인: 점으로 구분된 라벨 2개 이상, 라벨은 영문·숫자·하이픈(앞뒤 하이픈 불가) 1~63자, 마지막은 영문 2자 이상
 * 예) review@domain..com, .a@b.com, a@-b.com, a@b → 거절
 */
public final class EmailRules {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=[^@]{1,64}@)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private EmailRules() {
    }

    /** 규칙에 맞으면 정규화된 이메일, 아니면 빈 값. 오류 응답은 호출한 서비스가 정한다. */
    public static Optional<String> normalize(String raw) {
        String email = TextNormalizer.normalize(raw).toLowerCase(Locale.ROOT);
        int length = email.codePointCount(0, email.length());
        if (length < 3 || length > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            return Optional.empty();
        }
        return Optional.of(email);
    }
}
