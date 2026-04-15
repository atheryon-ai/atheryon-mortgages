package com.atheryon.mortgages.lixi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tier 3 — Lender-specific validation rules.
 * <p>
 * Configurable rules that vary by lender. Each lender has a rule set
 * identified by lenderCode. "DEFAULT" provides sensible baseline rules.
 */
@Service
public class LenderRuleValidator {

    private static final Logger log = LoggerFactory.getLogger(LenderRuleValidator.class);
    private static final String TIER = "LENDER";

    // Default lender rule parameters
    private static final double DEFAULT_MAX_LOAN_AMOUNT = 5_000_000.0;
    private static final double DEFAULT_MIN_LOAN_AMOUNT = 50_000.0;
    private static final double DEFAULT_MAX_LVR_WITH_LMI = 95.0;
    private static final double DEFAULT_MAX_LVR_WITHOUT_LMI = 80.0;
    private static final int DEFAULT_MAX_LOAN_TERM_MONTHS = 360;
    private static final int DEFAULT_MIN_BORROWER_AGE = 18;
    private static final int DEFAULT_MAX_BORROWER_AGE_AT_MATURITY = 70;
    private static final Set<String> DEFAULT_POSTCODE_BLACKLIST = Set.of();
    private static final Set<String> DEFAULT_RESTRICTED_PROPERTY_TYPES = Set.of("Rural");
    private static final int DEFAULT_MIN_EMPLOYMENT_MONTHS = 3;
    private static final int DEFAULT_IO_MAX_TERM_MONTHS = 60;

    public ValidationResult validate(JsonNode lixiMessage, String lenderCode) {
        long start = System.nanoTime();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        log.debug("Running Tier 3 lender rules with lenderCode={}", lenderCode);

        JsonNode application = lixiMessage.path("Package").path("Content").path("Application");
        if (application.isMissingNode()) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return ValidationResult.success(TIER, durationMs);
        }

        JsonNode loanDetails = application.path("LoanDetails");
        JsonNode applicants = application.path("PersonApplicant");
        JsonNode realEstate = application.path("RealEstateAsset");

        // All rules use DEFAULT parameters for now
        checkLR001(loanDetails, errors);
        checkLR002(loanDetails, errors);
        checkLR003(loanDetails, realEstate, errors, warnings);
        checkLR004(loanDetails, errors);
        checkLR005(applicants, errors);
        checkLR006(applicants, loanDetails, errors);
        checkLR007(realEstate, errors);
        checkLR008(realEstate, errors);
        checkLR009(applicants, errors);
        checkLR010(loanDetails, errors);

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (errors.isEmpty() && warnings.isEmpty()) {
            return ValidationResult.success(TIER, durationMs);
        }
        if (errors.isEmpty()) {
            return ValidationResult.withWarnings(TIER, warnings, durationMs);
        }
        return ValidationResult.failure(TIER, errors, warnings, durationMs);
    }

    /** LR-001: Max loan amount ($5M for default) */
    private void checkLR001(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;
        double amount = loanAmount.doubleValue();
        if (amount > DEFAULT_MAX_LOAN_AMOUNT) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "Loan amount ($" + formatAmount(amount) + ") exceeds lender maximum ($" + formatAmount(DEFAULT_MAX_LOAN_AMOUNT) + ")",
                    "LR-001"));
        }
    }

    /** LR-002: Min loan amount ($50K for default) */
    private void checkLR002(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;
        double amount = loanAmount.doubleValue();
        if (amount < DEFAULT_MIN_LOAN_AMOUNT) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "Loan amount ($" + formatAmount(amount) + ") is below lender minimum ($" + formatAmount(DEFAULT_MIN_LOAN_AMOUNT) + ")",
                    "LR-002"));
        }
    }

    /** LR-003: Max LVR (95% with LMI, 80% without LMI) */
    private void checkLR003(JsonNode loanDetails, JsonNode realEstate,
                            List<ValidationResult.ValidationError> errors,
                            List<ValidationResult.ValidationWarning> warnings) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;
        double loan = loanAmount.doubleValue();

        double propertyValue = extractPropertyValue(realEstate);
        if (propertyValue <= 0) return;

        double lvr = (loan / propertyValue) * 100;

        if (lvr > DEFAULT_MAX_LVR_WITH_LMI) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "LVR of " + String.format("%.1f", lvr) + "% exceeds maximum " + String.format("%.0f", DEFAULT_MAX_LVR_WITH_LMI) + "% (even with LMI)",
                    "LR-003"));
        } else if (lvr > DEFAULT_MAX_LVR_WITHOUT_LMI) {
            boolean hasLmi = loanDetails.path("LMI").asBoolean(false);
            if (!hasLmi) {
                warnings.add(warning("$.Package.Content.Application.LoanDetails.LoanAmount",
                        "LVR of " + String.format("%.1f", lvr) + "% exceeds " + String.format("%.0f", DEFAULT_MAX_LVR_WITHOUT_LMI) + "% — LMI is required but not indicated",
                        "LR-003"));
            }
        }
    }

    /** LR-004: Max loan term (30 years / 360 months) */
    private void checkLR004(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        JsonNode term = loanDetails.path("Term");
        if (!term.isNumber()) return;
        int months = term.intValue();
        if (months > DEFAULT_MAX_LOAN_TERM_MONTHS) {
            errors.add(error("$.Package.Content.Application.LoanDetails.Term",
                    "Loan term (" + months + " months) exceeds lender maximum (" + DEFAULT_MAX_LOAN_TERM_MONTHS + " months / " + (DEFAULT_MAX_LOAN_TERM_MONTHS / 12) + " years)",
                    "LR-004"));
        }
    }

    /** LR-005: Min borrower age (18) */
    private void checkLR005(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            int age = calculateAge(applicants.get(i));
            if (age >= 0 && age < DEFAULT_MIN_BORROWER_AGE) {
                errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].DateOfBirth",
                        "Borrower age (" + age + ") is below lender minimum (" + DEFAULT_MIN_BORROWER_AGE + ")",
                        "LR-005"));
            }
        }
    }

    /** LR-006: Max borrower age at maturity (70) */
    private void checkLR006(JsonNode applicants, JsonNode loanDetails,
                            List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        JsonNode term = loanDetails.path("Term");
        if (!term.isNumber()) return;
        int termMonths = term.intValue();

        for (int i = 0; i < applicants.size(); i++) {
            int age = calculateAge(applicants.get(i));
            if (age < 0) continue;
            int ageAtMaturity = age + (termMonths / 12);
            if (ageAtMaturity > DEFAULT_MAX_BORROWER_AGE_AT_MATURITY) {
                errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "]",
                        "Borrower will be " + ageAtMaturity + " at loan maturity, exceeding lender maximum of " + DEFAULT_MAX_BORROWER_AGE_AT_MATURITY,
                        "LR-006"));
            }
        }
    }

    /** LR-007: Postcode blacklist (empty by default, but structure exists) */
    private void checkLR007(JsonNode realEstate, List<ValidationResult.ValidationError> errors) {
        if (!realEstate.isArray() || DEFAULT_POSTCODE_BLACKLIST.isEmpty()) return;
        for (int i = 0; i < realEstate.size(); i++) {
            String postcode = realEstate.get(i).path("Address").path("Postcode").asText("");
            if (!postcode.isBlank() && DEFAULT_POSTCODE_BLACKLIST.contains(postcode)) {
                errors.add(error("$.Package.Content.Application.RealEstateAsset[" + i + "].Address.Postcode",
                        "Postcode " + postcode + " is in the lender's restricted postcode list",
                        "LR-007"));
            }
        }
    }

    /** LR-008: Property type restrictions (no Rural for default lender) */
    private void checkLR008(JsonNode realEstate, List<ValidationResult.ValidationError> errors) {
        if (!realEstate.isArray()) return;
        for (int i = 0; i < realEstate.size(); i++) {
            String propType = realEstate.get(i).path("PropertyType").asText("");
            if (!propType.isBlank() && DEFAULT_RESTRICTED_PROPERTY_TYPES.contains(propType)) {
                errors.add(error("$.Package.Content.Application.RealEstateAsset[" + i + "].PropertyType",
                        "Property type '" + propType + "' is not accepted by this lender",
                        "LR-008"));
            }
        }
    }

    /** LR-009: Min employment duration for primary applicant (3 months) */
    private void checkLR009(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode applicant = applicants.get(i);
            if (!applicant.path("@PrimaryApplicant").asBoolean(false)) continue;

            JsonNode employment = applicant.path("Employment");
            if (!employment.isArray() || employment.isEmpty()) return;

            // Check the current/first employment record
            JsonNode currentEmployment = employment.get(0);
            String startDateStr = currentEmployment.path("StartDate").asText("");
            if (startDateStr.isBlank()) return;

            try {
                LocalDate startDate = LocalDate.parse(startDateStr);
                long monthsEmployed = Period.between(startDate, LocalDate.now()).toTotalMonths();
                if (monthsEmployed < DEFAULT_MIN_EMPLOYMENT_MONTHS) {
                    errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].Employment[0].StartDate",
                            "Primary applicant employment duration (" + monthsEmployed + " months) is below lender minimum (" + DEFAULT_MIN_EMPLOYMENT_MONTHS + " months)",
                            "LR-009"));
                }
            } catch (DateTimeParseException e) {
                // Date parsing handled by schema/schematron tiers
            }
            return;
        }
    }

    /** LR-010: Interest-only max term (5 years / 60 months) */
    private void checkLR010(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        String repaymentType = loanDetails.path("RepaymentType").asText("");
        if (!"InterestOnly".equals(repaymentType)) return;

        JsonNode ioTerm = loanDetails.path("InterestOnlyTerm");
        if (!ioTerm.isNumber()) {
            // If IO but no IO term specified, check the full loan term
            JsonNode term = loanDetails.path("Term");
            if (term.isNumber() && term.intValue() > DEFAULT_IO_MAX_TERM_MONTHS) {
                errors.add(error("$.Package.Content.Application.LoanDetails.Term",
                        "Interest-only loan term (" + term.intValue() + " months) exceeds lender maximum (" + DEFAULT_IO_MAX_TERM_MONTHS + " months)",
                        "LR-010"));
            }
            return;
        }

        int ioMonths = ioTerm.intValue();
        if (ioMonths > DEFAULT_IO_MAX_TERM_MONTHS) {
            errors.add(error("$.Package.Content.Application.LoanDetails.InterestOnlyTerm",
                    "Interest-only period (" + ioMonths + " months) exceeds lender maximum (" + DEFAULT_IO_MAX_TERM_MONTHS + " months)",
                    "LR-010"));
        }
    }

    // --- Helpers ---

    private int calculateAge(JsonNode applicant) {
        String dobStr = applicant.path("DateOfBirth").asText("");
        if (dobStr.isBlank()) return -1;
        try {
            return Period.between(LocalDate.parse(dobStr), LocalDate.now()).getYears();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    private double extractPropertyValue(JsonNode realEstate) {
        if (!realEstate.isArray() || realEstate.isEmpty()) return 0;
        JsonNode contractPrice = realEstate.get(0).path("ContractPrice").path("Amount");
        if (contractPrice.isNumber()) return contractPrice.doubleValue();
        JsonNode estimatedValue = realEstate.get(0).path("EstimatedValue").path("Amount");
        if (estimatedValue.isNumber()) return estimatedValue.doubleValue();
        return 0;
    }

    private static String formatAmount(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        }
        return String.format("%,.0f", amount);
    }

    private static ValidationResult.ValidationError error(String path, String message, String code) {
        return new ValidationResult.ValidationError(path, message, code);
    }

    private static ValidationResult.ValidationWarning warning(String path, String message, String code) {
        return new ValidationResult.ValidationWarning(path, message, code);
    }
}
