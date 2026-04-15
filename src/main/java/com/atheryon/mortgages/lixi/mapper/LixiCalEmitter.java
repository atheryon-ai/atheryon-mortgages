package com.atheryon.mortgages.lixi.mapper;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reverse mapper — emits a LIXI2 CAL JSON message from domain entities.
 * Used for outbound messages to brokers/aggregators.
 */
@Service
public class LixiCalEmitter {

    private final ObjectMapper objectMapper;

    // ── Domain → LIXI enum reverse maps ───────────────────────────────

    private static final Map<LoanPurpose, String> LOAN_PURPOSE_REVERSE = Map.of(
            LoanPurpose.PURCHASE, "OWNER_OCCUPIED_PURCHASE",
            LoanPurpose.REFINANCE, "REFINANCE",
            LoanPurpose.CONSTRUCTION, "CONSTRUCTION",
            LoanPurpose.EQUITY_RELEASE, "EQUITY_RELEASE"
    );

    private static final Map<InterestType, String> INTEREST_TYPE_REVERSE = Map.of(
            InterestType.VARIABLE, "Variable",
            InterestType.FIXED, "Fixed",
            InterestType.SPLIT, "Split"
    );

    private static final Map<RepaymentType, String> REPAYMENT_TYPE_REVERSE = Map.of(
            RepaymentType.PRINCIPAL_AND_INTEREST, "PrincipalAndInterest",
            RepaymentType.INTEREST_ONLY, "InterestOnly"
    );

    private static final Map<RepaymentFrequency, String> REPAYMENT_FREQ_REVERSE = Map.of(
            RepaymentFrequency.WEEKLY, "Weekly",
            RepaymentFrequency.FORTNIGHTLY, "Fortnightly",
            RepaymentFrequency.MONTHLY, "Monthly"
    );

    private static final Map<PropertyCategory, String> PROPERTY_TYPE_REVERSE = Map.of(
            PropertyCategory.HOUSE, "House",
            PropertyCategory.UNIT, "Unit",
            PropertyCategory.TOWNHOUSE, "Townhouse",
            PropertyCategory.APARTMENT, "Apartment",
            PropertyCategory.VACANT_LAND, "VacantLand",
            PropertyCategory.RURAL, "Rural"
    );

    private static final Map<EmploymentType, String> EMPLOYMENT_TYPE_REVERSE = Map.of(
            EmploymentType.PAYG_FULL_TIME, "PAYG",
            EmploymentType.PAYG_PART_TIME, "PAYG_PartTime",
            EmploymentType.PAYG_CASUAL, "PAYG_Casual",
            EmploymentType.SELF_EMPLOYED, "SelfEmployed",
            EmploymentType.CONTRACT, "Contract",
            EmploymentType.RETIRED, "Retired",
            EmploymentType.HOME_DUTIES, "HomeDuties",
            EmploymentType.STUDENT, "Student",
            EmploymentType.UNEMPLOYED, "Unemployed"
    );

    private static final Map<ValuationType, String> VALUATION_TYPE_REVERSE = Map.of(
            ValuationType.AUTOMATED_AVM, "AVM",
            ValuationType.DESKTOP, "Desktop",
            ValuationType.KERBSIDE, "Kerbside",
            ValuationType.SHORT_FORM, "ShortForm",
            ValuationType.FULL_INTERNAL, "FullInternal",
            ValuationType.FULL_EXTERNAL, "FullExternal"
    );

    private static final Map<HousingStatus, String> HOUSING_STATUS_REVERSE = Map.of(
            HousingStatus.RENTING, "Renting",
            HousingStatus.OWN_HOME_WITH_MORTGAGE, "OwnWithMortgage",
            HousingStatus.OWN_HOME_NO_MORTGAGE, "OwnNoMortgage",
            HousingStatus.BOARDING, "Boarding",
            HousingStatus.LIVING_WITH_PARENTS, "LivingWithParents"
    );

    private static final Map<AssetType, String> ASSET_TYPE_REVERSE = Map.of(
            AssetType.REAL_ESTATE, "RealEstate",
            AssetType.SAVINGS, "SavingsAccount",
            AssetType.SHARES, "Shares",
            AssetType.SUPERANNUATION, "Superannuation",
            AssetType.VEHICLE, "Vehicle",
            AssetType.OTHER, "Other"
    );

    private static final Map<LiabilityType, String> LIABILITY_TYPE_REVERSE = Map.of(
            LiabilityType.HOME_LOAN, "HomeLoan",
            LiabilityType.PERSONAL_LOAN, "PersonalLoan",
            LiabilityType.CAR_LOAN, "CarLoan",
            LiabilityType.CREDIT_CARD, "CreditCard",
            LiabilityType.HECS_HELP, "HECS",
            LiabilityType.TAX_DEBT, "TaxDebt",
            LiabilityType.OTHER, "Other"
    );

    public LixiCalEmitter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Main entry point ──────────────────────────────────────────────

    public JsonNode emitCal(LoanApplication app, List<MappingResult.PartyWithRole> parties,
                            List<PropertySecurity> securities, FinancialSnapshot financials) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode pkg = root.putObject("Package");

        if (app.getApplicationNumber() != null) {
            pkg.put("@UniqueID", "PKG-" + app.getApplicationNumber());
        }
        pkg.put("@ProductionData", true);

        ObjectNode content = pkg.putObject("Content");
        ObjectNode application = content.putObject("Application");

        if (app.getApplicationNumber() != null) {
            application.put("@UniqueID", app.getApplicationNumber());
        }

        emitOverview(application, app);
        emitApplicants(application, parties);
        emitSecurities(application, securities);
        emitLoanDetails(application, app);

        if (financials != null) {
            emitExpenses(application, financials.getExpenseItems());
            emitLiabilities(application, financials.getLiabilities());
            emitAssets(application, financials.getAssets());
        }

        emitPublisher(pkg, app);

        ObjectNode recipient = pkg.putObject("Recipient");
        recipient.put("CompanyName", "Atheryon Labs");
        recipient.put("Description", "Mortgage Platform");

        pkg.put("SchemaVersion", "2.6.91");

        return root;
    }

    // ── Section emitters ──────────────────────────────────────────────

    private void emitOverview(ObjectNode application, LoanApplication app) {
        ObjectNode overview = application.putObject("Overview");
        if (app.getPurpose() != null) {
            // Use occupancy to determine purchase subtype
            if (app.getPurpose() == LoanPurpose.PURCHASE
                    && app.getOccupancyType() == OccupancyType.INVESTMENT) {
                overview.put("LoanPurpose", "INVESTMENT_PURCHASE");
            } else {
                overview.put("LoanPurpose",
                        LOAN_PURPOSE_REVERSE.getOrDefault(app.getPurpose(), "OWNER_OCCUPIED_PURCHASE"));
            }
        }
    }

    private void emitApplicants(ObjectNode application, List<MappingResult.PartyWithRole> parties) {
        if (parties == null || parties.isEmpty()) return;

        ArrayNode applicants = application.putArray("PersonApplicant");
        for (MappingResult.PartyWithRole pwr : parties) {
            ObjectNode applicant = applicants.addObject();
            Party party = pwr.party();

            applicant.put("@PrimaryApplicant", pwr.role() == PartyRole.PRIMARY_BORROWER);

            ObjectNode personName = applicant.putObject("PersonName");
            putIfNotNull(personName, "FirstName", party.getFirstName());
            putIfNotNull(personName, "MiddleName", party.getMiddleNames());
            putIfNotNull(personName, "Surname", party.getSurname());

            if (party.getDateOfBirth() != null) {
                applicant.put("DateOfBirth", party.getDateOfBirth().toString());
            }
            putIfNotNull(applicant, "Gender", party.getGender());
            putIfNotNull(applicant, "Email", party.getEmail());

            if (party.getMobilePhone() != null) {
                ObjectNode phone = applicant.putObject("Phone");
                phone.put("Mobile", party.getMobilePhone());
            }

            emitAddresses(applicant, pwr.addresses());
            emitEmployments(applicant, pwr.employments());
            emitIdentifications(applicant, pwr.identifications());

            // Privacy/consent — emit a simple block
            ObjectNode privacy = applicant.putObject("Privacy");
            privacy.put("ConsentObtained", true);
            privacy.put("ConsentDate", LocalDate.now().toString());
        }
    }

    private void emitAddresses(ObjectNode applicant, List<PartyAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) return;

        ArrayNode addrArray = applicant.putArray("Address");
        for (PartyAddress addr : addresses) {
            ObjectNode addrNode = addrArray.addObject();
            putIfNotNull(addrNode, "@Type", addr.getAddressType());
            if (addr.getHousingStatus() != null) {
                addrNode.put("@Housing",
                        HOUSING_STATUS_REVERSE.getOrDefault(addr.getHousingStatus(), "Renting"));
            }
            putIfNotNull(addrNode, "StreetNumber", addr.getStreetNumber());
            putIfNotNull(addrNode, "Street", addr.getStreetName());
            putIfNotNull(addrNode, "Suburb", addr.getSuburb());
            putIfNotNull(addrNode, "State", addr.getState());
            putIfNotNull(addrNode, "Postcode", addr.getPostcode());
            addrNode.put("Country", addr.getCountry() != null ? addr.getCountry() : "AU");
            addrNode.put("YearsAtAddress", addr.getYearsAtAddress());
        }
    }

    private void emitEmployments(ObjectNode applicant, List<Employment> employments) {
        if (employments == null || employments.isEmpty()) return;

        ArrayNode empArray = applicant.putArray("Employment");
        for (Employment emp : employments) {
            ObjectNode empNode = empArray.addObject();
            if (emp.getEmploymentType() != null) {
                empNode.put("@Type",
                        EMPLOYMENT_TYPE_REVERSE.getOrDefault(emp.getEmploymentType(), "PAYG"));
            }
            putIfNotNull(empNode, "Employer", emp.getEmployerName());
            putIfNotNull(empNode, "Role", emp.getOccupation());
            if (emp.getStartDate() != null) {
                empNode.put("StartDate", emp.getStartDate().toString());
            }
            if (emp.getAnnualBaseSalary() != null) {
                ObjectNode income = empNode.putObject("Income");
                income.put("GrossAnnual", emp.getAnnualBaseSalary());
                income.put("PayFrequency", "Monthly");
            }
        }
    }

    private void emitIdentifications(ObjectNode applicant, List<PartyIdentification> identifications) {
        if (identifications == null || identifications.isEmpty()) return;

        ArrayNode idArray = applicant.putArray("Identification");
        for (PartyIdentification id : identifications) {
            ObjectNode idNode = idArray.addObject();
            putIfNotNull(idNode, "Type", id.getIdType());
            putIfNotNull(idNode, "Number", id.getIdNumber());
            putIfNotNull(idNode, "State", id.getIssuingState());
            if (id.getExpiryDate() != null) {
                idNode.put("ExpiryDate", id.getExpiryDate().toString());
            }
        }
    }

    private void emitSecurities(ObjectNode application, List<PropertySecurity> securities) {
        if (securities == null || securities.isEmpty()) return;

        ArrayNode assetArray = application.putArray("RealEstateAsset");
        for (PropertySecurity sec : securities) {
            ObjectNode asset = assetArray.addObject();
            asset.put("@Transaction", "Purchasing");

            if (sec.getPurchasePrice() != null) {
                ObjectNode contractPrice = asset.putObject("ContractPrice");
                contractPrice.put("Amount", sec.getPurchasePrice());
                contractPrice.put("Currency", "AUD");
            }

            ObjectNode addr = asset.putObject("Address");
            putIfNotNull(addr, "StreetNumber", sec.getStreetNumber());
            putIfNotNull(addr, "Street", sec.getStreetName());
            putIfNotNull(addr, "Suburb", sec.getSuburb());
            putIfNotNull(addr, "State", sec.getState());
            putIfNotNull(addr, "Postcode", sec.getPostcode());
            addr.put("Country", "AU");

            if (sec.getPropertyCategory() != null) {
                asset.put("PropertyType",
                        PROPERTY_TYPE_REVERSE.getOrDefault(sec.getPropertyCategory(), "House"));
            }
            if (sec.getNumberOfBedrooms() != null) asset.put("Bedrooms", sec.getNumberOfBedrooms());
            if (sec.getLandAreaSqm() != null) asset.put("LandArea", sec.getLandAreaSqm());
            if (sec.getYearBuilt() != null) asset.put("YearBuilt", sec.getYearBuilt());

            if (sec.getValuation() != null) {
                emitValuation(asset, sec.getValuation());
            }
        }
    }

    private void emitValuation(ObjectNode asset, Valuation val) {
        ObjectNode valNode = asset.putObject("Valuation");
        if (val.getEstimatedValue() != null) valNode.put("EstimatedValue", val.getEstimatedValue());
        if (val.getCompletedDate() != null) valNode.put("ValuationDate", val.getCompletedDate().toString());
        if (val.getValuationType() != null) {
            valNode.put("ValuationType",
                    VALUATION_TYPE_REVERSE.getOrDefault(val.getValuationType(), "Desktop"));
        }
        putIfNotNull(valNode, "Valuer", val.getProvider());
    }

    private void emitLoanDetails(ObjectNode application, LoanApplication app) {
        ObjectNode loanDetails = application.putObject("LoanDetails");
        if (app.getRequestedAmount() != null) loanDetails.put("LoanAmount", app.getRequestedAmount());
        loanDetails.put("Term", app.getTermMonths());
        loanDetails.put("TermUnit", "Months");
        if (app.getInterestType() != null) {
            loanDetails.put("InterestType",
                    INTEREST_TYPE_REVERSE.getOrDefault(app.getInterestType(), "Variable"));
        }
        if (app.getRepaymentType() != null) {
            loanDetails.put("RepaymentType",
                    REPAYMENT_TYPE_REVERSE.getOrDefault(app.getRepaymentType(), "PrincipalAndInterest"));
        }
        if (app.getRepaymentFrequency() != null) {
            loanDetails.put("RepaymentFrequency",
                    REPAYMENT_FREQ_REVERSE.getOrDefault(app.getRepaymentFrequency(), "Monthly"));
        }
    }

    private void emitExpenses(ObjectNode application, List<ExpenseItem> expenses) {
        if (expenses == null || expenses.isEmpty()) return;

        ArrayNode expArray = application.putArray("Expense");
        for (ExpenseItem exp : expenses) {
            ObjectNode expNode = expArray.addObject();
            putIfNotNull(expNode, "Type", exp.getCategory());
            if (exp.getMonthlyAmount() != null) expNode.put("MonthlyAmount", exp.getMonthlyAmount());
        }
    }

    private void emitLiabilities(ObjectNode application, List<Liability> liabilities) {
        if (liabilities == null || liabilities.isEmpty()) return;

        ArrayNode liabArray = application.putArray("Liability");
        for (Liability liab : liabilities) {
            ObjectNode liabNode = liabArray.addObject();
            if (liab.getLiabilityType() != null) {
                liabNode.put("Type",
                        LIABILITY_TYPE_REVERSE.getOrDefault(liab.getLiabilityType(), "Other"));
            }
            if (liab.getOutstandingBalance() != null) liabNode.put("Balance", liab.getOutstandingBalance());
            if (liab.getCreditLimit() != null) liabNode.put("Limit", liab.getCreditLimit());
            if (liab.getMonthlyRepayment() != null) liabNode.put("MonthlyPayment", liab.getMonthlyRepayment());
            putIfNotNull(liabNode, "Institution", liab.getLender());
        }
    }

    private void emitAssets(ObjectNode application, List<Asset> assets) {
        if (assets == null || assets.isEmpty()) return;

        ArrayNode assetArray = application.putArray("Asset");
        for (Asset asset : assets) {
            ObjectNode assetNode = assetArray.addObject();
            if (asset.getAssetType() != null) {
                assetNode.put("Type",
                        ASSET_TYPE_REVERSE.getOrDefault(asset.getAssetType(), "Other"));
            }
            if (asset.getEstimatedValue() != null) assetNode.put("Value", asset.getEstimatedValue());
            putIfNotNull(assetNode, "Description", asset.getDescription());
        }
    }

    private void emitPublisher(ObjectNode pkg, LoanApplication app) {
        ObjectNode publisher = pkg.putObject("Publisher");
        publisher.put("CompanyName", "Atheryon Labs");
        publisher.put("Description", "Mortgage Platform");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void putIfNotNull(ObjectNode node, String field, String value) {
        if (value != null) node.put(field, value);
    }
}
