package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.*;

import java.math.BigDecimal;

public record UpdateApplicationRequest(
    LoanPurpose purpose,
    OccupancyType occupancyType,
    BigDecimal requestedAmount,
    Integer termMonths,
    InterestType interestType,
    RepaymentType repaymentType,
    RepaymentFrequency repaymentFrequency,
    Boolean firstHomeBuyer
) {}
