package com.atheryon.mortgages.migration.pipeline;

import com.atheryon.mortgages.migration.entity.MigrationFieldMapping;
import com.atheryon.mortgages.migration.entity.MigrationJob;
import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import com.atheryon.mortgages.migration.repository.MigrationFieldMappingRepository;
import com.atheryon.mortgages.migration.repository.MigrationJobRepository;
import com.atheryon.mortgages.migration.repository.MigrationLoanStagingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Orchestrates the full migration pipeline:
 * Parse -> Map -> Transform -> Validate -> Classify -> Store
 */
@Service
public class MigrationPipelineService {

    private static final Logger log = LoggerFactory.getLogger(MigrationPipelineService.class);

    private final CsvParserService csvParser;
    private final ColumnAutoMapper columnMapper;
    private final MigrationJobRepository jobRepository;
    private final MigrationLoanStagingRepository stagingRepository;
    private final MigrationFieldMappingRepository fieldMappingRepository;
    private final ObjectMapper objectMapper;

    // Date patterns for normalisation
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter D_MM_YYYY = DateTimeFormatter.ofPattern("d/MM/yyyy");
    private static final DateTimeFormatter DD_MM_YY = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter[] DATE_FORMATS = {DD_MM_YYYY, YYYY_MM_DD, D_MM_YYYY, DD_MM_YY};

    private static final Pattern MONEY_PATTERN = Pattern.compile("^\\$?[\\d,]+\\.?\\d*$");

    public MigrationPipelineService(CsvParserService csvParser,
                                     ColumnAutoMapper columnMapper,
                                     MigrationJobRepository jobRepository,
                                     MigrationLoanStagingRepository stagingRepository,
                                     MigrationFieldMappingRepository fieldMappingRepository,
                                     ObjectMapper objectMapper) {
        this.csvParser = csvParser;
        this.columnMapper = columnMapper;
        this.jobRepository = jobRepository;
        this.stagingRepository = stagingRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MigrationJob startMigration(String jobName, String filename, InputStream csvData) throws IOException {
        // 1. Parse CSV
        log.info("Starting migration: {}", filename);
        CsvParserService.ParseResult parsed = csvParser.parse(csvData);

        // 2. Create job record
        MigrationJob job = MigrationJob.builder()
                .name(jobName)
                .sourceFilename(filename)
                .sourceRowCount(parsed.rows().size())
                .sourceColumnCount(parsed.headers().size())
                .status("INGESTED")
                .createdBy("migration-pipeline")
                .build();
        job = jobRepository.save(job);
        UUID jobId = job.getId();
        log.info("Created migration job {} with {} rows, {} columns",
                jobId, parsed.rows().size(), parsed.headers().size());

        // 3. Auto-map columns
        List<MigrationFieldMapping> mappings = columnMapper.mapColumns(jobId, parsed.headers());
        fieldMappingRepository.saveAll(mappings);
        job.setStatus("MAPPED");
        jobRepository.save(job);

        // Build source->target lookup from confirmed/suggested mappings
        Map<String, String> columnToTarget = new LinkedHashMap<>();
        for (MigrationFieldMapping mapping : mappings) {
            if (mapping.getTargetPath() != null) {
                columnToTarget.put(mapping.getSourceColumn(), mapping.getTargetPath());
            }
        }

        // 4. Process each row
        job.setStatus("TRANSFORMING");
        jobRepository.save(job);

        long cleanCount = 0;
        long warningCount = 0;
        long failedCount = 0;

        List<MigrationLoanStaging> stagingBatch = new ArrayList<>(500);

        for (int i = 0; i < parsed.rows().size(); i++) {
            Map<String, String> rawRow = parsed.rows().get(i);

            // Map fields
            Map<String, String> mappedRow = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : rawRow.entrySet()) {
                String target = columnToTarget.get(entry.getKey());
                if (target != null) {
                    mappedRow.put(target, entry.getValue());
                }
            }

            // Transform values
            Map<String, Object> transformed = transformRow(mappedRow);

            // Validate
            List<String> errors = validateRow(transformed);

            // Classify
            String classification;
            BigDecimal qualityScore;
            if (!errors.isEmpty() && errors.stream().anyMatch(e -> e.startsWith("CRITICAL:"))) {
                classification = "FAILED";
                qualityScore = BigDecimal.ZERO;
                failedCount++;
            } else if (!errors.isEmpty()) {
                classification = "WARNING";
                qualityScore = calculateQualityScore(transformed, errors);
                warningCount++;
            } else {
                classification = "CLEAN";
                qualityScore = BigDecimal.ONE;
                cleanCount++;
            }

            MigrationLoanStaging staging = MigrationLoanStaging.builder()
                    .jobId(jobId)
                    .rowIndex(i)
                    .sourceData(toJson(rawRow))
                    .mappedData(toJson(mappedRow))
                    .transformedData(toJson(transformed))
                    .validationResult(toJson(errors))
                    .classification(classification)
                    .qualityScore(qualityScore)
                    .build();

            stagingBatch.add(staging);

            // Flush in batches
            if (stagingBatch.size() >= 500) {
                stagingRepository.saveAll(stagingBatch);
                stagingBatch.clear();
            }
        }

        // Save remaining
        if (!stagingBatch.isEmpty()) {
            stagingRepository.saveAll(stagingBatch);
        }

        // 5. Update job with quality report
        long total = parsed.rows().size();
        Map<String, Object> qualityReport = new LinkedHashMap<>();
        qualityReport.put("totalRecords", total);
        qualityReport.put("clean", cleanCount);
        qualityReport.put("warning", warningCount);
        qualityReport.put("failed", failedCount);
        qualityReport.put("cleanPct", total > 0 ? round((double) cleanCount / total * 100) : 0);
        qualityReport.put("warningPct", total > 0 ? round((double) warningCount / total * 100) : 0);
        qualityReport.put("failedPct", total > 0 ? round((double) failedCount / total * 100) : 0);
        qualityReport.put("columnsMapped", columnToTarget.size());
        qualityReport.put("columnsTotal", parsed.headers().size());

        job.setQualityReport(toJson(qualityReport));
        job.setStatus("CLASSIFIED");
        jobRepository.save(job);

        log.info("Migration complete: {} clean, {} warning, {} failed out of {} rows",
                cleanCount, warningCount, failedCount, total);

        return job;
    }

    public MigrationJob getJobStatus(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Migration job not found: " + jobId));
    }

    // ── Transform ─────────────────────────────────────────────────────────────

    private Map<String, Object> transformRow(Map<String, String> mapped) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : mapped.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isBlank()) {
                result.put(field, null);
                continue;
            }

            result.put(field, switch (field) {
                // Date fields: normalise to ISO format
                case "borrowerDob", "originationDate", "settlementDate", "valuationDate",
                     "lastPaymentDate", "ioExpiryDate", "fixedRateExpiry", "dischargeDate",
                     "lastUpdated" -> normaliseDate(value);

                // Money fields: strip $ and commas, parse as number
                case "purchasePrice", "loanAmount", "currentBalance", "repaymentAmount",
                     "lmiPremium", "arrearsAmount", "lastPaymentAmount", "offsetBalance",
                     "redrawBalance", "strataLevy", "annualFee", "incomeGrossAnnual",
                     "incomeNetMonthly", "expensesMonthly", "creditCardBalance",
                     "otherLiabilities", "propertyValueEstimate", "discountMargin" ->
                        parseAmount(value);

                // Rate: legacy stores monthly, convert to annual percentage
                case "interestRate" -> normaliseRate(value);

                // Numeric fields
                case "loanTermMonths", "arrearsDays", "bedrooms", "bathrooms", "yearBuilt",
                     "internalScore" -> parseInteger(value);

                case "lvr", "landAreaSqm", "floorAreaSqm" -> parseDecimal(value);

                // Boolean-ish fields
                case "lmiFlag", "redrawEnabled", "interestOnlyFlag", "strataFlag",
                     "guarantorFlag" -> normaliseBoolean(value);

                // Enum-ish fields: normalise casing
                case "interestType" -> normaliseInterestType(value);
                case "repaymentFrequency" -> normaliseRepaymentFrequency(value);
                case "propertyType" -> capitalise(value);
                case "addressState" -> value.toUpperCase().trim();
                case "loanStatus" -> value.toUpperCase().trim();

                default -> value.trim();
            });
        }

        return result;
    }

    private String normaliseDate(String value) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(value.trim(), fmt);
                return date.format(YYYY_MM_DD);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return value; // return as-is if unparseable
    }

    private Object parseAmount(String value) {
        String cleaned = value.replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return value; // return original if unparseable
        }
    }

    private Object normaliseRate(String value) {
        try {
            BigDecimal rate = new BigDecimal(value.replace("%", "").trim());
            // If rate looks like a monthly rate (< 1.5), multiply by 12
            if (rate.compareTo(new BigDecimal("1.5")) < 0 && rate.compareTo(BigDecimal.ZERO) > 0) {
                rate = rate.multiply(new BigDecimal("12")).setScale(3, RoundingMode.HALF_UP);
            }
            return rate;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object parseDecimal(String value) {
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private boolean normaliseBoolean(String value) {
        String v = value.trim().toUpperCase();
        return "Y".equals(v) || "YES".equals(v) || "TRUE".equals(v) || "1".equals(v);
    }

    private String normaliseInterestType(String value) {
        return switch (value.trim().toUpperCase()) {
            case "VARIABLE", "VAR", "V" -> "VARIABLE";
            case "FIXED", "FIX", "F" -> "FIXED";
            case "SPLIT", "MIX", "MIXED" -> "SPLIT";
            default -> value.trim();
        };
    }

    private String normaliseRepaymentFrequency(String value) {
        return switch (value.trim().toUpperCase()) {
            case "MONTHLY", "M" -> "MONTHLY";
            case "FORTNIGHTLY", "F", "FORTNIGHT" -> "FORTNIGHTLY";
            case "WEEKLY", "W" -> "WEEKLY";
            default -> value.trim();
        };
    }

    private String capitalise(String value) {
        if (value == null || value.isBlank()) return value;
        String v = value.trim().toLowerCase();
        return v.substring(0, 1).toUpperCase() + v.substring(1);
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    private List<String> validateRow(Map<String, Object> row) {
        List<String> errors = new ArrayList<>();

        // Critical: loan reference is required
        if (isBlank(row.get("loanRef"))) {
            errors.add("CRITICAL: Missing loan reference");
        }

        // Critical: loan amount must be positive
        Object amount = row.get("loanAmount");
        if (amount instanceof BigDecimal bd) {
            if (bd.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("CRITICAL: Loan amount must be positive, got " + bd);
            }
        } else if (amount == null) {
            errors.add("CRITICAL: Missing loan amount");
        }

        // Warning: missing borrower name
        if (isBlank(row.get("borrowerFirstName")) && isBlank(row.get("borrowerLastName"))) {
            errors.add("WARNING: Missing borrower name");
        }

        // Warning: rate out of range
        Object rate = row.get("interestRate");
        if (rate instanceof BigDecimal bd) {
            if (bd.compareTo(new BigDecimal("1.0")) < 0 || bd.compareTo(new BigDecimal("15.0")) > 0) {
                errors.add("WARNING: Interest rate out of normal range: " + bd + "%");
            }
        }

        // Warning: missing income
        if (isBlank(row.get("incomeGrossAnnual"))) {
            errors.add("WARNING: Missing gross annual income");
        }

        // Warning: LVR > 80 but no LMI flag
        Object lvr = row.get("lvr");
        Object lmiFlag = row.get("lmiFlag");
        if (lvr instanceof BigDecimal lvrBd && lvrBd.compareTo(new BigDecimal("80")) > 0) {
            if (lmiFlag == null || Boolean.FALSE.equals(lmiFlag)) {
                errors.add("WARNING: LVR > 80% but LMI flag not set");
            }
        }

        // Warning: incomplete address
        if (isBlank(row.get("addressStreet")) || isBlank(row.get("addressSuburb"))) {
            errors.add("WARNING: Incomplete property address");
        }

        // Warning: missing settlement date
        if (isBlank(row.get("settlementDate"))) {
            errors.add("WARNING: Missing settlement date");
        }

        return errors;
    }

    private boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    private BigDecimal calculateQualityScore(Map<String, Object> row, List<String> errors) {
        // Start at 1.0, deduct for each issue
        double score = 1.0;
        for (String error : errors) {
            if (error.startsWith("CRITICAL:")) {
                score -= 0.3;
            } else {
                score -= 0.1;
            }
        }
        return BigDecimal.valueOf(Math.max(0.0, score)).setScale(4, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
