package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;

import java.math.BigDecimal;

public record ServiceabilityResponse(
    BigDecimal netDisposableIncome,
    BigDecimal debtServiceRatio,
    BigDecimal uncommittedMonthlyIncome,
    BigDecimal assessmentRate,
    ServiceabilityOutcome outcome
) {}
