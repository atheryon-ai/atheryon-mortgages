package com.atheryon.mortgages.lixi.mapper;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Maps a LIXI2 CAL (Credit Application Lodgement) JSON message
 * into canonical domain entities for persistence.
 */
@Service
public class LixiCalMapper {

    private static final Logger log = LoggerFactory.getLogger(LixiCalMapper.class);

    // ── LIXI → Domain enum lookup maps ────────────────────────────────

    private static final Map<String, LoanPurpose> LOAN_PURPOSE_MAP = Map.of(
            "OWNER_OCCUPIED_PURCHASE", LoanPurpose.PURCHASE,
            "INVESTMENT_PURCHASE", LoanPurpose.PURCHASE,
            "REFINANCE", LoanPurpose.REFINANCE,
            "REFINANCE_EXTERNAL", LoanPurpose.REFINANCE,
            "CONSTRUCTION", LoanPurpose.CONSTRUCTION,
            "EQUITY_RELEASE", LoanPurpose.EQUITY_RELEASE
    );

    private static final Map<String, OccupancyType> OCCUPANCY_MAP = Map.of(
            "OWNER_OCCUPIED_PURCHASE", OccupancyType.OWNER_OCCUPIED,
            "INVESTMENT_PURCHASE", OccupancyType.INVESTMENT
    );

    private static final Map<String, InterestType> INTEREST_TYPE_MAP = Map.of(
            "Variable", InterestType.VARIABLE,
            "Fixed", InterestType.FIXED,
            "Split", InterestType.SPLIT
    );

    private static final Map<String, RepaymentType> REPAYMENT_TYPE_MAP = Map.of(
            "PrincipalAndInterest", RepaymentType.PRINCIPAL_AND_INTEREST,
            "InterestOnly", RepaymentType.INTEREST_ONLY
    );

    private static final Map<String, RepaymentFrequency> REPAYMENT_FREQ_MAP = Map.of(
            "Weekly", RepaymentFrequency.WEEKLY,
            "Fortnightly", RepaymentFrequency.FORTNIGHTLY,
            "Monthly", RepaymentFrequency.MONTHLY
    );

    private static final Map<String, PropertyCategory> PROPERTY_TYPE_MAP = Map.of(
            "House", PropertyCategory.HOUSE,
            "Unit", PropertyCategory.UNIT,
            "Townhouse", PropertyCategory.TOWNHOUSE,
            "Apartment", PropertyCategory.APARTMENT,
            "VacantLand", PropertyCategory.VACANT_LAND,
            "Rural", PropertyCategory.RURAL
    );

    private static final Map<String, SecurityType> SECURITY_TYPE_MAP = Map.of(
            "House", SecurityType.EXISTING_RESIDENTIAL,
            "Unit", SecurityType.STRATA_UNIT,
            "Townhouse", SecurityType.EXISTING_RESIDENTIAL,
            "Apartment", SecurityType.STRATA_UNIT,
            "VacantLand", SecurityType.VACANT_LAND,
            "Rural", SecurityType.RURAL_RESIDENTIAL
    );

    private static final Map<String, EmploymentType> EMPLOYMENT_TYPE_MAP = Map.of(
            "PAYG", EmploymentType.PAYG_FULL_TIME,
            "PAYG_FullTime", EmploymentType.PAYG_FULL_TIME,
            "PAYG_PartTime", EmploymentType.PAYG_PART_TIME,
            "PAYG_Casual", EmploymentType.PAYG_CASUAL,
            "SelfEmployed", EmploymentType.SELF_EMPLOYED,
            "Contract", EmploymentType.CONTRACT,
            "Retired", EmploymentType.RETIRED,
            "HomeDuties", EmploymentType.HOME_DUTIES,
            "Student", EmploymentType.STUDENT,
            "Unemployed", EmploymentType.UNEMPLOYED
    );

    private static final Map<String, ValuationType> VALUATION_TYPE_MAP = Map.of(
            "AVM", ValuationType.AUTOMATED_AVM,
            "Desktop", ValuationType.DESKTOP,
            "Kerbside", ValuationType.KERBSIDE,
            "ShortForm", ValuationType.SHORT_FORM,
            "FullInternal", ValuationType.FULL_INTERNAL,
            "FullExternal", ValuationType.FULL_EXTERNAL
    );

    private static final Map<String, HousingStatus> HOUSING_STATUS_MAP = Map.of(
            "Renting", HousingStatus.RENTING,
            "OwnWithMortgage", HousingStatus.OWN_HOME_WITH_MORTGAGE,
            "OwnNoMortgage", HousingStatus.OWN_HOME_NO_MORTGAGE,
            "Boarding", HousingStatus.BOARDING,
            "LivingWithParents", HousingStatus.LIVING_WITH_PARENTS
    );

    private static final Map<String, IncomeType> INCOME_TYPE_MAP = Map.ofEntries(
            Map.entry("RentalIncome", IncomeType.RENTAL),
            Map.entry("GovernmentBenefit", IncomeType.GOVERNMENT_BENEFIT),
            Map.entry("Superannuation", IncomeType.SUPERANNUATION),
            Map.entry("Dividend", IncomeType.DIVIDEND),
            Map.entry("SelfEmployedIncome", IncomeType.SELF_EMPLOYED_INCOME),
            Map.entry("Other", IncomeType.OTHER)
    );

    private static final Map<String, AssetType> ASSET_TYPE_MAP = Map.of(
            "RealEstate", AssetType.REAL_ESTATE,
            "SavingsAccount", AssetType.SAVINGS,
            "Shares", AssetType.SHARES,
            "Superannuation", AssetType.SUPERANNUATION,
            "Vehicle", AssetType.VEHICLE,
            "Other", AssetType.OTHER
    );

    private static final Map<String, LiabilityType> LIABILITY_TYPE_MAP = Map.of(
            "HomeLoan", LiabilityType.HOME_LOAN,
            "PersonalLoan", LiabilityType.PERSONAL_LOAN,
            "CarLoan", LiabilityType.CAR_LOAN,
            "CreditCard", LiabilityType.CREDIT_CARD,
            "HECS", LiabilityType.HECS_HELP,
            "TaxDebt", LiabilityType.TAX_DEBT,
            "Other", LiabilityType.OTHER
    );

    // ── Main entry point ──────────────────────────────────────────────

    public MappingResult mapFromCal(JsonNode calMessage) {
        List<MappingResult.MappingWarning> warnings = new ArrayList<>();

        JsonNode pkg = calMessage.path("Package");
        JsonNode content = pkg.path("Content");
        JsonNode application = content.path("Application");
        JsonNode overview = application.path("Overview");
        JsonNode loanDetails = application.path("LoanDetails");

        LoanApplication loanApp = mapApplication(application, loanDetails, overview, warnings);
        List<MappingResult.PartyWithRole> parties = mapApplicants(application.path("PersonApplicant"), warnings);
        List<PropertySecurity> securities = mapSecurities(application.path("RealEstateAsset"), warnings);
        FinancialSnapshot financials = mapFinancials(application, parties, warnings);
        List<ConsentRecord> consents = mapAllConsents(application.path("PersonApplicant"), warnings);
        BrokerDetail broker = mapBrokerDetail(pkg.path("Publisher"), warnings);

        return new MappingResult(loanApp, parties, securities, financials, consents, broker, warnings);
    }

    // ── Application mapping ───────────────────────────────────────────

    private LoanApplication mapApplication(JsonNode application, JsonNode loanDetails,
                                           JsonNode overview, List<MappingResult.MappingWarning> warnings) {
        String lixiPurpose = textOrNull(overview, "LoanPurpose");

        LoanApplication.LoanApplicationBuilder builder = LoanApplication.builder()
                .applicationNumber(textOrNull(application, "@UniqueID"))
                .status(ApplicationStatus.DRAFT)
                .channel(Channel.BROKER);

        if (lixiPurpose != null) {
            LoanPurpose purpose = LOAN_PURPOSE_MAP.get(lixiPurpose);
            if (purpose != null) {
                builder.purpose(purpose);
            } else {
                warnings.add(new MappingResult.MappingWarning(
                        "Overview.LoanPurpose", "Unmapped loan purpose: " + lixiPurpose));
            }
            OccupancyType occ = OCCUPANCY_MAP.get(lixiPurpose);
            if (occ != null) {
                builder.occupancyType(occ);
            }
        }

        if (!loanDetails.isMissingNode()) {
            builder.requestedAmount(decimalOrNull(loanDetails, "LoanAmount"));
            builder.termMonths(intOrZero(loanDetails, "Term"));
            mapEnum(loanDetails, "InterestType", INTEREST_TYPE_MAP, warnings,
                    "LoanDetails.InterestType").ifPresent(builder::interestType);
            mapEnum(loanDetails, "RepaymentType", REPAYMENT_TYPE_MAP, warnings,
                    "LoanDetails.RepaymentType").ifPresent(builder::repaymentType);
            mapEnum(loanDetails, "RepaymentFrequency", REPAYMENT_FREQ_MAP, warnings,
                    "LoanDetails.RepaymentFrequency").ifPresent(builder::repaymentFrequency);
        }

        return builder.build();
    }

    // ── Applicant mapping ─────────────────────────────────────────────

    private List<MappingResult.PartyWithRole> mapApplicants(JsonNode personApplicants,
                                                            List<MappingResult.MappingWarning> warnings) {
        List<MappingResult.PartyWithRole> result = new ArrayList<>();
        if (personApplicants.isMissingNode() || !personApplicants.isArray()) {
            warnings.add(new MappingResult.MappingWarning("PersonApplicant", "No applicants found"));
            return result;
        }

        for (int i = 0; i < personApplicants.size(); i++) {
            JsonNode applicant = personApplicants.get(i);
            String pathPrefix = "PersonApplicant[" + i + "]";

            Party party = mapParty(applicant, pathPrefix, warnings);
            boolean isPrimary = applicant.path("@PrimaryApplicant").asBoolean(i == 0);
            PartyRole role = isPrimary ? PartyRole.PRIMARY_BORROWER : PartyRole.CO_BORROWER;

            List<PartyAddress> addresses = mapAddresses(applicant.path("Address"), party, pathPrefix, warnings);
            List<Employment> employments = mapEmployments(applicant.path("Employment"), party, pathPrefix, warnings);
            List<PartyIdentification> ids = mapIdentifications(applicant.path("Identification"), party, pathPrefix, warnings);

            result.add(new MappingResult.PartyWithRole(party, role, addresses, employments, ids));
        }
        return result;
    }

    private Party mapParty(JsonNode applicant, String pathPrefix,
                           List<MappingResult.MappingWarning> warnings) {
        JsonNode name = applicant.path("PersonName");
        return Party.builder()
                .partyType(PartyType.INDIVIDUAL)
                .firstName(textOrNull(name, "FirstName"))
                .middleNames(textOrNull(name, "MiddleName"))
                .surname(textOrNull(name, "Surname"))
                .dateOfBirth(dateOrNull(applicant, "DateOfBirth"))
                .gender(textOrNull(applicant, "Gender"))
                .email(textOrNull(applicant, "Email"))
                .mobilePhone(textOrNull(applicant.path("Phone"), "Mobile"))
                .kycStatus(KycStatus.NOT_STARTED)
                .build();
    }

    // ── Address mapping ───────────────────────────────────────────────

    private List<PartyAddress> mapAddresses(JsonNode addresses, Party party, String pathPrefix,
                                            List<MappingResult.MappingWarning> warnings) {
        List<PartyAddress> result = new ArrayList<>();
        if (addresses.isMissingNode() || !addresses.isArray()) return result;

        for (int i = 0; i < addresses.size(); i++) {
            JsonNode addr = addresses.get(i);
            String addrPath = pathPrefix + ".Address[" + i + "]";

            PartyAddress.PartyAddressBuilder builder = PartyAddress.builder()
                    .party(party)
                    .addressType(textOrNull(addr, "@Type"))
                    .streetNumber(textOrNull(addr, "StreetNumber"))
                    .streetName(textOrNull(addr, "Street"))
                    .suburb(textOrNull(addr, "Suburb"))
                    .state(textOrNull(addr, "State"))
                    .postcode(textOrNull(addr, "Postcode"))
                    .country(addr.has("Country") ? addr.get("Country").asText() : "AU");

            int years = intOrZero(addr, "YearsAtAddress");
            builder.yearsAtAddress(years);
            builder.monthsAtAddress(years * 12);

            String housing = textOrNull(addr, "@Housing");
            if (housing != null) {
                HousingStatus hs = HOUSING_STATUS_MAP.get(housing);
                if (hs != null) {
                    builder.housingStatus(hs);
                } else {
                    warnings.add(new MappingResult.MappingWarning(addrPath + ".@Housing",
                            "Unmapped housing status: " + housing));
                }
            }

            result.add(builder.build());
        }
        return result;
    }

    // ── Employment mapping ────────────────────────────────────────────

    private List<Employment> mapEmployments(JsonNode employments, Party party, String pathPrefix,
                                            List<MappingResult.MappingWarning> warnings) {
        List<Employment> result = new ArrayList<>();
        if (employments.isMissingNode() || !employments.isArray()) return result;

        for (int i = 0; i < employments.size(); i++) {
            JsonNode emp = employments.get(i);
            String empPath = pathPrefix + ".Employment[" + i + "]";

            Employment.EmploymentBuilder builder = Employment.builder()
                    .party(party)
                    .employerName(textOrNull(emp, "Employer"))
                    .occupation(textOrNull(emp, "Role"))
                    .startDate(dateOrNull(emp, "StartDate"))
                    .isCurrent(true);

            String empType = textOrNull(emp, "@Type");
            if (empType != null) {
                EmploymentType mapped = EMPLOYMENT_TYPE_MAP.get(empType);
                if (mapped != null) {
                    builder.employmentType(mapped);
                } else {
                    warnings.add(new MappingResult.MappingWarning(empPath + ".@Type",
                            "Unmapped employment type: " + empType));
                    builder.employmentType(EmploymentType.PAYG_FULL_TIME);
                }
            } else {
                builder.employmentType(EmploymentType.PAYG_FULL_TIME);
            }

            JsonNode income = emp.path("Income");
            if (!income.isMissingNode()) {
                builder.annualBaseSalary(decimalOrNull(income, "GrossAnnual"));
            }

            result.add(builder.build());
        }
        return result;
    }

    // ── Identification mapping ────────────────────────────────────────

    private List<PartyIdentification> mapIdentifications(JsonNode identifications, Party party,
                                                         String pathPrefix,
                                                         List<MappingResult.MappingWarning> warnings) {
        List<PartyIdentification> result = new ArrayList<>();
        if (identifications.isMissingNode() || !identifications.isArray()) return result;

        for (int i = 0; i < identifications.size(); i++) {
            JsonNode idNode = identifications.get(i);
            result.add(PartyIdentification.builder()
                    .party(party)
                    .idType(textOrNull(idNode, "Type"))
                    .idNumber(textOrNull(idNode, "Number"))
                    .issuingState(textOrNull(idNode, "State"))
                    .expiryDate(dateOrNull(idNode, "ExpiryDate"))
                    .build());
        }
        return result;
    }

    // ── Property / Security mapping ───────────────────────────────────

    private List<PropertySecurity> mapSecurities(JsonNode realEstateAssets,
                                                 List<MappingResult.MappingWarning> warnings) {
        List<PropertySecurity> result = new ArrayList<>();
        if (realEstateAssets.isMissingNode() || !realEstateAssets.isArray()) return result;

        for (int i = 0; i < realEstateAssets.size(); i++) {
            JsonNode asset = realEstateAssets.get(i);
            String assetPath = "RealEstateAsset[" + i + "]";
            result.add(mapProperty(asset, assetPath, warnings));
        }
        return result;
    }

    private PropertySecurity mapProperty(JsonNode realEstateAsset, String pathPrefix,
                                         List<MappingResult.MappingWarning> warnings) {
        JsonNode addr = realEstateAsset.path("Address");
        String propertyType = textOrNull(realEstateAsset, "PropertyType");

        PropertySecurity.PropertySecurityBuilder builder = PropertySecurity.builder()
                .streetNumber(textOrNull(addr, "StreetNumber"))
                .streetName(textOrNull(addr, "Street"))
                .suburb(textOrNull(addr, "Suburb"))
                .state(textOrNull(addr, "State"))
                .postcode(textOrNull(addr, "Postcode"))
                .numberOfBedrooms(intOrNull(realEstateAsset, "Bedrooms"))
                .landAreaSqm(decimalOrNull(realEstateAsset, "LandArea"))
                .yearBuilt(intOrNull(realEstateAsset, "YearBuilt"))
                .purchasePrice(decimalOrNull(realEstateAsset.path("ContractPrice"), "Amount"));

        if (propertyType != null) {
            PropertyCategory cat = PROPERTY_TYPE_MAP.get(propertyType);
            if (cat != null) {
                builder.propertyCategory(cat);
            } else {
                warnings.add(new MappingResult.MappingWarning(pathPrefix + ".PropertyType",
                        "Unmapped property type: " + propertyType));
            }
            SecurityType sec = SECURITY_TYPE_MAP.get(propertyType);
            if (sec != null) {
                builder.securityType(sec);
            } else {
                builder.securityType(SecurityType.EXISTING_RESIDENTIAL);
            }
        } else {
            builder.securityType(SecurityType.EXISTING_RESIDENTIAL);
        }

        PropertySecurity security = builder.build();

        // Map valuation if present
        JsonNode valNode = realEstateAsset.path("Valuation");
        if (!valNode.isMissingNode()) {
            Valuation valuation = mapValuation(valNode, security, pathPrefix, warnings);
            security.setValuation(valuation);
        }

        return security;
    }

    private Valuation mapValuation(JsonNode valNode, PropertySecurity security,
                                   String pathPrefix, List<MappingResult.MappingWarning> warnings) {
        Valuation.ValuationBuilder builder = Valuation.builder()
                .security(security)
                .estimatedValue(decimalOrNull(valNode, "EstimatedValue"))
                .completedDate(dateOrNull(valNode, "ValuationDate"))
                .provider(textOrNull(valNode, "Valuer"))
                .status(ValuationStatus.COMPLETED);

        String valType = textOrNull(valNode, "ValuationType");
        if (valType != null) {
            ValuationType mapped = VALUATION_TYPE_MAP.get(valType);
            if (mapped != null) {
                builder.valuationType(mapped);
            } else {
                warnings.add(new MappingResult.MappingWarning(pathPrefix + ".Valuation.ValuationType",
                        "Unmapped valuation type: " + valType));
                builder.valuationType(ValuationType.DESKTOP);
            }
        } else {
            builder.valuationType(ValuationType.DESKTOP);
        }

        return builder.build();
    }

    // ── Financial snapshot mapping ─────────────────────────────────────

    private FinancialSnapshot mapFinancials(JsonNode application,
                                            List<MappingResult.PartyWithRole> parties,
                                            List<MappingResult.MappingWarning> warnings) {
        FinancialSnapshot.FinancialSnapshotBuilder builder = FinancialSnapshot.builder()
                .capturedAt(LocalDateTime.now());

        // Income items from employment + non-employment income
        List<IncomeItem> incomeItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;

        for (MappingResult.PartyWithRole pwr : parties) {
            for (Employment emp : pwr.employments()) {
                if (emp.getAnnualBaseSalary() != null) {
                    totalGross = totalGross.add(emp.getAnnualBaseSalary());
                    incomeItems.add(IncomeItem.builder()
                            .incomeType(IncomeType.SALARY)
                            .grossAnnualAmount(emp.getAnnualBaseSalary())
                            .frequency("Annual")
                            .build());
                }
            }
        }

        // Non-employment income from PersonApplicant[n].Income.NonEmployment
        JsonNode applicants = application.path("PersonApplicant");
        if (applicants.isArray()) {
            for (int i = 0; i < applicants.size(); i++) {
                JsonNode nonEmp = applicants.get(i).path("Income").path("NonEmployment");
                if (nonEmp.isArray()) {
                    for (JsonNode item : nonEmp) {
                        String type = textOrNull(item, "Type");
                        BigDecimal gross = decimalOrNull(item, "GrossAnnual");
                        if (gross != null) {
                            totalGross = totalGross.add(gross);
                        }
                        IncomeType incomeType = type != null ? INCOME_TYPE_MAP.getOrDefault(type, IncomeType.OTHER)
                                : IncomeType.OTHER;
                        incomeItems.add(IncomeItem.builder()
                                .incomeType(incomeType)
                                .grossAnnualAmount(gross)
                                .frequency("Annual")
                                .build());
                    }
                }
            }
        }

        builder.totalGrossAnnualIncome(totalGross);

        // Expenses
        List<ExpenseItem> expenses = mapExpenses(application.path("Expense"), warnings);
        BigDecimal totalMonthlyExpenses = expenses.stream()
                .map(ExpenseItem::getMonthlyAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.declaredMonthlyExpenses(totalMonthlyExpenses);

        // Liabilities
        List<Liability> liabilities = mapLiabilities(application.path("Liability"), warnings);

        // Assets
        List<Asset> assets = mapAssets(application.path("Asset"), warnings);
        BigDecimal totalAssetValue = assets.stream()
                .map(Asset::getEstimatedValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialSnapshot snapshot = builder.build();

        // Link children to parent
        for (IncomeItem ii : incomeItems) {
            ii.setFinancialSnapshot(snapshot);
        }
        snapshot.setIncomeItems(incomeItems);

        for (ExpenseItem ei : expenses) {
            ei.setFinancialSnapshot(snapshot);
        }
        snapshot.setExpenseItems(expenses);

        for (Liability li : liabilities) {
            li.setFinancialSnapshot(snapshot);
        }
        snapshot.setLiabilities(liabilities);

        for (Asset a : assets) {
            a.setFinancialSnapshot(snapshot);
        }
        snapshot.setAssets(assets);

        return snapshot;
    }

    private List<ExpenseItem> mapExpenses(JsonNode expenses, List<MappingResult.MappingWarning> warnings) {
        List<ExpenseItem> result = new ArrayList<>();
        if (expenses.isMissingNode() || !expenses.isArray()) return result;

        for (JsonNode exp : expenses) {
            BigDecimal amount = decimalOrNull(exp, "MonthlyAmount");
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                result.add(ExpenseItem.builder()
                        .category(textOrNull(exp, "Type"))
                        .monthlyAmount(amount)
                        .frequency("Monthly")
                        .source("LIXI_CAL")
                        .build());
            }
        }
        return result;
    }

    private List<Liability> mapLiabilities(JsonNode liabilities, List<MappingResult.MappingWarning> warnings) {
        List<Liability> result = new ArrayList<>();
        if (liabilities.isMissingNode() || !liabilities.isArray()) return result;

        for (int i = 0; i < liabilities.size(); i++) {
            JsonNode liab = liabilities.get(i);
            String liabPath = "Liability[" + i + "]";

            String type = textOrNull(liab, "Type");
            LiabilityType liabType = type != null ? LIABILITY_TYPE_MAP.getOrDefault(type, LiabilityType.OTHER)
                    : LiabilityType.OTHER;
            if (type != null && !LIABILITY_TYPE_MAP.containsKey(type)) {
                warnings.add(new MappingResult.MappingWarning(liabPath + ".Type",
                        "Unmapped liability type: " + type));
            }

            result.add(Liability.builder()
                    .liabilityType(liabType)
                    .outstandingBalance(decimalOrNull(liab, "Balance"))
                    .creditLimit(decimalOrNull(liab, "Limit"))
                    .monthlyRepayment(decimalOrNull(liab, "MonthlyPayment"))
                    .lender(textOrNull(liab, "Institution"))
                    .build());
        }
        return result;
    }

    private List<Asset> mapAssets(JsonNode assets, List<MappingResult.MappingWarning> warnings) {
        List<Asset> result = new ArrayList<>();
        if (assets.isMissingNode() || !assets.isArray()) return result;

        for (int i = 0; i < assets.size(); i++) {
            JsonNode asset = assets.get(i);
            String assetPath = "Asset[" + i + "]";

            String type = textOrNull(asset, "Type");
            AssetType assetType = type != null ? ASSET_TYPE_MAP.getOrDefault(type, AssetType.OTHER)
                    : AssetType.OTHER;
            if (type != null && !ASSET_TYPE_MAP.containsKey(type)) {
                warnings.add(new MappingResult.MappingWarning(assetPath + ".Type",
                        "Unmapped asset type: " + type));
            }

            result.add(Asset.builder()
                    .assetType(assetType)
                    .estimatedValue(decimalOrNull(asset, "Value"))
                    .description(textOrNull(asset, "Description"))
                    .build());
        }
        return result;
    }

    // ── Consent mapping ───────────────────────────────────────────────

    private List<ConsentRecord> mapAllConsents(JsonNode personApplicants,
                                               List<MappingResult.MappingWarning> warnings) {
        List<ConsentRecord> result = new ArrayList<>();
        if (personApplicants.isMissingNode() || !personApplicants.isArray()) return result;

        for (int i = 0; i < personApplicants.size(); i++) {
            JsonNode privacy = personApplicants.get(i).path("Privacy");
            if (!privacy.isMissingNode()) {
                result.add(mapConsent(privacy, warnings));
            }
        }
        return result;
    }

    private ConsentRecord mapConsent(JsonNode privacy, List<MappingResult.MappingWarning> warnings) {
        boolean granted = privacy.path("ConsentObtained").asBoolean(false);
        LocalDate consentDate = dateOrNull(privacy, "ConsentDate");

        return ConsentRecord.builder()
                .consentType(ConsentType.PRIVACY_COLLECTION)
                .granted(granted)
                .grantedAt(consentDate != null ? consentDate.atStartOfDay() : null)
                .captureMethod("LIXI_CAL")
                .build();
    }

    // ── Broker detail mapping ─────────────────────────────────────────

    private BrokerDetail mapBrokerDetail(JsonNode publisher, List<MappingResult.MappingWarning> warnings) {
        if (publisher.isMissingNode()) return null;

        return BrokerDetail.builder()
                .brokerCompany(textOrNull(publisher, "CompanyName"))
                .brokerId(textOrNull(publisher, "ABN"))
                .brokerReference(textOrNull(publisher, "ACL"))
                .build();
    }

    // ── Generic enum mapper ───────────────────────────────────────────

    private <T> Optional<T> mapEnum(JsonNode parent, String field, Map<String, T> map,
                                    List<MappingResult.MappingWarning> warnings, String lixiPath) {
        String value = textOrNull(parent, field);
        if (value == null) return Optional.empty();

        T mapped = map.get(value);
        if (mapped != null) return Optional.of(mapped);

        warnings.add(new MappingResult.MappingWarning(lixiPath, "Unmapped value: " + value));
        return Optional.empty();
    }

    // ── JSON helper methods ───────────────────────────────────────────

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOrZero(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return (node.isMissingNode() || node.isNull()) ? 0 : node.asInt(0);
    }

    private static Integer intOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return (node.isMissingNode() || node.isNull()) ? null : node.asInt();
    }

    private static LocalDate dateOrNull(JsonNode parent, String field) {
        String text = textOrNull(parent, field);
        if (text == null) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
