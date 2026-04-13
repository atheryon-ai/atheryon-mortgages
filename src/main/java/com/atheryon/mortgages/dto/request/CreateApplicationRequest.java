package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateApplicationRequest(
    @NotNull UUID productId,
    @NotNull Channel channel,
    @NotNull LoanPurpose purpose,
    @NotNull OccupancyType occupancyType,
    @NotNull BigDecimal requestedAmount,
    @NotNull @Min(12) @Max(360) Integer termMonths,
    @NotNull InterestType interestType,
    @NotNull RepaymentType repaymentType,
    RepaymentFrequency repaymentFrequency,
    Boolean firstHomeBuyer,
    List<PartyRequest> parties,
    BrokerDetailRequest brokerDetails
) {}
