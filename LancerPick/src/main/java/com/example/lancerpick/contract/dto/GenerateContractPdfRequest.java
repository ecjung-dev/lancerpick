package com.example.lancerpick.contract.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Preserve the JSON number until validation; never truncate a fractional version to an integer. */
public record GenerateContractPdfRequest(
    @NotNull @DecimalMin("1") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0)
        BigDecimal version) {}
