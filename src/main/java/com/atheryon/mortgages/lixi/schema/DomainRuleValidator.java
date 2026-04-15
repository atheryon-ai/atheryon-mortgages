package com.atheryon.mortgages.lixi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier 4 — Domain rule validation from the mortgage origination SRS.
 * <p>
 * These are universal mortgage business rules (MR-001 to MR-010) that apply
 * regardless of lender. They enforce fundamental soundness of the application.
 */
@Service
public class DomainRuleValidator {

    private static final Logger log = LoggerFactory.getLogger(DomainRuleValidator.class);
    private static final String TIER = "DOMAIN";

    private static final double MIN_DEPOSIT_PERCENT = 5.0;
    private static final double MAX_LVR_PERCENT = 95.0;

    public ValidationResult validate(JsonNode lixiMessage) {
        long start = System.nanoTime();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        log.debug("Running Tier 4 domain rules");

        JsonNode application = lixiMessage.path("Package").path("Content").path("Application");
        if (application.isMissingNode()) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return ValidationResult.success(TIER, durationMs);
        }

        JsonNode loanDetails = application.path("LoanDetails");
        JsonNode applicants = application.path("PersonApplicant");
        JsonNode realEstate = application.path("RealEstateAsset");
        JsonNode financials = application.path("Financial");

        checkMR001(loanDetails, realEstate, errors);
        checkMR002(financials, errors);
        checkMR003(loanDetails, realEstate, errors);
        checkMR004(applicants, errors);
        checkMR005(applicants, errors);
        checkMR006(realEstate, errors);
        checkMR007(financials, errors);
        checkMR008(loanDetails, realEstate, errors);
        checkMR009(financials, errors);
        checkMR010(loanDetails, financials, warnings);

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (errors.isEmpty() && warnings.isEmpty()) {
            return ValidationResult.success(TIER, durationMs);
        }
        if (errors.isEmpty()) {
            return ValidationResult.withWarnings(TIER, warnings, durationMs);
        }
        return ValidationResult.failure(TIER, errors, warnings, durationMs);
    }

    /** MR-001: LVR must be calculable (loan amount / purchase price) */
    private void checkMR001(JsonNode loanDetails, JsonNode realEstate,
                            List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "LVR cannot be calculated — LoanAmount is missing or not numeric", "MR-001"));
            return;
        }

        double propertyValue = extractPropertyValue(realEstate);
        if (propertyValue <= 0) {
            errors.add(error("$.Package.Content.Application.RealEstateAsset",
                    "LVR cannot be calculated — property value (ContractPrice or EstimatedValue) is missing", "MR-001"));
        }
    }

    /** MR-002: Net surplus must be positive (income - expenses - liabilities > 0) */
    private void checkMR002(JsonNode financials, List<ValidationResult.ValidationError> errors) {
        double totalIncome = sumFinancialCategory(financials, "Income");
        double totalExpenses = sumFinancialCategory(financials, "Expense");
        double totalLiabilities = sumFinancialCategory(financials, "Liability");

        // Only check if we have financial data to work with
        if (totalIncome <= 0 && totalExpenses <= 0 && totalLiabilities <= 0) return;

        double netSurplus = totalIncome - totalExpenses - totalLiabilities;
        if (netSurplus <= 0) {
            errors.add(error("$.Package.Content.Application.Financial",
                    "Net surplus is negative or zero ($" + String.format("%,.0f", netSurplus)
                            + "): income ($" + String.format("%,.0f", totalIncome)
                            + ") - expenses ($" + String.format("%,.0f", totalExpenses)
                            + ") - liabilities ($" + String.format("%,.0f", totalLiabilities) + ")",
                    "MR-002"));
        }
    }

    /** MR-003: Deposit must be sufficient (purchase price - loan amount >= 5% of purchase price) */
    private void checkMR003(JsonNode loanDetails, JsonNode realEstate,
                            List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;
        double loan = loanAmount.doubleValue();

        double propertyValue = extractPropertyValue(realEstate);
        if (propertyValue <= 0) return;

        double deposit = propertyValue - loan;
        double minDeposit = propertyValue * (MIN_DEPOSIT_PERCENT / 100.0);

        if (deposit < minDeposit) {
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "Insufficient deposit: $" + String.format("%,.0f", deposit)
                            + " (minimum " + String.format("%.0f", MIN_DEPOSIT_PERCENT) + "% = $" + String.format("%,.0f", minDeposit) + ")",
                    "MR-003"));
        }
    }

    /** MR-004: All applicants must have privacy consent */
    private void checkMR004(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode applicant = applicants.get(i);
            boolean hasConsent = applicant.path("PrivacyConsent").asBoolean(false);
            if (!hasConsent) {
                // Also check nested Consent array
                JsonNode consents = applicant.path("Consent");
                boolean foundPrivacy = false;
                if (consents.isArray()) {
                    for (JsonNode consent : consents) {
                        if ("Privacy".equals(consent.path("Type").asText(""))
                                && consent.path("Granted").asBoolean(false)) {
                            foundPrivacy = true;
                            break;
                        }
                    }
                }
                if (!foundPrivacy) {
                    String name = applicant.path("PersonName").path("FirstName").asText("Applicant " + i);
                    errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].PrivacyConsent",
                            "Applicant '" + name + "' does not have privacy consent recorded", "MR-004"));
                }
            }
        }
    }

    /** MR-005: Primary applicant must have at least 1 form of ID */
    private void checkMR005(JsonNode applicants, List<ValidationResult.ValidationError> errors) {
        if (!applicants.isArray()) return;
        for (int i = 0; i < applicants.size(); i++) {
            JsonNode applicant = applicants.get(i);
            if (!applicant.path("@PrimaryApplicant").asBoolean(false)) continue;

            JsonNode identifications = applicant.path("Identification");
            if (!identifications.isArray() || identifications.isEmpty()) {
                errors.add(error("$.Package.Content.Application.PersonApplicant[" + i + "].Identification",
                        "Primary applicant must have at least one form of identification", "MR-005"));
            }
            return;
        }
    }

    /** MR-006: Property must have an address with suburb, state, postcode */
    private void checkMR006(JsonNode realEstate, List<ValidationResult.ValidationError> errors) {
        if (!realEstate.isArray() || realEstate.isEmpty()) {
            errors.add(error("$.Package.Content.Application.RealEstateAsset",
                    "At least one property (RealEstateAsset) is required", "MR-006"));
            return;
        }

        for (int i = 0; i < realEstate.size(); i++) {
            JsonNode address = realEstate.get(i).path("Address");
            String basePath = "$.Package.Content.Application.RealEstateAsset[" + i + "].Address";

            if (address.isMissingNode()) {
                errors.add(error(basePath, "Property must have an Address", "MR-006"));
                continue;
            }

            String suburb = address.path("Suburb").asText("");
            String state = address.path("State").asText("");
            String postcode = address.path("Postcode").asText("");

            List<String> missing = new ArrayList<>();
            if (suburb.isBlank()) missing.add("Suburb");
            if (state.isBlank()) missing.add("State");
            if (postcode.isBlank()) missing.add("Postcode");

            if (!missing.isEmpty()) {
                errors.add(error(basePath,
                        "Property address is incomplete — missing: " + String.join(", ", missing), "MR-006"));
            }
        }
    }

    /** MR-007: Total income must be > 0 */
    private void checkMR007(JsonNode financials, List<ValidationResult.ValidationError> errors) {
        double totalIncome = sumFinancialCategory(financials, "Income");
        if (totalIncome <= 0) {
            errors.add(error("$.Package.Content.Application.Financial",
                    "Total declared income must be greater than zero", "MR-007"));
        }
    }

    /** MR-008: Loan amount must not exceed 95% of property value */
    private void checkMR008(JsonNode loanDetails, JsonNode realEstate,
                            List<ValidationResult.ValidationError> errors) {
        JsonNode loanAmount = loanDetails.path("LoanAmount");
        if (!loanAmount.isNumber()) return;
        double loan = loanAmount.doubleValue();

        double propertyValue = extractPropertyValue(realEstate);
        if (propertyValue <= 0) return;

        double maxLoan = propertyValue * (MAX_LVR_PERCENT / 100.0);
        if (loan > maxLoan) {
            double lvr = (loan / propertyValue) * 100;
            errors.add(error("$.Package.Content.Application.LoanDetails.LoanAmount",
                    "Loan amount ($" + String.format("%,.0f", loan) + ") exceeds " + String.format("%.0f", MAX_LVR_PERCENT)
                            + "% of property value ($" + String.format("%,.0f", propertyValue) + ") — LVR is " + String.format("%.1f", lvr) + "%",
                    "MR-008"));
        }
    }

    /** MR-009: At least one asset declared (demonstrates borrower capacity) */
    private void checkMR009(JsonNode financials, List<ValidationResult.ValidationError> errors) {
        if (financials.isMissingNode()) {
            errors.add(error("$.Package.Content.Application.Financial",
                    "At least one asset must be declared to demonstrate borrower capacity", "MR-009"));
            return;
        }

        JsonNode assets = financials.path("Asset");
        if (!assets.isArray() || assets.isEmpty()) {
            // Also check if assets are at the top level
            int assetCount = 0;
            if (financials.isArray()) {
                for (JsonNode item : financials) {
                    if ("Asset".equals(item.path("Category").asText(""))) {
                        assetCount++;
                    }
                }
            }
            if (assetCount == 0) {
                errors.add(error("$.Package.Content.Application.Financial.Asset",
                        "At least one asset must be declared to demonstrate borrower capacity", "MR-009"));
            }
        }
    }

    /** MR-010: Repayment frequency must match income frequency (warning only) */
    private void checkMR010(JsonNode loanDetails, JsonNode financials,
                            List<ValidationResult.ValidationWarning> warnings) {
        String repaymentFrequency = loanDetails.path("RepaymentFrequency").asText("");
        if (repaymentFrequency.isBlank()) return;

        String incomeFrequency = extractPrimaryIncomeFrequency(financials);
        if (incomeFrequency.isBlank()) return;

        if (!repaymentFrequency.equalsIgnoreCase(incomeFrequency)) {
            warnings.add(warning("$.Package.Content.Application.LoanDetails.RepaymentFrequency",
                    "Repayment frequency (" + repaymentFrequency + ") does not match primary income frequency ("
                            + incomeFrequency + ") — this may cause cashflow issues",
                    "MR-010"));
        }
    }

    // --- Helpers ---

    private double extractPropertyValue(JsonNode realEstate) {
        if (!realEstate.isArray() || realEstate.isEmpty()) return 0;
        JsonNode contractPrice = realEstate.get(0).path("ContractPrice").path("Amount");
        if (contractPrice.isNumber()) return contractPrice.doubleValue();
        JsonNode estimatedValue = realEstate.get(0).path("EstimatedValue").path("Amount");
        if (estimatedValue.isNumber()) return estimatedValue.doubleValue();
        return 0;
    }

    private double sumFinancialCategory(JsonNode financials, String category) {
        if (financials.isMissingNode()) return 0;

        // Try nested structure: Financial.Income[], Financial.Expense[], etc.
        JsonNode categoryNode = financials.path(category);
        if (categoryNode.isArray()) {
            double total = 0;
            for (JsonNode item : categoryNode) {
                total += item.path("Amount").asDouble(0);
            }
            return total;
        }

        // Try flat array: Financial[] with Category field
        if (financials.isArray()) {
            double total = 0;
            for (JsonNode item : financials) {
                if (category.equals(item.path("Category").asText(""))) {
                    total += item.path("Amount").asDouble(0);
                }
            }
            return total;
        }

        return 0;
    }

    private String extractPrimaryIncomeFrequency(JsonNode financials) {
        if (financials.isMissingNode()) return "";

        JsonNode incomes = financials.path("Income");
        if (incomes.isArray() && !incomes.isEmpty()) {
            return incomes.get(0).path("Frequency").asText("");
        }

        if (financials.isArray()) {
            for (JsonNode item : financials) {
                if ("Income".equals(item.path("Category").asText(""))) {
                    return item.path("Frequency").asText("");
                }
            }
        }

        return "";
    }

    private static ValidationResult.ValidationError error(String path, String message, String code) {
        return new ValidationResult.ValidationError(path, message, code);
    }

    private static ValidationResult.ValidationWarning warning(String path, String message, String code) {
        return new ValidationResult.ValidationWarning(path, message, code);
    }
}
