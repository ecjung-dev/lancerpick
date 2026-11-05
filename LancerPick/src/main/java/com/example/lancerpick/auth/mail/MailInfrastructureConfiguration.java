package com.example.lancerpick.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * C3 메일 발송 인프라 연결. lancerpick.mail.enabled=true일 때만 SMTP 발송기를 등록한다.
 * 기본값은 false라 메일 서버 없이도 앱·테스트·CI가 지금처럼 켜지고, 계정 메일은 FAILED(MAIL_NOT_CONFIGURED)로 남는다.
 * 켜면 JavaMailSender(spring.mail.host 등)가 반드시 있어야 하며, 없으면 서버 시작이 실패한다.
 */
@Configuration
@Profile("!demo")
@ConditionalOnProperty(name = "lancerpick.mail.enabled", havingValue = "true")
public class MailInfrastructureConfiguration {

    @Bean
    SmtpAccountMailSender smtpAccountMailSender(JavaMailSender mailSender, AccountMailDeliveryRecorder recorder,
            @Value("${lancerpick.mail.from}") String from,
            @Value("${lancerpick.mail.queue-capacity:100}") int queueCapacity) {
        return new SmtpAccountMailSender(mailSender, recorder, from, queueCapacity);
    }
}
