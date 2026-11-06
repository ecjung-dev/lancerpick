package com.example.lancerpick.support;

import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * C3 SMTP 발송기 대신 쓰는 가짜 발송기. 받은 메일을 모아 두고, mode로 큐 가득 참·예외를 흉내 낸다.
 * 사용: 테스트 클래스에 @Import(FakeAccountMailSender.Config.class). 같은 설정을 쓰는 테스트끼리 Spring 컨텍스트를 함께 쓴다.
 */
public class FakeAccountMailSender implements AccountMailSender {
    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    public final List<AccountMail> sent = new CopyOnWriteArrayList<>();
    public volatile String mode = "ACCEPT";

    @Override
    public boolean enqueue(AccountMail mail) {
        if (mode.equals("THROW")) throw new IllegalStateException("smtp down");
        if (mode.equals("FULL")) return false;
        sent.add(mail);
        return true;
    }

    /** 테스트 시작 시 호출: 받은 메일을 비우고 정상 모드로 돌린다. */
    public void reset() {
        sent.clear();
        mode = "ACCEPT";
    }

    /** 이 수신자에게 간 메일들(보낸 순서). */
    public List<AccountMail> to(String email) {
        return sent.stream().filter(m -> m.to().equals(email)).toList();
    }

    /** 메일 본문 링크의 #token= 값. */
    public static String tokenIn(AccountMail mail) {
        Matcher matcher = TOKEN.matcher(mail.body());
        if (!matcher.find()) {
            throw new AssertionError("no token link in mail " + mail.challengeId());
        }
        return matcher.group(1);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        FakeAccountMailSender fakeAccountMailSender() {
            return new FakeAccountMailSender();
        }
    }
}
