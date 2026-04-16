package com.atheryon.mortgages.migration.quality;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobQualityReport(
    UUID jobId,
    int totalRecords,
    double averageScore,
    Map<QualityDimension, Double> dimensionAverages,
    Map<String, Integer> scoreDistribution,
    List<IssueFrequency> topIssues,
    Map<String, Double> fieldCoverage,
    Map<String, Integer> statusCounts
) {
    public record IssueFrequency(String field, String message, QualityDimension dimension, int count) {}
}
