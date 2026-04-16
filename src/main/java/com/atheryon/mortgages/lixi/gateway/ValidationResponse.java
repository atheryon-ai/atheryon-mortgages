package com.atheryon.mortgages.lixi.gateway;

import java.util.List;

public record ValidationResponse(
        boolean valid,
        List<TierResult> tiers,
        long totalDurationMs
) {
    public record TierResult(
            String tier,
            boolean valid,
            List<String> errors,
            List<String> warnings,
            long durationMs
    ) {}
}
