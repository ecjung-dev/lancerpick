package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder;
import com.example.lancerpick.auth.mail.AccountMailSender;
import com.example.lancerpick.auth.mail.MailInfrastructureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

/** DB 없이 확인: 메일 발송기는 lancerpick.mail.enabled=true일 때만 등록되고, 켜면 JavaMailSender가 꼭 있어야 한다. */
class MailInfrastructureConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailInfrastructureConfiguration.class)
            .withBean(AccountMailDeliveryRecorder.class, () -> mock(AccountMailDeliveryRecorder.class))
            .withPropertyValues("lancerpick.mail.from=LancerPick <no-reply@lancerpick.local>");

    @Test
    void disabledByDefault() {
        runner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).doesNotHaveBean(AccountMailSender.class));
    }

    @Test
    void enabledRegistersTheSender() {
        runner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withPropertyValues("lancerpick.mail.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AccountMailSender.class));
    }

    @Test
    void enabledWithoutAMailServerConfigFailsAtStartup() {
        runner.withPropertyValues("lancerpick.mail.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void demoProfileNeverRegistersTheSender() {
        runner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withPropertyValues("lancerpick.mail.enabled=true", "spring.profiles.active=demo")
                .run(context -> assertThat(context).doesNotHaveBean(AccountMailSender.class));
    }
}
