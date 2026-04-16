package com.atheryon.mortgages.migration.remediation;

import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import com.atheryon.mortgages.migration.entity.RemediationAction;
import com.atheryon.mortgages.migration.repository.MigrationLoanStagingRepository;
import com.atheryon.mortgages.migration.repository.RemediationActionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private final MigrationLoanStagingRepository stagingRepository;
    private final RemediationActionRepository remediationRepository;
    private final ObjectMapper objectMapper;

    private static final Map<String, RuleDefinition> RULES = new LinkedHashMap<>();

    static {
        RULES.put("REM-001", new RuleDefinition("REM-001",
                "Standardise date formats to YYYY-MM-DD",
                List.of("borrowerDob", "originationDate", "settlementDate", "valuationDate",
                        "lastPaymentDate", "ioExpiryDate", "fixedRateExpiry", "dischargeDate", "lastUpdated")));
        RULES.put("REM-002", new RuleDefinition("REM-002",
                "Normalise phone numbers to 04XX XXX XXX",
                List.of("borrowerPhone", "borrowerMobile")));
        RULES.put("REM-003", new RuleDefinition("REM-003",
                "Uppercase state codes (NSW, VIC, QLD, etc.)",
                List.of("addressState")));
        RULES.put("REM-004", new RuleDefinition("REM-004",
                "Map legacy loan statuses to domain enum values",
                List.of("loanStatus")));
        RULES.put("REM-005", new RuleDefinition("REM-005",
                "Remove whitespace from loan references",
                List.of("loanRef")));
        RULES.put("REM-006", new RuleDefinition("REM-006",
                "Default missing repayment frequency to Monthly",
                List.of("repaymentFrequency")));
        RULES.put("REM-007", new RuleDefinition("REM-007",
                "Calculate missing LVR from loan amount / purchase price",
                List.of("lvr")));
        RULES.put("REM-008", new RuleDefinition("REM-008",
                "Strip currency symbols from amount fields",
                List.of("purchasePrice", "loanAmount", "currentBalance", "repaymentAmount",
                        "lmiPremium", "arrearsAmount", "lastPaymentAmount", "offsetBalance",
                        "redrawBalance", "strataLevy", "annualFee")));
        RULES.put("REM-009", new RuleDefinition("REM-009",
                "Fix postcode padding (3-digit to 4-digit with leading zero)",
                List.of("addressPostcode")));
        RULES.put("REM-010", new RuleDefinition("REM-010",
                "Deduplicate records by loan_ref (keep latest)",
                List.of("loanRef")));
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // Date patterns for REM-001
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Phone pattern for REM-002
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\d+");

    // Legacy status map for REM-004
    private static final Map<String, String> LEGACY_STATUS_MAP = Map.ofEntries(
            Map.entry("ACTIVE", "CURRENT"),
            Map.entry("ACT", "CURRENT"),
            Map.entry("CURRENT", "CURRENT"),
            Map.entry("CLOSED", "DISCHARGED"),
            Map.entry("DISCHARGED", "DISCHARGED"),
            Map.entry("REPAID", "DISCHARGED"),
            Map.entry("SETTLED", "DISCHARGED"),
            Map.entry("DEFAULT", "IN_ARREARS"),
            Map.entry("ARREARS", "IN_ARREARS"),
            Map.entry("IN ARREARS", "IN_ARREARS"),
            Map.entry("IN_ARREARS", "IN_ARREARS"),
            Map.entry("HARDSHIP", "HARDSHIP"),
            Map.entry("PENDING", "PENDING")
    );

    // Australian state codes for REM-003
    private static final Set<String> VALID_STATES = Set.of(
            "NSW", "VIC", "QLD", "WA", "SA", "TAS", "ACT", "NT");

    public RemediationService(MigrationLoanStagingRepository stagingRepository,
                               RemediationActionRepository remediationRepository,
                               ObjectMapper objectMapper) {
        this.stagingRepository = stagingRepository;
        this.remediationRepository = remediationRepository;
        this.objectMapper = objectMapper;
    }

    public List<String> listRules() {
        return RULES.values().stream()
                .map(r -> r.ruleId() + ": " + r.description())
                .toList();
    }

    @Transactional(readOnly = true)
    public RemediationPreview preview(UUID jobId, String ruleId) {
        RuleDefinition rule = getRule(ruleId);
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        List<RemediationPreview.SampleChange> samples = new ArrayList<>();
        int affected = 0;

        for (MigrationLoanStaging record : records) {
            Map<String, Object> data = parseJson(record.getTransformedData());
            if (data == null) continue;

            List<RemediationPreview.SampleChange> changes = computeChanges(rule, record, data);
            if (!changes.isEmpty()) {
                affected++;
                if (samples.size() < 10) {
                    samples.addAll(changes);
                }
            }
        }

        return new RemediationPreview(ruleId, rule.description(), affected,
                samples.size() > 10 ? samples.subList(0, 10) : samples);
    }

    @Transactional
    public RemediationResult apply(UUID jobId, String ruleId, String appliedBy) {
        RuleDefinition rule = getRule(ruleId);
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        double qualityBefore = averageQuality(records);
        int affected = 0;
        int fixed = 0;

        // REM-010 is special: deduplication removes records
        if ("REM-010".equals(ruleId)) {
            int[] result = applyDeduplication(records);
            affected = result[0];
            fixed = result[1];
        } else {
            for (MigrationLoanStaging record : records) {
                Map<String, Object> data = parseJson(record.getTransformedData());
                if (data == null) continue;

                boolean changed = applyRule(rule, data);
                if (changed) {
                    affected++;
                    fixed++;
                    record.setTransformedData(toJson(data));
                    stagingRepository.save(record);
                }
            }
        }

        // Recalculate quality after
        List<MigrationLoanStaging> updated = stagingRepository.findByJobId(jobId);
        double qualityAfter = averageQuality(updated);

        // Log the action
        RemediationAction action = RemediationAction.builder()
                .jobId(jobId)
                .ruleId(ruleId)
                .description(rule.description())
                .condition(toJson(Map.of("targetFields", rule.targetFields())))
                .transform(toJson(Map.of("type", ruleId)))
                .affectedRows(affected)
                .qualityBefore(BigDecimal.valueOf(qualityBefore).setScale(4, RoundingMode.HALF_UP))
                .qualityAfter(BigDecimal.valueOf(qualityAfter).setScale(4, RoundingMode.HALF_UP))
                .actorId(appliedBy)
                .build();
        action = remediationRepository.save(action);

        log.info("Applied {} to job {}: {} affected, {} fixed, quality {:.4f} -> {:.4f}",
                ruleId, jobId, affected, fixed, qualityBefore, qualityAfter);

        return new RemediationResult(action.getId(), ruleId, affected, fixed,
                qualityBefore, qualityAfter, qualityAfter - qualityBefore);
    }

    @Transactional(readOnly = true)
    public List<RemediationAction> getHistory(UUID jobId) {
        return remediationRepository.findByJobId(jobId);
    }

    // ── Rule implementations ──────────────────────────────────────────────────

    private List<RemediationPreview.SampleChange> computeChanges(RuleDefinition rule,
                                                                   MigrationLoanStaging record,
                                                                   Map<String, Object> data) {
        List<RemediationPreview.SampleChange> changes = new ArrayList<>();
        String loanRef = stringVal(data.get("loanRef"));

        for (String field : rule.targetFields()) {
            Object oldVal = data.get(field);
            if (oldVal == null) {
                // Check if rule can fill missing values
                Object newVal = computeNewValue(rule.ruleId(), field, data);
                if (newVal != null) {
                    changes.add(new RemediationPreview.SampleChange(
                            record.getRowIndex(), loanRef, field, "(null)", String.valueOf(newVal)));
                }
                continue;
            }

            String oldStr = String.valueOf(oldVal);
            Object newVal = applyTransform(rule.ruleId(), field, oldStr, data);
            String newStr = String.valueOf(newVal);

            if (!oldStr.equals(newStr)) {
                changes.add(new RemediationPreview.SampleChange(
                        record.getRowIndex(), loanRef, field, oldStr, newStr));
            }
        }

        return changes;
    }

    private boolean applyRule(RuleDefinition rule, Map<String, Object> data) {
        boolean changed = false;

        for (String field : rule.targetFields()) {
            Object oldVal = data.get(field);

            if (oldVal == null) {
                Object newVal = computeNewValue(rule.ruleId(), field, data);
                if (newVal != null) {
                    data.put(field, newVal);
                    changed = true;
                }
                continue;
            }

            String oldStr = String.valueOf(oldVal);
            Object newVal = applyTransform(rule.ruleId(), field, oldStr, data);
            String newStr = String.valueOf(newVal);

            if (!oldStr.equals(newStr)) {
                data.put(field, newVal);
                changed = true;
            }
        }

        return changed;
    }

    private Object applyTransform(String ruleId, String field, String value, Map<String, Object> data) {
        return switch (ruleId) {
            case "REM-001" -> standardiseDate(value);
            case "REM-002" -> normalisePhone(value);
            case "REM-003" -> value.trim().toUpperCase();
            case "REM-004" -> mapLegacyStatus(value);
            case "REM-005" -> value.replaceAll("\\s+", "");
            case "REM-006" -> value; // handled by computeNewValue
            case "REM-007" -> value; // handled by computeNewValue
            case "REM-008" -> stripCurrencySymbols(value);
            case "REM-009" -> padPostcode(value);
            default -> value;
        };
    }

    private Object computeNewValue(String ruleId, String field, Map<String, Object> data) {
        return switch (ruleId) {
            case "REM-006" -> "repaymentFrequency".equals(field) ? "MONTHLY" : null;
            case "REM-007" -> {
                if (!"lvr".equals(field)) yield null;
                Object loanAmt = data.get("loanAmount");
                Object purchasePrice = data.get("purchasePrice");
                if (loanAmt instanceof Number la && purchasePrice instanceof Number pp) {
                    double ppVal = pp.doubleValue();
                    if (ppVal > 0) {
                        yield BigDecimal.valueOf(la.doubleValue() / ppVal * 100)
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    private String standardiseDate(String value) {
        if (value == null || value.isBlank()) return value;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(value.trim(), fmt);
                return date.format(ISO_DATE);
            } catch (DateTimeParseException ignored) {}
        }
        return value;
    }

    private String normalisePhone(String value) {
        if (value == null || value.isBlank()) return value;
        StringBuilder digits = new StringBuilder();
        Matcher m = PHONE_DIGITS.matcher(value);
        while (m.find()) digits.append(m.group());

        String d = digits.toString();
        // Convert +61 prefix to 0
        if (d.startsWith("61") && d.length() == 11) {
            d = "0" + d.substring(2);
        }
        if (d.length() == 10 && d.startsWith("04")) {
            return d.substring(0, 4) + " " + d.substring(4, 7) + " " + d.substring(7);
        }
        return value;
    }

    private String mapLegacyStatus(String value) {
        if (value == null) return value;
        String key = value.trim().toUpperCase();
        return LEGACY_STATUS_MAP.getOrDefault(key, key);
    }

    private String stripCurrencySymbols(String value) {
        if (value == null) return null;
        return value.replaceAll("[$€£¥]", "").replace(",", "").trim();
    }

    private String padPostcode(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.matches("\\d{3}")) {
            return "0" + trimmed;
        }
        return trimmed;
    }

    private int[] applyDeduplication(List<MigrationLoanStaging> records) {
        // Group by loan_ref, keep latest (highest rowIndex)
        Map<String, List<MigrationLoanStaging>> byLoanRef = new LinkedHashMap<>();

        for (MigrationLoanStaging record : records) {
            Map<String, Object> data = parseJson(record.getTransformedData());
            if (data == null) continue;
            String loanRef = stringVal(data.get("loanRef"));
            if (loanRef == null || loanRef.isBlank()) continue;
            byLoanRef.computeIfAbsent(loanRef, k -> new ArrayList<>()).add(record);
        }

        int duplicatesFound = 0;
        int duplicatesRemoved = 0;
        for (Map.Entry<String, List<MigrationLoanStaging>> entry : byLoanRef.entrySet()) {
            List<MigrationLoanStaging> group = entry.getValue();
            if (group.size() <= 1) continue;

            duplicatesFound += group.size() - 1;

            // Sort by rowIndex descending — keep the last one
            group.sort(Comparator.comparingInt(MigrationLoanStaging::getRowIndex).reversed());
            for (int i = 1; i < group.size(); i++) {
                MigrationLoanStaging dup = group.get(i);
                dup.setClassification("DUPLICATE");
                stagingRepository.save(dup);
                duplicatesRemoved++;
            }
        }

        return new int[]{duplicatesFound, duplicatesRemoved};
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RuleDefinition getRule(String ruleId) {
        RuleDefinition rule = RULES.get(ruleId);
        if (rule == null) {
            throw new NoSuchElementException("Unknown remediation rule: " + ruleId);
        }
        return rule;
    }

    private double averageQuality(List<MigrationLoanStaging> records) {
        return records.stream()
                .filter(r -> r.getQualityScore() != null)
                .mapToDouble(r -> r.getQualityScore().doubleValue())
                .average()
                .orElse(0.0);
    }

    private String stringVal(Object obj) {
        return obj != null ? String.valueOf(obj) : null;
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

    private record RuleDefinition(String ruleId, String description, List<String> targetFields) {}
}
