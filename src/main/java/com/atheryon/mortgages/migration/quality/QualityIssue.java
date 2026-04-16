package com.atheryon.mortgages.migration.quality;

public record QualityIssue(
    QualityDimension dimension,
    String field,
    String message,
    String severity,
    String suggestedFix
) {}
