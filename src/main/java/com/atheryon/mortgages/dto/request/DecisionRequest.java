package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DecisionRequest(
    @NotNull DecisionOutcome outcome,
    @NotBlank String decidedBy,
    List<String> reasons,
    List<String> conditions
) {}
