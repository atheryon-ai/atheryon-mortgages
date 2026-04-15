package com.atheryon.mortgages.migration.quality;

import java.util.List;
import java.util.Map;

public record QualityReport(
    Map<QualityDimension, Double> dimensionScores,
    double compositeScore,
    List<QualityIssue> issues,
    int totalFields,
    int validFields
) {

    private static final Map<QualityDimension, Double> WEIGHTS = Map.of(
        QualityDimension.COMPLETENESS, 0.30,
        QualityDimension.ACCURACY, 0.25,
        QualityDimension.CONSISTENCY, 0.20,
        QualityDimension.VALIDITY, 0.20,
        QualityDimension.UNIQUENESS, 0.05
    );

    public static double computeComposite(Map<QualityDimension, Double> scores) {
        double total = 0.0;
        double weightSum = 0.0;
        for (var entry : WEIGHTS.entrySet()) {
            Double score = scores.get(entry.getKey());
            if (score != null) {
                total += score * entry.getValue();
                weightSum += entry.getValue();
            }
        }
        return weightSum > 0 ? total / weightSum : 0.0;
    }
}
