package com.atheryon.mortgages.migration.reconciliation;

import com.atheryon.mortgages.migration.entity.MigrationJob;
import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import com.atheryon.mortgages.migration.entity.ReconciliationReport;
import com.atheryon.mortgages.migration.repository.MigrationJobRepository;
import com.atheryon.mortgages.migration.repository.MigrationLoanStagingRepository;
import com.atheryon.mortgages.migration.repository.ReconciliationReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final MigrationJobRepository jobRepository;
    private final MigrationLoanStagingRepository stagingRepository;
    private final ReconciliationReportRepository reconciliationRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // Fields to compare in field-level reconciliation
    private static final List<String> RECONCILE_FIELDS = List.of(
            "loanRef", "borrowerFirstName", "borrowerLastName", "loanAmount",
            "currentBalance", "interestRate", "loanTermMonths", "interestType",
            "repaymentFrequency", "addressState", "addressPostcode", "loanStatus",
            "settlementDate", "originationDate", "purchasePrice", "lvr");

    public ReconciliationService(MigrationJobRepository jobRepository,
                                  MigrationLoanStagingRepository stagingRepository,
                                  ReconciliationReportRepository reconciliationRepository,
                                  ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.stagingRepository = stagingRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReconciliationSummary.RecordRecon runRecordReconciliation(UUID jobId) {
        MigrationJob job = getJob(jobId);
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        int sourceCount = (int) job.getSourceRowCount();
        // Exclude duplicates from count
        int importedCount = (int) records.stream()
                .filter(r -> !"DUPLICATE".equals(r.getClassification()))
                .count();

        // Match by loan_ref presence in both source and transformed data
        Set<String> sourceLoanRefs = new HashSet<>();
        Set<String> importedLoanRefs = new HashSet<>();

        for (MigrationLoanStaging record : records) {
            Map<String, Object> source = parseJson(record.getSourceData());
            Map<String, Object> transformed = parseJson(record.getTransformedData());

            if (source != null) {
                String ref = extractLoanRef(source);
                if (ref != null) sourceLoanRefs.add(ref);
            }
            if (transformed != null && !"DUPLICATE".equals(record.getClassification())) {
                String ref = stringVal(transformed.get("loanRef"));
                if (ref != null) importedLoanRefs.add(ref);
            }
        }

        int matched = 0;
        for (String ref : sourceLoanRefs) {
            if (importedLoanRefs.contains(ref)) matched++;
        }

        int missingTarget = sourceLoanRefs.size() - matched;
        int missingSource = importedLoanRefs.size() - matched;

        ReconciliationSummary.RecordRecon recon = new ReconciliationSummary.RecordRecon(
                sourceCount, importedCount, matched, missingSource, missingTarget);

        saveReport(jobId, "RECORD", recon);
        log.info("Record reconciliation for job {}: {} source, {} imported, {} matched",
                jobId, sourceCount, importedCount, matched);

        return recon;
    }

    @Transactional
    public ReconciliationSummary.FinancialRecon runFinancialReconciliation(UUID jobId) {
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        BigDecimal sourceTotalAmount = BigDecimal.ZERO;
        BigDecimal importedTotalAmount = BigDecimal.ZERO;
        double sourceRateSum = 0;
        double importedRateSum = 0;
        int sourceRateCount = 0;
        int importedRateCount = 0;

        for (MigrationLoanStaging record : records) {
            if ("DUPLICATE".equals(record.getClassification())) continue;

            Map<String, Object> source = parseJson(record.getSourceData());
            Map<String, Object> transformed = parseJson(record.getTransformedData());

            // Source amounts — look for raw amount fields
            if (source != null) {
                BigDecimal amt = extractAmount(source, "loan_amount", "loanAmount", "Loan Amount",
                        "LoanAmount", "loan_amt", "LOAN_AMOUNT");
                if (amt != null) sourceTotalAmount = sourceTotalAmount.add(amt);

                Double rate = extractRate(source, "interest_rate", "interestRate", "Interest Rate",
                        "InterestRate", "rate", "INTEREST_RATE");
                if (rate != null) {
                    sourceRateSum += rate;
                    sourceRateCount++;
                }
            }

            // Imported (transformed) amounts
            if (transformed != null) {
                BigDecimal amt = toBigDecimal(transformed.get("loanAmount"));
                if (amt != null) importedTotalAmount = importedTotalAmount.add(amt);

                Double rate = toDouble(transformed.get("interestRate"));
                if (rate != null) {
                    importedRateSum += rate;
                    importedRateCount++;
                }
            }
        }

        BigDecimal variance = sourceTotalAmount.subtract(importedTotalAmount).abs();
        boolean amountMatch = variance.compareTo(new BigDecimal("0.01")) < 0;
        double sourceAvgRate = sourceRateCount > 0 ? sourceRateSum / sourceRateCount : 0;
        double importedAvgRate = importedRateCount > 0 ? importedRateSum / importedRateCount : 0;

        ReconciliationSummary.FinancialRecon recon = new ReconciliationSummary.FinancialRecon(
                sourceTotalAmount.setScale(2, RoundingMode.HALF_UP),
                importedTotalAmount.setScale(2, RoundingMode.HALF_UP),
                variance.setScale(2, RoundingMode.HALF_UP),
                amountMatch,
                Math.round(sourceAvgRate * 1000.0) / 1000.0,
                Math.round(importedAvgRate * 1000.0) / 1000.0);

        saveReport(jobId, "FINANCIAL", recon);
        log.info("Financial reconciliation for job {}: source={}, imported={}, variance={}, match={}",
                jobId, sourceTotalAmount, importedTotalAmount, variance, amountMatch);

        return recon;
    }

    @Transactional
    public ReconciliationSummary.FieldRecon runFieldReconciliation(UUID jobId) {
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        // Track mismatches per field
        Map<String, Integer> mismatchCounts = new LinkedHashMap<>();
        Map<String, String[]> mismatchSamples = new LinkedHashMap<>();
        int totalFieldsCompared = 0;
        int totalFieldsMatched = 0;
        int totalFieldsMismatched = 0;

        for (MigrationLoanStaging record : records) {
            if ("DUPLICATE".equals(record.getClassification())) continue;

            Map<String, Object> source = parseJson(record.getSourceData());
            Map<String, Object> mapped = parseJson(record.getMappedData());
            Map<String, Object> transformed = parseJson(record.getTransformedData());

            if (source == null || transformed == null) continue;

            for (String field : RECONCILE_FIELDS) {
                // Compare mapped value (post-mapping, pre-transform) with transformed value
                // This checks that transforms preserved intent
                String sourceVal = mapped != null ? stringVal(mapped.get(field)) : null;
                String importedVal = stringVal(transformed.get(field));

                // Skip if both null — nothing to compare
                if (sourceVal == null && importedVal == null) continue;

                totalFieldsCompared++;

                // Normalise for comparison (trim, case-insensitive for strings)
                String normSource = normalise(sourceVal);
                String normImported = normalise(importedVal);

                if (Objects.equals(normSource, normImported)) {
                    totalFieldsMatched++;
                } else {
                    totalFieldsMismatched++;
                    mismatchCounts.merge(field, 1, Integer::sum);
                    if (!mismatchSamples.containsKey(field)) {
                        mismatchSamples.put(field, new String[]{
                                sourceVal != null ? sourceVal : "(null)",
                                importedVal != null ? importedVal : "(null)"});
                    }
                }
            }
        }

        List<ReconciliationSummary.FieldMismatch> topMismatches = mismatchCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    String[] sample = mismatchSamples.get(e.getKey());
                    return new ReconciliationSummary.FieldMismatch(
                            e.getKey(), e.getValue(), sample[0], sample[1]);
                })
                .toList();

        ReconciliationSummary.FieldRecon recon = new ReconciliationSummary.FieldRecon(
                totalFieldsCompared, totalFieldsMatched, totalFieldsMismatched, topMismatches);

        saveReport(jobId, "FIELD", recon);
        log.info("Field reconciliation for job {}: {} compared, {} matched, {} mismatched",
                jobId, totalFieldsCompared, totalFieldsMatched, totalFieldsMismatched);

        return recon;
    }

    @Transactional
    public ReconciliationSummary getFullReconciliation(UUID jobId) {
        ReconciliationSummary.RecordRecon recordRecon = runRecordReconciliation(jobId);
        ReconciliationSummary.FinancialRecon financialRecon = runFinancialReconciliation(jobId);
        ReconciliationSummary.FieldRecon fieldRecon = runFieldReconciliation(jobId);

        boolean overallPass = recordRecon.missingTarget() == 0
                && recordRecon.missingSource() == 0
                && financialRecon.amountMatch()
                && fieldRecon.fieldsMismatched() == 0;

        ReconciliationSummary summary = new ReconciliationSummary(
                jobId, recordRecon, financialRecon, fieldRecon, overallPass);

        // Also persist the full summary on the MigrationJob
        MigrationJob job = getJob(jobId);
        job.setReconciliation(toJson(summary));
        jobRepository.save(job);

        return summary;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationReport> getReports(UUID jobId) {
        return reconciliationRepository.findByJobId(jobId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MigrationJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Migration job not found: " + jobId));
    }

    private void saveReport(UUID jobId, String reportType, Object data) {
        ReconciliationReport report = ReconciliationReport.builder()
                .jobId(jobId)
                .reportData(toJson(Map.of("type", reportType, "data", data)))
                .build();
        reconciliationRepository.save(report);
    }

    private String extractLoanRef(Map<String, Object> source) {
        // Try common source column names for loan reference
        for (String key : List.of("loan_ref", "loanRef", "Loan Ref", "LoanRef",
                "loan_reference", "LoanReference", "LOAN_REF", "loan_id", "LoanID")) {
            Object val = source.get(key);
            if (val != null && !String.valueOf(val).isBlank()) {
                return String.valueOf(val).trim();
            }
        }
        return null;
    }

    private BigDecimal extractAmount(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object val = source.get(key);
            if (val != null) {
                return toBigDecimal(val);
            }
        }
        return null;
    }

    private Double extractRate(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object val = source.get(key);
            if (val != null) {
                return toDouble(val);
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(val).replace("$", "").replace(",", "").trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try {
            String s = String.valueOf(val).replace("%", "").trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringVal(Object obj) {
        return obj != null ? String.valueOf(obj) : null;
    }

    private String normalise(String val) {
        if (val == null) return null;
        return val.trim().toLowerCase();
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON", e);
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise to JSON", e);
            return "{}";
        }
    }
}
