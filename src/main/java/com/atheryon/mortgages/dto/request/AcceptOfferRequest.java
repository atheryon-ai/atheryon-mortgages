package com.atheryon.mortgages.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AcceptOfferRequest(
    @NotBlank String acceptedBy,
    @NotBlank String acceptanceMethod
) {}
