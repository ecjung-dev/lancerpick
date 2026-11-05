package com.example.lancerpick.contract.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SignContractRequest(
    @NotNull @DecimalMin("1") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0)
        BigDecimal version,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentHash,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String fileHash,
    @NotNull @AssertTrue Boolean agreed) {}
