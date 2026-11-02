package com.example.lancerpick.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmWalletTopupRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[\\x21-\\x7E]+") String paymentKey,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_=-]{6,64}") String orderId,
        @NotNull @Min(1) @Max(1000000) @Digits(integer = 7, fraction = 0) BigDecimal amount) {}
