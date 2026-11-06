package com.example.lancerpick.admin.config;

import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 조회는 복구 Job을 켜지 않아도 같은 재시도 설정을 읽을 수 있다. */
@Configuration(proxyBeanMethods = false)
@Profile("!demo")
@EnableConfigurationProperties(TopupRecoveryProperties.class)
public class AdminFinanceConfiguration {}
