package com.atheryon.mortgages.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentVerifyRequest(@NotBlank String verifiedBy) {}
