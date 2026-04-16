package com.atheryon.mortgages.migration.remediation;

import java.util.UUID;

public record RemediationResult(
    UUID actionId,
    String ruleId,
    int recordsAffected,
    int recordsFixed,
    double qualityScoreBefore,
    double qualityScoreAfter,
    double qualityDelta
) {}
