package com.atheryon.mortgages.lixi.schema;

import java.util.Collections;
import java.util.List;

public record ValidationResult(
        boolean valid,
        List<ValidationError> errors,
        List<ValidationWarning> warnings,
        String tier,
        long durationMs
) {
    public record ValidationError(String path, String message, String code) {}
    public record ValidationWarning(String path, String message, String code) {}

    public static ValidationResult success(String tier, long durationMs) {
        return new ValidationResult(true, Collections.emptyList(), Collections.emptyList(), tier, durationMs);
    }

    public static ValidationResult failure(String tier, List<ValidationError> errors, long durationMs) {
        return new ValidationResult(false, errors, Collections.emptyList(), tier, durationMs);
    }

    public static ValidationResult failure(String tier, List<ValidationError> errors,
                                           List<ValidationWarning> warnings, long durationMs) {
        return new ValidationResult(false, errors, warnings, tier, durationMs);
    }

    public static ValidationResult withWarnings(String tier, List<ValidationWarning> warnings, long durationMs) {
        return new ValidationResult(true, Collections.emptyList(), warnings, tier, durationMs);
    }
}
