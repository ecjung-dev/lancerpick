package com.example.lancerpick.wallet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** 회원 ID·토스 주문번호·상태·시크릿은 클라이언트 입력으로 받지 않는다. */
public record CreateWalletTopupRequest(
        @NotNull @Min(1) @Max(1000000) @Digits(integer = 7, fraction = 0) BigDecimal amount) {}
