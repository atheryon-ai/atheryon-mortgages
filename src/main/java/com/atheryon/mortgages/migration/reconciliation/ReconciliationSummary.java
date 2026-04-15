package com.atheryon.mortgages.migration.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReconciliationSummary(
    UUID jobId,
    RecordRecon records,
    FinancialRecon financials,
    FieldRecon fields,
    boolean overallPass
) {
    public record RecordRecon(int sourceCount, int importedCount, int matched,
                               int missingSource, int missingTarget) {}

    public record FinancialRecon(BigDecimal sourceTotalAmount, BigDecimal importedTotalAmount,
                                  BigDecimal variance, boolean amountMatch,
                                  double sourceAvgRate, double importedAvgRate) {}

    public record FieldRecon(int fieldsCompared, int fieldsMatched, int fieldsMismatched,
                              List<FieldMismatch> topMismatches) {}

    public record FieldMismatch(String field, int mismatchCount, String sampleSource,
                                 String sampleImported) {}
}
