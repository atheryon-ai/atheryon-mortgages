package com.atheryon.mortgages.migration.pipeline;

import com.atheryon.mortgages.migration.entity.MigrationFieldMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Stage 2 — Map source columns to target domain fields.
 * Uses exact match, synonym lookup, and fuzzy match (Levenshtein).
 */
@Service
public class ColumnAutoMapper {

    private static final Logger log = LoggerFactory.getLogger(ColumnAutoMapper.class);

    // Target domain fields
    private static final Set<String> TARGET_FIELDS = Set.of(
            "loanRef", "borrowerFirstName", "borrowerLastName", "borrowerDob",
            "borrowerEmail", "borrowerPhone", "coBorrowerFirstName", "coBorrowerLastName",
            "addressStreetNumber", "addressStreet", "addressSuburb", "addressState",
            "addressPostcode", "propertyType", "purchasePrice", "loanAmount",
            "currentBalance", "interestRate", "interestType", "loanTermMonths",
            "originationDate", "settlementDate", "loanStatus", "repaymentFrequency",
            "repaymentAmount", "lvr", "lmiFlag", "lmiPremium", "employerName",
            "employmentType", "incomeGrossAnnual", "incomeNetMonthly", "expensesMonthly",
            "creditCardBalance", "otherLiabilities", "propertyValueEstimate",
            "valuationDate", "insuranceProvider", "brokerName", "brokerCode",
            "branchCode", "internalScore", "arrearsDays", "arrearsAmount",
            "lastPaymentDate", "lastPaymentAmount", "offsetBalance", "redrawBalance",
            "offsetAccountNumber", "redrawEnabled", "interestOnlyFlag", "ioExpiryDate",
            "fixedRateExpiry", "discountMargin", "productCode", "productName",
            "channel", "purpose", "securityType", "titleReference", "councilArea",
            "zoning", "bedrooms", "bathrooms", "landAreaSqm", "floorAreaSqm",
            "yearBuilt", "strataFlag", "strataLevy", "bodyCorpName", "guarantorFlag",
            "guarantorName", "guarantorRelationship", "feePackage", "annualFee",
            "applicationId", "loanGroupId", "settlementAgent", "solicitorName",
            "dischargeDate", "dischargeReason", "notes", "lastUpdated"
    );

    // Synonym dictionary: normalised source name -> target field
    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        // Loan reference
        syn("loanRef", "loan_acct_num", "loan_reference", "reference_number", "account_number",
                "loan_account", "loan_no", "loan_id", "account_no", "acct_num", "loan_number",
                "facility_id", "facility_number");

        // Borrower name
        syn("borrowerFirstName", "borrower_first_name", "first_name", "given_name",
                "applicant_first_name", "client_first_name");
        syn("borrowerLastName", "borrower_last_name", "last_name", "surname", "family_name",
                "applicant_last_name", "client_last_name", "applicant_surname");

        // Borrower details
        syn("borrowerDob", "borrower_dob", "date_of_birth", "dob", "birth_date",
                "applicant_dob", "borrower_date_of_birth");
        syn("borrowerEmail", "borrower_email", "email", "email_address",
                "applicant_email", "client_email", "contact_email");
        syn("borrowerPhone", "borrower_phone", "phone", "phone_number", "mobile",
                "mobile_number", "contact_phone", "applicant_phone", "telephone");

        // Co-borrower
        syn("coBorrowerFirstName", "co_borrower_first_name", "coborrower_first_name",
                "joint_applicant_first_name", "second_applicant_first");
        syn("coBorrowerLastName", "co_borrower_last_name", "coborrower_last_name",
                "joint_applicant_last_name", "second_applicant_last");

        // Address
        syn("addressStreetNumber", "prop_street_num", "street_number", "unit_number",
                "property_number", "house_number");
        syn("addressStreet", "prop_street", "street_name", "street_address",
                "property_street", "address_line_1");
        syn("addressSuburb", "prop_suburb", "suburb", "locality", "city", "town",
                "property_suburb");
        syn("addressState", "prop_state", "state", "state_territory", "property_state");
        syn("addressPostcode", "prop_postcode", "postcode", "post_code", "zip",
                "property_postcode");

        // Property
        syn("propertyType", "prop_type", "property_type", "dwelling_type",
                "security_property_type");
        syn("purchasePrice", "purchase_price", "property_value", "valuation",
                "security_value", "contract_price", "sale_price");
        syn("propertyValueEstimate", "property_value_est", "estimated_value",
                "current_valuation", "market_value");

        // Loan financials
        syn("loanAmount", "orig_amount", "loan_amount", "original_amount",
                "principal_amount", "facility_amount", "advance_amount",
                "approved_amount", "drawn_amount");
        syn("currentBalance", "current_balance", "outstanding_balance",
                "balance", "remaining_balance", "principal_outstanding");
        syn("interestRate", "current_rate", "interest_rate", "rate",
                "annual_rate", "nominal_rate");
        syn("interestType", "rate_type", "interest_type", "rate_structure");
        syn("loanTermMonths", "loan_term_months", "term_months", "loan_term",
                "term", "facility_term");

        // Dates
        syn("originationDate", "origination_date", "orig_date", "settlement_date_orig",
                "start_date", "commencement_date", "drawdown_date");
        syn("settlementDate", "settlement_date", "settle_date", "completion_date");
        syn("valuationDate", "valuation_date", "val_date", "appraisal_date");
        syn("lastPaymentDate", "last_payment_date", "last_repayment_date");
        syn("dischargeDate", "discharge_date", "close_date", "termination_date");

        // Status
        syn("loanStatus", "status", "loan_status", "account_status", "facility_status");

        // Repayment
        syn("repaymentFrequency", "repay_frequency", "repayment_frequency",
                "payment_frequency", "frequency");
        syn("repaymentAmount", "repay_amount", "repayment_amount", "payment_amount",
                "instalment_amount");

        // LVR / LMI
        syn("lvr", "lvr", "loan_to_value", "ltv", "loan_to_value_ratio");
        syn("lmiFlag", "lmi_flag", "lmi_required", "lmi_indicator");
        syn("lmiPremium", "lmi_premium", "lmi_amount", "lmi_cost");

        // Employment / income
        syn("employerName", "employer_name", "employer", "company_name");
        syn("employmentType", "employment_type", "employment_status", "emp_type");
        syn("incomeGrossAnnual", "gross_annual_income", "annual_income", "gross_income",
                "total_income", "income_gross_annual");
        syn("incomeNetMonthly", "net_monthly_income", "monthly_income", "take_home_pay",
                "income_net_monthly");

        // Expenses / liabilities
        syn("expensesMonthly", "monthly_expenses", "total_expenses", "living_expenses",
                "hem_expenses");
        syn("creditCardBalance", "credit_card_balance", "cc_balance", "card_balance");
        syn("otherLiabilities", "other_liabilities", "other_debts", "total_liabilities");

        // Broker
        syn("brokerName", "broker_name", "broker", "introducer_name");
        syn("brokerCode", "broker_code", "broker_id", "introducer_code");

        // Misc
        syn("branchCode", "branch_code", "branch_id", "branch");
        syn("internalScore", "internal_score", "risk_score", "credit_score");
        syn("insuranceProvider", "insurance_provider", "insurer", "home_insurance");
        syn("arrearsDays", "arrears_days", "days_past_due", "dpd");
        syn("arrearsAmount", "arrears_amount", "overdue_amount");
        syn("lastPaymentAmount", "last_payment_amount", "last_repayment_amount");
        syn("offsetBalance", "offset_balance", "offset_amount");
        syn("redrawBalance", "redraw_balance", "redraw_available");
        syn("offsetAccountNumber", "offset_acct_num", "offset_account");
        syn("redrawEnabled", "redraw_enabled", "redraw_facility");
        syn("interestOnlyFlag", "interest_only_flag", "io_flag", "interest_only");
        syn("ioExpiryDate", "io_expiry_date", "io_end_date");
        syn("fixedRateExpiry", "fixed_rate_expiry", "fixed_expiry", "fixed_period_end");
        syn("discountMargin", "discount_margin", "margin", "rate_discount");
        syn("productCode", "product_code", "prod_code");
        syn("productName", "product_name", "prod_name", "product_description");
        syn("channel", "channel", "origination_channel", "source_channel");
        syn("purpose", "purpose", "loan_purpose", "facility_purpose");
        syn("securityType", "security_type", "mortgage_type", "security_class");
        syn("titleReference", "title_ref", "title_reference", "ct_number", "folio");
        syn("councilArea", "council_area", "lga", "local_government_area");
        syn("zoning", "zoning", "zone", "land_use");
        syn("bedrooms", "num_bedrooms", "bedrooms", "beds");
        syn("bathrooms", "num_bathrooms", "bathrooms", "baths");
        syn("landAreaSqm", "land_area_sqm", "land_size", "lot_size");
        syn("floorAreaSqm", "floor_area_sqm", "floor_size", "building_area");
        syn("yearBuilt", "year_built", "construction_year", "built_year");
        syn("strataFlag", "strata_flag", "strata", "body_corp_flag");
        syn("strataLevy", "strata_levy", "body_corp_levy", "strata_fees");
        syn("bodyCorpName", "body_corp_name", "strata_plan", "owners_corp");
        syn("guarantorFlag", "guarantor_flag", "has_guarantor", "guarantor_indicator");
        syn("guarantorName", "guarantor_name", "guarantor");
        syn("guarantorRelationship", "guarantor_relationship", "guarantor_relation");
        syn("feePackage", "fee_package", "package_type", "pricing_package");
        syn("annualFee", "annual_fee", "package_fee", "yearly_fee");
        syn("applicationId", "application_id", "app_id", "application_number");
        syn("loanGroupId", "loan_group_id", "group_id", "facility_group");
        syn("settlementAgent", "settlement_agent", "conveyancer");
        syn("solicitorName", "solicitor_name", "solicitor", "lawyer");
        syn("dischargeReason", "discharge_reason", "close_reason", "termination_reason");
        syn("notes", "notes", "comments", "remarks", "memo");
        syn("lastUpdated", "last_updated", "updated_at", "last_modified",
                "modification_date");
    }

    private static void syn(String target, String... sources) {
        for (String source : sources) {
            SYNONYMS.put(normalise(source), target);
        }
    }

    /**
     * Map source column names to target domain fields.
     */
    public List<MigrationFieldMapping> mapColumns(UUID jobId, List<String> sourceColumns) {
        List<MigrationFieldMapping> mappings = new ArrayList<>();

        for (String sourceColumn : sourceColumns) {
            String normalised = normalise(sourceColumn);

            // Strategy 1: Exact match against target fields
            String match = findExactMatch(normalised);
            if (match != null) {
                mappings.add(buildMapping(jobId, sourceColumn, match, new BigDecimal("1.00"), "EXACT"));
                continue;
            }

            // Strategy 2: Synonym lookup
            match = SYNONYMS.get(normalised);
            if (match != null) {
                mappings.add(buildMapping(jobId, sourceColumn, match, new BigDecimal("0.90"), "SYNONYM"));
                continue;
            }

            // Strategy 3: Fuzzy match (Levenshtein distance <= 2)
            match = findFuzzyMatch(normalised);
            if (match != null) {
                mappings.add(buildMapping(jobId, sourceColumn, match, new BigDecimal("0.70"), "FUZZY"));
                continue;
            }

            // No match found
            mappings.add(MigrationFieldMapping.builder()
                    .jobId(jobId)
                    .sourceColumn(sourceColumn)
                    .targetPath(null)
                    .confidence(BigDecimal.ZERO)
                    .status("SUGGESTED")
                    .build());
        }

        long mapped = mappings.stream().filter(m -> m.getTargetPath() != null).count();
        log.info("Auto-mapped {}/{} columns (exact + synonym + fuzzy)", mapped, sourceColumns.size());

        return mappings;
    }

    private MigrationFieldMapping buildMapping(UUID jobId, String sourceColumn,
                                                String targetField, BigDecimal confidence,
                                                String transformType) {
        return MigrationFieldMapping.builder()
                .jobId(jobId)
                .sourceColumn(sourceColumn)
                .targetPath(targetField)
                .confidence(confidence)
                .status("SUGGESTED")
                .transformType(transformType)
                .build();
    }

    /**
     * Normalise a column name: lowercase, strip non-alphanumeric, collapse underscores.
     */
    static String normalise(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * Check if normalised name matches a target field (case-insensitive camelCase comparison).
     */
    private String findExactMatch(String normalised) {
        for (String target : TARGET_FIELDS) {
            if (normalise(camelToSnake(target)).equals(normalised)) {
                return target;
            }
        }
        return null;
    }

    /**
     * Find the closest target field via Levenshtein distance on snake_case forms.
     */
    private String findFuzzyMatch(String normalised) {
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        // Check against all target fields (as snake_case)
        for (String target : TARGET_FIELDS) {
            String targetSnake = normalise(camelToSnake(target));
            int distance = levenshtein(normalised, targetSnake);
            if (distance <= 2 && distance < bestDistance) {
                bestDistance = distance;
                bestMatch = target;
            }
        }

        // Also check against all synonym keys
        if (bestMatch == null) {
            for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
                int distance = levenshtein(normalised, entry.getKey());
                if (distance <= 2 && distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = entry.getValue();
                }
            }
        }

        return bestMatch;
    }

    private static String camelToSnake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Standard Levenshtein distance.
     */
    static int levenshtein(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) dp[i][0] = i;
        for (int j = 0; j <= lenB; j++) dp[0][j] = j;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[lenA][lenB];
    }
}
