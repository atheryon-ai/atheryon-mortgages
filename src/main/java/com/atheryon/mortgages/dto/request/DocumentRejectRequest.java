package com.atheryon.mortgages.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentRejectRequest(@NotBlank String reason) {}
