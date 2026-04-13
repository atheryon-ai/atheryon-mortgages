package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.ValuationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValuationRequest(
    @NotNull ValuationType valuationType,
    @NotBlank String provider
) {}
