package com.example.lancerpick.contract.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PrepareContractRequest(
    @NotNull @DecimalMin("0") @DecimalMax("2147483646") @Digits(integer = 10, fraction = 0)
        BigDecimal expectedVersion,
    @NotNull @Size(max = 10000) String specialTerms) {}
