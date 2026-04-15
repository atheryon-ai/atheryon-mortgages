package com.atheryon.mortgages.migration.remediation;

import java.util.List;

public record RemediationPreview(
    String ruleId,
    String description,
    int recordsAffected,
    List<SampleChange> sampleChanges
) {
    public record SampleChange(int rowIndex, String loanRef, String field,
                                String oldValue, String newValue) {}
}
