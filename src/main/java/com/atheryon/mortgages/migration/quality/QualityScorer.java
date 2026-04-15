package com.atheryon.mortgages.migration.quality;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QualityScorer {

    private static final List<String> REQUIRED_FIELDS = List.of(
        "loanRef", "borrowerFirstName", "borrowerLastName",
        "loanAmount", "interestRate", "loanStatus",
        "addressSuburb", "addressState", "addressPostcode"
    );

    private static final List<String> REQUIRED_VALUE_FIELDS = List.of(
        "purchasePrice", "propertyValueEstimate"
    );

    private static final Set<String> VALID_STATES = Set.of(
        "NSW", "VIC", "QLD", "SA", "WA", "TAS", "NT", "ACT"
    );

    private static final Set<String> VALID_LOAN_STATUSES = Set.of(
        "CURRENT", "ARREARS", "CLOSED", "DEFAULT", "SETTLED", "DISCHARGED",
        "PENDING", "APPROVED", "DECLINED", "WRITTEN_OFF"
    );

    private static final Set<String> VALID_INTEREST_TYPES = Set.of(
        "VARIABLE", "FIXED", "SPLIT"
    );

    private static final Set<String> VALID_PROPERTY_TYPES = Set.of(
        "HOUSE", "UNIT", "TOWNHOUSE", "APARTMENT", "VACANT_LAND", "RURAL"
    );

    private static final Pattern POSTCODE_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^\\+?[0-9\\s\\-()]{8,15}$"
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public QualityReport scoreRecord(Map<String, String> mappedData) {
        List<QualityIssue> issues = new ArrayList<>();

        double completenessScore = checkCompleteness(mappedData, issues);
        double accuracyScore = checkAccuracy(mappedData, issues);
        double consistencyScore = checkConsistency(mappedData, issues);
        double validityScore = checkValidity(mappedData, issues);

        Map<QualityDimension, Double> dimensionScores = new EnumMap<>(QualityDimension.class);
        dimensionScores.put(QualityDimension.COMPLETENESS, completenessScore);
        dimensionScores.put(QualityDimension.ACCURACY, accuracyScore);
        dimensionScores.put(QualityDimension.CONSISTENCY, consistencyScore);
        dimensionScores.put(QualityDimension.VALIDITY, validityScore);
        dimensionScores.put(QualityDimension.UNIQUENESS, 1.0); // handled at aggregate level

        double composite = QualityReport.computeComposite(dimensionScores);

        int totalFields = mappedData.size();
        int validFields = (int) mappedData.values().stream()
            .filter(v -> v != null && !v.isBlank())
            .count();

        return new QualityReport(dimensionScores, composite, issues, totalFields, validFields);
    }

    private double checkCompleteness(Map<String, String> data, List<QualityIssue> issues) {
        int totalRequired = REQUIRED_FIELDS.size() + 1; // +1 for value field (either purchasePrice or propertyValueEstimate)
        int present = 0;

        for (String field : REQUIRED_FIELDS) {
            if (hasValue(data, field)) {
                present++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.COMPLETENESS, field,
                    "Required field is missing or empty", "CRITICAL",
                    "Provide a value for " + field
                ));
            }
        }

        boolean hasValue = hasValue(data, "purchasePrice") || hasValue(data, "propertyValueEstimate");
        if (hasValue) {
            present++;
        } else {
            issues.add(new QualityIssue(
                QualityDimension.COMPLETENESS, "purchasePrice/propertyValueEstimate",
                "Neither purchasePrice nor propertyValueEstimate is present", "CRITICAL",
                "Provide purchasePrice or propertyValueEstimate"
            ));
        }

        return totalRequired > 0 ? (double) present / totalRequired : 1.0;
    }

    private double checkAccuracy(Map<String, String> data, List<QualityIssue> issues) {
        int checks = 0;
        int passed = 0;

        // Date checks (DOB, origination, settlement)
        for (String dateField : List.of("borrowerDob", "originationDate", "settlementDate")) {
            if (hasValue(data, dateField)) {
                checks++;
                LocalDate parsed = parseDate(data.get(dateField));
                if (parsed == null) {
                    issues.add(new QualityIssue(
                        QualityDimension.ACCURACY, dateField,
                        "Date is not in a recognised format", "HIGH",
                        "Use ISO format yyyy-MM-dd or dd/MM/yyyy"
                    ));
                } else if (dateField.equals("borrowerDob")) {
                    if (parsed.isAfter(LocalDate.now())) {
                        issues.add(new QualityIssue(
                            QualityDimension.ACCURACY, dateField,
                            "Date of birth is in the future", "HIGH",
                            "Correct the date of birth"
                        ));
                    } else if (parsed.isBefore(LocalDate.now().minusYears(100))) {
                        issues.add(new QualityIssue(
                            QualityDimension.ACCURACY, dateField,
                            "Date of birth is more than 100 years ago", "MEDIUM",
                            "Verify the date of birth"
                        ));
                    } else {
                        passed++;
                    }
                } else {
                    passed++;
                }
            }
        }

        // Amount checks (loanAmount, purchasePrice, propertyValueEstimate, currentBalance)
        for (String amountField : List.of("loanAmount", "purchasePrice", "propertyValueEstimate", "currentBalance", "repaymentAmount")) {
            if (hasValue(data, amountField)) {
                checks++;
                BigDecimal amount = parseDecimal(data.get(amountField));
                if (amount == null) {
                    issues.add(new QualityIssue(
                        QualityDimension.ACCURACY, amountField,
                        "Amount is not a valid number", "HIGH",
                        "Ensure the value is numeric"
                    ));
                } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    issues.add(new QualityIssue(
                        QualityDimension.ACCURACY, amountField,
                        "Amount must be positive", "HIGH",
                        "Correct the value to a positive number"
                    ));
                } else {
                    passed++;
                }
            }
        }

        // Interest rate check
        if (hasValue(data, "interestRate")) {
            checks++;
            BigDecimal rate = parseDecimal(data.get("interestRate"));
            if (rate == null) {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "interestRate",
                    "Interest rate is not a valid number", "HIGH",
                    "Ensure the rate is numeric (e.g. 5.25)"
                ));
            } else if (rate.compareTo(new BigDecimal("0.5")) < 0 || rate.compareTo(new BigDecimal("25")) > 0) {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "interestRate",
                    "Interest rate outside expected range (0.5%-25%)", "HIGH",
                    "Verify the interest rate is correct"
                ));
            } else {
                passed++;
            }
        }

        // Postcode check
        if (hasValue(data, "addressPostcode")) {
            checks++;
            if (POSTCODE_PATTERN.matcher(data.get("addressPostcode").trim()).matches()) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "addressPostcode",
                    "Postcode is not 4 digits", "MEDIUM",
                    "Australian postcodes must be exactly 4 digits"
                ));
            }
        }

        // Email check
        if (hasValue(data, "borrowerEmail")) {
            checks++;
            if (EMAIL_PATTERN.matcher(data.get("borrowerEmail").trim()).matches()) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "borrowerEmail",
                    "Email address format is invalid", "LOW",
                    "Provide a valid email address"
                ));
            }
        }

        // Phone check
        if (hasValue(data, "borrowerPhone")) {
            checks++;
            if (PHONE_PATTERN.matcher(data.get("borrowerPhone").trim()).matches()) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "borrowerPhone",
                    "Phone number format is invalid", "LOW",
                    "Provide a valid phone number"
                ));
            }
        }

        // LVR range check
        if (hasValue(data, "lvr")) {
            checks++;
            BigDecimal lvr = parseDecimal(data.get("lvr"));
            if (lvr == null) {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "lvr",
                    "LVR is not a valid number", "HIGH",
                    "Ensure LVR is numeric"
                ));
            } else if (lvr.compareTo(BigDecimal.ZERO) < 0 || lvr.compareTo(new BigDecimal("200")) > 0) {
                issues.add(new QualityIssue(
                    QualityDimension.ACCURACY, "lvr",
                    "LVR outside expected range (0-200)", "HIGH",
                    "Verify the LVR value"
                ));
            } else {
                if (lvr.compareTo(new BigDecimal("100")) > 0) {
                    issues.add(new QualityIssue(
                        QualityDimension.ACCURACY, "lvr",
                        "LVR exceeds 100% — verify this is correct", "MEDIUM",
                        null
                    ));
                }
                passed++;
            }
        }

        return checks > 0 ? (double) passed / checks : 1.0;
    }

    private double checkConsistency(Map<String, String> data, List<QualityIssue> issues) {
        int checks = 0;
        int passed = 0;

        // LVR vs loanAmount / purchasePrice
        BigDecimal loanAmount = parseDecimal(data.get("loanAmount"));
        BigDecimal purchasePrice = parseDecimal(data.get("purchasePrice"));
        BigDecimal reportedLvr = parseDecimal(data.get("lvr"));

        if (loanAmount != null && purchasePrice != null && purchasePrice.compareTo(BigDecimal.ZERO) > 0 && reportedLvr != null) {
            checks++;
            double calculatedLvr = loanAmount.doubleValue() / purchasePrice.doubleValue() * 100.0;
            double tolerance = reportedLvr.doubleValue() * 0.05;
            if (Math.abs(calculatedLvr - reportedLvr.doubleValue()) <= Math.max(tolerance, 1.0)) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.CONSISTENCY, "lvr",
                    String.format("LVR (%.1f) does not match loanAmount/purchasePrice (%.1f)", reportedLvr.doubleValue(), calculatedLvr),
                    "HIGH",
                    "Recalculate LVR or correct loan amount / purchase price"
                ));
            }
        }

        // Settlement date after origination date
        if (hasValue(data, "settlementDate") && hasValue(data, "originationDate")) {
            LocalDate settlement = parseDate(data.get("settlementDate"));
            LocalDate origination = parseDate(data.get("originationDate"));
            if (settlement != null && origination != null) {
                checks++;
                if (!settlement.isBefore(origination)) {
                    passed++;
                } else {
                    issues.add(new QualityIssue(
                        QualityDimension.CONSISTENCY, "settlementDate",
                        "Settlement date is before origination date", "HIGH",
                        "Verify settlement and origination dates"
                    ));
                }
            }
        }

        // Current balance <= original loan amount
        BigDecimal currentBalance = parseDecimal(data.get("currentBalance"));
        if (loanAmount != null && currentBalance != null) {
            checks++;
            if (currentBalance.compareTo(loanAmount) <= 0) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.CONSISTENCY, "currentBalance",
                    "Current balance exceeds original loan amount", "MEDIUM",
                    "Verify current balance includes capitalised interest/fees or correct"
                ));
            }
        }

        // Repayment amount reasonableness (rough check: monthly payment should be between 0.1% and 5% of loan amount)
        BigDecimal repayment = parseDecimal(data.get("repaymentAmount"));
        if (loanAmount != null && repayment != null && loanAmount.compareTo(BigDecimal.ZERO) > 0) {
            checks++;
            double ratio = repayment.doubleValue() / loanAmount.doubleValue();
            if (ratio >= 0.001 && ratio <= 0.05) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.CONSISTENCY, "repaymentAmount",
                    "Repayment amount seems unreasonable for the loan amount", "MEDIUM",
                    "Verify the repayment amount and frequency"
                ));
            }
        }

        return checks > 0 ? (double) passed / checks : 1.0;
    }

    private double checkValidity(Map<String, String> data, List<QualityIssue> issues) {
        int checks = 0;
        int passed = 0;

        // Loan status
        if (hasValue(data, "loanStatus")) {
            checks++;
            if (VALID_LOAN_STATUSES.contains(data.get("loanStatus").trim().toUpperCase())) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.VALIDITY, "loanStatus",
                    "Loan status '" + data.get("loanStatus") + "' is not a recognised value", "HIGH",
                    "Map to one of: " + String.join(", ", VALID_LOAN_STATUSES)
                ));
            }
        }

        // Interest type
        if (hasValue(data, "interestType")) {
            checks++;
            if (VALID_INTEREST_TYPES.contains(data.get("interestType").trim().toUpperCase())) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.VALIDITY, "interestType",
                    "Interest type '" + data.get("interestType") + "' is not a recognised value", "HIGH",
                    "Map to one of: " + String.join(", ", VALID_INTEREST_TYPES)
                ));
            }
        }

        // Property type
        if (hasValue(data, "propertyType")) {
            checks++;
            if (VALID_PROPERTY_TYPES.contains(data.get("propertyType").trim().toUpperCase())) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.VALIDITY, "propertyType",
                    "Property type '" + data.get("propertyType") + "' is not a recognised value", "MEDIUM",
                    "Map to one of: " + String.join(", ", VALID_PROPERTY_TYPES)
                ));
            }
        }

        // Australian state
        if (hasValue(data, "addressState")) {
            checks++;
            if (VALID_STATES.contains(data.get("addressState").trim().toUpperCase())) {
                passed++;
            } else {
                issues.add(new QualityIssue(
                    QualityDimension.VALIDITY, "addressState",
                    "State '" + data.get("addressState") + "' is not a valid Australian state/territory", "HIGH",
                    "Use one of: NSW, VIC, QLD, SA, WA, TAS, NT, ACT"
                ));
            }
        }

        return checks > 0 ? (double) passed / checks : 1.0;
    }

    private boolean hasValue(Map<String, String> data, String field) {
        String value = data.get(field);
        return value != null && !value.isBlank();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replaceAll("[,$]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
