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
 * Tier 2 — Schematron-compatible business rule validation.
 * <p>
 * Real LIXI2 Schematron rules require a $985/yr license (1600+ rules).
 * This implements 10 core business rules as a built-in Schematron-compatible layer.
 */
@Service
public class LixiSchematronValidator {

    private static final Logger log = LoggerFactory.getLogger(LixiSchematronValidator.class);
    private static final String TIER = "SCHEMATRON";

    private static final Set<String> VALID_INTEREST_TYPES = Set.of("Variable", "Fixed", "Split");
    private static final Set<String> VALID_REPAYMENT_TYPES = Set.of("PrincipalAndInterest", "InterestOnly");
    private static final Set<String> VALID_PROPERTY_TYPES = Set.of("House", "Unit", "Townhouse", "Apartment", "Land");

    public ValidationResult validate(JsonNode lixiMessage) {
        long start = System.nanoTime();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        JsonNode application = lixiMessage.path("Package").path("Content").path("Application");
        if (application.isMissingNode()) {
            errors.add(error("$.Package.Content.Application", "Application element is required", "SCH-000"));
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return ValidationResult.failure(TIER, errors, warnings, durationMs);
        }

        JsonNode loanDetails = application.path("LoanDetails");
        JsonNode applicants = application.path("PersonApplicant");
        JsonNode realEstate = application.path("RealEstateAsset");

        checkSCH001(loanDetails, errors);
        checkSCH002(loanDetails, realEstate, errors, warnings);
        checkSCH003(applicants, errors);
        checkSCH004(applicants, errors);
        checkSCH005(applicants, errors);
        checkSCH006(loanDetails, errors);
        checkSCH007(loanDetails, errors);
        checkSCH008(loanDetails, errors);
        checkSCH009(applicants, errors);
        checkSCH010(realEstate, errors);

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (errors.isEmpty() && warnings.isEmpty()) {
            return ValidationResult.success(TIER, durationMs);
        }
        if (errors.isEmpty()) {
            return ValidationResult.withWarnings(TIER, warnings, durationMs);
        }
        return ValidationResult.failure(TIER, errors, warnings, durationMs);
    }

    /** SCH-001: LoanAmount must be > 0 */
    private void checkSCH001(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (loanAmount.isMissingNode() || !loanAmount.isNumber()) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "LoanAmount is required and must be numeric", "SCH-001"));
        } else if (loanAmount.doubleValue() <= 0) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "LoanAmount must be greater than 0", "SCH-001"));
        }
    }

    /** SCH-002: LoanAmount must be <= ContractPrice (LVR check) */
    private void checkSCH002(JsonNode loanDetails, JsonNode realEstate,
                             List<ValidationResult.ValidationError> errors,
                             List<ValidationResult.ValidationWarning> warnings) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;

        double loan = loanAmount.doubleValue();

        if (realEstate.isArray() && !realEstate.isEmpty()) {
            JsonNode contractPrice = realEstate.get(0).path("ContractPrice").path("Amount");
            if (contractPrice.isNumber()) {
                double price = contractPrice.doubleValue();
                if (loan > price) {
                    errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                            "LoanAmount (" + loan + ") must not exceed ContractPrice (" + price + ")",
                            "SCH-002"));
                } else {
                    double lvr = (loan / price) * 100;
                    if (lvr > 80) {
                        warnings.add(warning("$.Package.Content.Application.LoanDetails.LoanAmount",
                                "LVR is " + String.format("%.1f", lvr) + "% — LMI may be required",
                                "SCH-002"));
                    }
                }
            }
        }
    }

    /** SCH-003: At least one PersonApplicant required */
    private void checkSCH003(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray() || applicants.isEmpty()) {
            errors.add(error("$.Package.Content.Application.PersonApplicant",
                    "At least one PersonApplicant is required", "SCH-003"));
        }
    }

    /** SCH-004: Primary applicant must have DateOfBirth */
    private void checkSCH004(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode applicant = applicants.get(i);
            if (applicant.path("@PrimaryApplicant").asBoolean(false)) {
                JsonNode dob = applicant.path("DateOfBirth");
                if (dob.isMissingNode() || dob.asText().isBlank()) {
                    errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].DateOfBirth",
                            "Primary applicant must have DateOfBirth", "SCH-004"));
                }
                return;
            }
        }
    }

    /** SCH-005: Primary applicant must be >= 18 years old */
    private void checkSCH005(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode applicant = applicants.get(i);
            if (applicant.path("@PrimaryApplicant").asBoolean(false)) {
                String dobStr = applicant.path("DateOfBirth").asText("");
                if (dobStr.isBlank()) return; // SCH-004 already catches this
                try {
                    LocalDate dob = LocalDate.parse(dobStr);
                    int age = Period.between(dob, LocalDate.now()).getYears();
                    if (age < 18) {
                        errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].DateOfBirth",
                                "Primary applicant must be at least 18 years old (current age: " + age + ")",
                                "SCH-005"));
                    }
                } catch (DateTimeParseException e) {
                    errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].DateOfBirth",
                            "DateOfBirth is not a valid date: " + dobStr, "SCH-005"));
                }
                return;
            }
        }
    }

    /** SCH-006: Term must be 12–480 months */
    private void checkSCH006(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        JsonNode term = loanDetails.path("Term");
        if (term.isMissingNode() || !term.isNumber()) {
            errors.add(error("$.Package.Content.Application.LoanDetails.Term",
                    "Term is required and must be numeric", "SCH-006"));
        } else {
            int months = term.intValue();
            if (months < 12 || months > 480) {
                errors.add(error("$.Package.Content.Application.LoanDetails.Term",
                        "Term must be between 12 and 480 months (got " + months + ")", "SCH-006"));
            }
        }
    }

    /** SCH-007: InterestType must be Variable, Fixed, or Split */
    private void checkSCH007(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        String interestType = loanDetails.path("InterestType").asText("");
        if (interestType.isBlank()) {
            errors.add(error("$.Package.Content.Application.LoanDetails.InterestType",
                    "InterestType is required", "SCH-007"));
        } else if (!VALID_INTEREST_TYPES.contains(interestType)) {
            errors.add(error("$.Package.Content.Application.LoanDetails.InterestType",
                    "InterestType must be one of " + VALID_INTEREST_TYPES + " (got '" + interestType + "')",
                    "SCH-007"));
        }
    }

    /** SCH-008: RepaymentType must be PrincipalAndInterest or InterestOnly */
    private void checkSCH008(JsonNode loanDetails, List<ValidationResult.ValidationError> errors) {
        String repaymentType = loanDetails.path("RepaymentType").asText("");
        if (repaymentType.isBlank()) {
            errors.add(error("$.Package.Content.Application.LoanDetails.RepaymentType",
                    "RepaymentType is required", "SCH-008"));
        } else if (!VALID_REPAYMENT_TYPES.contains(repaymentType)) {
            errors.add(error("$.Package.Content.Application.LoanDetails.RepaymentType",
                    "RepaymentType must be one of " + VALID_REPAYMENT_TYPES + " (got '" + repaymentType + "')",
                    "SCH-008"));
        }
    }

    /** SCH-009: Each PersonApplicant must have at least one Employment record */
    private void checkSCH009(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode employment = applicants.get(i).path("Employment");
            if (!employment.isArray() || employment.isEmpty()) {
                String name = applicants.get(i).path("PersonName").path("FirstName").asText("Applicant " + i);
                errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].Employment",
                        "PersonApplicant '" + name + "' must have at least one Employment record",
                        "SCH-009"));
            }
        }
    }

    /** SCH-010: PropertyType must be recognized */
    private void checkSCH010(JsonNode realEstate, List<ValidationResult.ValidationError> errors) {
        if (!realEstate.isArray()) return;
        for (int i = 0; i < realEstate.size(); i++) {
            String propType = realEstate.get(i).path("PropertyType").asText("");
            if (!propType.isBlank() && !VALID_PROPERTY_TYPES.contains(propType)) {
                errors.add(error("$.Package.Content.Application.RealEstateAsset[" + i + "].PropertyType",
                        "PropertyType must be one of " + VALID_PROPERTY_TYPES + " (got '" + propType + "')",
                        "SCH-010"));
            }
        }
    }

    private static ValidationResult.ValidationError error(String path, String message, String code) {
        return new ValidationResult.ValidationError(path, message, code);
    }

    private static ValidationResult.ValidationWarning warning(String path, String message, String code) {
        return new ValidationResult.ValidationWarning(path, message, code);
    }
}
