package com.atheryon.mortgages.migration.seed;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a 10,000-loan seed CSV mimicking a legacy AU core banking export.
 * 83 columns with realistic distributions and embedded quality issues for migration demo/testing.
 *
 * Run with: java MigrationSeedGenerator
 * Or: ./gradlew run -PmainClass=com.atheryon.mortgages.migration.seed.MigrationSeedGenerator
 */
public class MigrationSeedGenerator {

    private static final int TOTAL_LOANS = 10_000;
    private static final int SAMPLE_SIZE = 100;
    private static final long SEED = 42L;

    private static final DateTimeFormatter LEGACY_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Path BASE_DIR = Paths.get("src/main/resources/migration");
    private static final Path FULL_FILE = BASE_DIR.resolve("seed-10k-loans.csv");
    private static final Path SAMPLE_FILE = BASE_DIR.resolve("seed-100-loans.csv");

    // ── Column header (83 columns) ──────────────────────────────────────────────
    private static final String[] HEADERS = {
        "LOAN_ACCT_NUM", "BORROWER_FIRST_NAME", "BORROWER_LAST_NAME", "BORROWER_DOB",
        "BORROWER_EMAIL", "BORROWER_PHONE", "CO_BORROWER_FIRST_NAME", "CO_BORROWER_LAST_NAME",
        "PROP_STREET_NUM", "PROP_STREET", "PROP_SUBURB", "PROP_STATE", "PROP_POSTCODE",
        "PROP_TYPE", "PURCHASE_PRICE", "ORIG_AMOUNT", "CURRENT_BALANCE", "CURRENT_RATE",
        "RATE_TYPE", "LOAN_TERM_MONTHS", "ORIGINATION_DATE", "SETTLEMENT_DATE", "STATUS",
        "REPAY_FREQUENCY", "REPAY_AMOUNT", "LVR", "LMI_FLAG", "LMI_PREMIUM",
        "EMPLOYER_NAME", "EMPLOYMENT_TYPE", "GROSS_ANNUAL_INCOME", "NET_MONTHLY_INCOME",
        "MONTHLY_EXPENSES", "CREDIT_CARD_BALANCE", "OTHER_LIABILITIES", "PROPERTY_VALUE_EST",
        "VALUATION_DATE", "INSURANCE_PROVIDER", "BROKER_NAME", "BROKER_CODE",
        "BRANCH_CODE", "INTERNAL_SCORE",
        // Extended columns (42-83) to reach 83 total
        "ARREARS_DAYS", "ARREARS_AMOUNT", "LAST_PAYMENT_DATE", "LAST_PAYMENT_AMOUNT",
        "OFFSET_BALANCE", "REDRAW_BALANCE", "OFFSET_ACCT_NUM", "REDRAW_ENABLED",
        "INTEREST_ONLY_FLAG", "IO_EXPIRY_DATE", "FIXED_RATE_EXPIRY", "DISCOUNT_MARGIN",
        "PRODUCT_CODE", "PRODUCT_NAME", "CHANNEL", "PURPOSE",
        "SECURITY_TYPE", "TITLE_REF", "COUNCIL_AREA", "ZONING",
        "NUM_BEDROOMS", "NUM_BATHROOMS", "LAND_AREA_SQM", "FLOOR_AREA_SQM",
        "YEAR_BUILT", "STRATA_FLAG", "STRATA_LEVY", "BODY_CORP_NAME",
        "GUARANTOR_FLAG", "GUARANTOR_NAME", "GUARANTOR_RELATIONSHIP", "FEE_PACKAGE",
        "ANNUAL_FEE", "APPLICATION_ID", "LOAN_GROUP_ID", "SETTLEMENT_AGENT",
        "SOLICITOR_NAME", "DISCHARGE_DATE", "DISCHARGE_REASON", "NOTES",
        "LAST_UPDATED"
    };

    // ── Reference data ──────────────────────────────────────────────────────────

    private static final String[] FIRST_NAMES = {
        "James", "John", "Robert", "Michael", "David", "William", "Richard", "Joseph",
        "Thomas", "Daniel", "Matthew", "Andrew", "Christopher", "Mark", "Steven",
        "Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan",
        "Jessica", "Sarah", "Karen", "Lisa", "Nancy", "Margaret", "Sandra", "Ashley",
        "Emily", "Olivia", "Charlotte", "Amelia", "Mia", "Isabella", "Sophie",
        "Liam", "Noah", "Oliver", "Jack", "Henry", "Leo", "Archer", "Ethan",
        "Lucas", "Mason", "Logan", "Alexander", "Benjamin", "Elijah", "Theodore",
        "Chloe", "Grace", "Ava", "Zoe", "Lily", "Harper", "Ella", "Aria"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Jones", "Williams", "Brown", "Wilson", "Taylor", "Johnson", "White",
        "Martin", "Anderson", "Thompson", "Nguyen", "Thomas", "Walker", "Harris",
        "Lee", "Ryan", "Robinson", "Kelly", "King", "Davis", "Wright", "Clark",
        "Hall", "Green", "Adams", "Baker", "Campbell", "Mitchell", "Roberts",
        "Turner", "Phillips", "Parker", "Evans", "Collins", "Edwards", "Stewart",
        "Morris", "Murphy", "Cook", "Rogers", "Morgan", "Cooper", "Richardson",
        "Cox", "Howard", "Ward", "Peterson", "Gray", "Watson"
    };

    private static final String[] EMPLOYER_NAMES = {
        "Commonwealth Bank", "Westpac Group", "ANZ Banking Group", "National Australia Bank",
        "Telstra Corporation", "BHP Group", "Woolworths Group", "Wesfarmers",
        "CSL Limited", "Macquarie Group", "Rio Tinto", "Transurban Group",
        "Qantas Airways", "Woodside Energy", "Santos Limited", "Fortescue Metals",
        "Coles Group", "Computershare", "ResMed Inc", "Cochlear Limited",
        "NSW Health", "VIC Department of Education", "QLD Government", "APS Federal",
        "University of Sydney", "University of Melbourne", "Monash University",
        "PwC Australia", "Deloitte Australia", "KPMG Australia", "EY Australia",
        "Google Australia", "Microsoft Australia", "Amazon Australia", "Atlassian",
        "Canva", "Afterpay", "Xero Australia", "Domain Group", "REA Group",
        "JB Hi-Fi", "Harvey Norman", "Bunnings", "Officeworks", "Kmart Australia",
        "Medibank Private", "Bupa Australia", "HCF", "NIB Health Funds",
        "Lendlease", "Stockland", "Mirvac Group", "Dexus", "GPT Group"
    };

    private static final String[] BROKER_NAMES = {
        "Aussie Home Loans", "Mortgage Choice", "Loan Market", "Yellow Brick Road",
        "AFG", "Connective", "Finsure", "PLAN Australia",
        "Liberty Network Services", "National Mortgage Brokers"
    };

    private static final String[] INSURANCE_PROVIDERS = {
        "QBE Insurance", "Suncorp", "Allianz Australia", "IAG",
        "CommInsure", "ANZ Lenders Mortgage Insurance", "Genworth", "Helia"
    };

    private static final String[] PRODUCT_CODES = {
        "HL-STD-VAR", "HL-STD-FIX", "HL-PKG-VAR", "HL-PKG-FIX", "HL-INV-VAR",
        "HL-INV-FIX", "HL-FHB-VAR", "HL-IO-VAR", "HL-IO-FIX", "HL-SMSF-VAR",
        "HL-LDL-VAR", "HL-CON-VAR", "HL-BRG-VAR", "HL-SPL-MIX"
    };

    private static final String[] PRODUCT_NAMES = {
        "Standard Variable", "Standard Fixed", "Package Variable", "Package Fixed",
        "Investor Variable", "Investor Fixed", "First Home Buyer Variable",
        "Interest Only Variable", "Interest Only Fixed", "SMSF Variable",
        "Low Doc Variable", "Construction Variable", "Bridging Variable", "Split Rate Mix"
    };

    private static final String[] CHANNELS = {"Branch", "Broker", "Online", "Mobile", "Phone"};

    private static final String[] PURPOSES = {
        "Purchase", "Refinance", "Construction", "Renovation", "Investment", "Equity Release"
    };

    private static final String[] SECURITY_TYPES = {
        "Registered First Mortgage", "Registered Second Mortgage", "Equitable Mortgage"
    };

    private static final String[] ZONINGS = {"Residential", "Mixed Use", "Rural Residential"};

    private static final String[] DISCHARGE_REASONS = {"Refinanced", "Sold", "Paid Out", "Transferred"};

    private static final String[] SETTLEMENT_AGENTS = {
        "Conveyancing Works", "Easy Settlements", "Nationwide Conveyancing",
        "PEXA Direct", "Settlex"
    };

    private static final String[] SOLICITOR_NAMES = {
        "Mills Oakley", "Gadens", "HWL Ebsworth", "Holding Redlich",
        "Slater & Gordon", "Maurice Blackburn"
    };

    // ── Australian suburb data (state -> suburb/postcode pairs) ──────────────────
    private static final Map<String, String[][]> SUBURBS = new LinkedHashMap<>();
    static {
        SUBURBS.put("NSW", new String[][]{
            {"Parramatta", "2150"}, {"Penrith", "2750"}, {"Liverpool", "2170"},
            {"Blacktown", "2148"}, {"Bankstown", "2200"}, {"Chatswood", "2067"},
            {"Hornsby", "2077"}, {"Sutherland", "2232"}, {"Manly", "2095"},
            {"Bondi", "2026"}, {"Surry Hills", "2010"}, {"Newtown", "2042"},
            {"Marrickville", "2204"}, {"Randwick", "2031"}, {"Mosman", "2088"},
            {"Castle Hill", "2154"}, {"Epping", "2121"}, {"Ryde", "2112"},
            {"Strathfield", "2135"}, {"Burwood", "2134"}, {"Camden", "2570"},
            {"Wollongong", "2500"}, {"Newcastle", "2300"}, {"Gosford", "2250"},
            {"Campbelltown", "2560"}
        });
        SUBURBS.put("VIC", new String[][]{
            {"Richmond", "3121"}, {"South Yarra", "3141"}, {"Carlton", "3053"},
            {"Brunswick", "3056"}, {"Footscray", "3011"}, {"Box Hill", "3128"},
            {"Doncaster", "3108"}, {"Glen Waverley", "3150"}, {"Frankston", "3199"},
            {"Geelong", "3220"}, {"Ballarat", "3350"}, {"Bendigo", "3550"},
            {"Hawthorn", "3122"}, {"Toorak", "3142"}, {"Brighton", "3186"},
            {"Ringwood", "3134"}, {"Dandenong", "3175"}, {"Cranbourne", "3977"},
            {"Werribee", "3030"}, {"Melton", "3337"}
        });
        SUBURBS.put("QLD", new String[][]{
            {"Southbank", "4101"}, {"West End", "4101"}, {"Paddington", "4064"},
            {"Toowong", "4066"}, {"Indooroopilly", "4068"}, {"Chermside", "4032"},
            {"Carindale", "4152"}, {"Gold Coast", "4217"}, {"Surfers Paradise", "4217"},
            {"Cairns", "4870"}, {"Townsville", "4810"}, {"Toowoomba", "4350"},
            {"Ipswich", "4305"}, {"Redcliffe", "4020"}, {"Springfield", "4300"}
        });
        SUBURBS.put("WA", new String[][]{
            {"Fremantle", "6160"}, {"Subiaco", "6008"}, {"Joondalup", "6027"},
            {"Rockingham", "6168"}, {"Mandurah", "6210"}, {"Armadale", "6112"},
            {"Midland", "6056"}, {"Scarborough", "6019"}, {"Cottesloe", "6011"},
            {"Claremont", "6010"}
        });
        SUBURBS.put("SA", new String[][]{
            {"Glenelg", "5045"}, {"Norwood", "5067"}, {"Unley", "5061"},
            {"Prospect", "5082"}, {"Salisbury", "5108"}, {"Elizabeth", "5112"},
            {"Mount Barker", "5251"}, {"Victor Harbor", "5211"}
        });
        SUBURBS.put("TAS", new String[][]{
            {"Sandy Bay", "7005"}, {"Glenorchy", "7010"}, {"Launceston", "7250"},
            {"Devonport", "7310"}, {"Burnie", "7320"}
        });
        SUBURBS.put("ACT", new String[][]{
            {"Belconnen", "2617"}, {"Woden", "2606"}, {"Tuggeranong", "2900"},
            {"Gungahlin", "2912"}, {"Weston Creek", "2611"}, {"Braddon", "2612"}
        });
        SUBURBS.put("NT", new String[][]{
            {"Darwin", "0800"}, {"Palmerston", "0830"}, {"Alice Springs", "0870"},
            {"Katherine", "0850"}
        });
    }

    private static final String[] STREET_TYPES = {
        "Street", "Road", "Avenue", "Drive", "Court", "Place", "Crescent",
        "Lane", "Way", "Boulevard", "Parade", "Circuit", "Close", "Terrace"
    };

    private static final String[] STREET_NAMES = {
        "High", "George", "Victoria", "King", "Queen", "Elizabeth", "Station",
        "Church", "Main", "Park", "Bridge", "William", "Market", "Albert",
        "Charles", "Railway", "Smith", "Pacific", "Ocean", "Harbour",
        "Wattle", "Banksia", "Eucalyptus", "Jacaranda", "Acacia"
    };

    // State weights: NSW 32%, VIC 28%, QLD 18%, WA 10%, SA 7%, TAS 2%, ACT 2%, NT 1%
    private static final String[] STATES = {"NSW", "VIC", "QLD", "WA", "SA", "TAS", "ACT", "NT"};
    private static final double[] STATE_CDF = {0.32, 0.60, 0.78, 0.88, 0.95, 0.97, 0.99, 1.0};

    // Property types: House 55%, Unit 25%, Townhouse 15%, Land 5%
    private static final String[] PROP_TYPES = {"House", "Unit", "Townhouse", "Land"};
    private static final double[] PROP_TYPE_CDF = {0.55, 0.80, 0.95, 1.0};

    // Rate types: Variable 60%, Fixed 30%, Split 10%
    private static final String[] RATE_TYPES = {"Variable", "Fixed", "Split"};
    private static final double[] RATE_TYPE_CDF = {0.60, 0.90, 1.0};

    // Status: ACTIVE 92%, CLOSED 5%, ARREARS 2%, DISCHARGED 1%
    private static final String[] STATUSES = {"ACTIVE", "CLOSED", "ARREARS", "DISCHARGED"};
    private static final double[] STATUS_CDF = {0.92, 0.97, 0.99, 1.0};

    // Repayment frequency
    private static final String[] REPAY_FREQS = {"Monthly", "Fortnightly", "Weekly"};
    private static final double[] REPAY_FREQ_CDF = {0.70, 0.90, 1.0};

    // Employment types
    private static final String[] EMPLOYMENT_TYPES = {"PAYG", "Self-Employed", "Contract"};
    private static final double[] EMPLOYMENT_TYPE_CDF = {0.65, 0.25, 1.0};

    // Fee packages
    private static final String[] FEE_PACKAGES = {"None", "Bronze", "Silver", "Gold", "Platinum"};

    // ── Generator state ─────────────────────────────────────────────────────────

    private final Random rng;

    // Track issue assignments for exact counts
    private final Set<Integer> lmiInconsistent = new HashSet<>();      // MR-008: 4,219
    private final Set<Integer> missingIncome = new HashSet<>();        // MR-007: 3,891
    private final Set<Integer> rateOutliers = new HashSet<>();         // MR-002: 1,247
    private final Set<Integer> variableIncomplete = new HashSet<>();   // MR-003: 893
    private final Set<Integer> principalInvalid = new HashSet<>();     // MR-001: 12
    private final Set<Integer> missingEmployment = new HashSet<>();    // 41.8%: ~4,180
    private final Set<Integer> missingAddress = new HashSet<>();       // 11.3%: ~1,130
    private final Set<Integer> isoDateLoans = new HashSet<>();         // 5%: ~500

    public MigrationSeedGenerator() {
        this.rng = new Random(SEED);
    }

    public static void main(String[] args) throws IOException {
        MigrationSeedGenerator gen = new MigrationSeedGenerator();
        gen.preAssignQualityIssues();

        Files.createDirectories(BASE_DIR);

        System.out.println("Generating " + TOTAL_LOANS + " loans...");

        List<String[]> allRows = new ArrayList<>(TOTAL_LOANS);
        for (int i = 0; i < TOTAL_LOANS; i++) {
            allRows.add(gen.generateRow(i));
            if ((i + 1) % 2000 == 0) {
                System.out.println("  " + (i + 1) + " / " + TOTAL_LOANS);
            }
        }

        // Write full file
        writeCsv(FULL_FILE, allRows);
        System.out.println("Written: " + FULL_FILE + " (" + allRows.size() + " rows)");

        // Write sample file (first 100)
        writeCsv(SAMPLE_FILE, allRows.subList(0, SAMPLE_SIZE));
        System.out.println("Written: " + SAMPLE_FILE + " (" + SAMPLE_SIZE + " rows)");

        // Print quality issue summary
        gen.printSummary();
    }

    // ── Pre-assign quality issues to specific loan indices ──────────────────────

    private void preAssignQualityIssues() {
        Random assignRng = new Random(SEED + 1);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < TOTAL_LOANS; i++) indices.add(i);

        // MR-001: Principal <= 0 (12 loans)
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 12; i++) principalInvalid.add(indices.get(i));

        // MR-002: Rate outliers (1,247 loans)
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 1247; i++) rateOutliers.add(indices.get(i));

        // MR-003: Variable rate incomplete (893 loans) — only from Variable rate loans
        // We'll assign more than needed and filter at generation time
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 1500; i++) variableIncomplete.add(indices.get(i));

        // MR-007: Missing income (3,891 loans)
        Collections.shuffle(indices, assignRng);
        // Bias toward older originations — first half of sorted indices more likely
        for (int i = 0; i < 3891; i++) missingIncome.add(indices.get(i));

        // MR-008: LMI inconsistency (4,219 loans) — only for LVR > 80%
        // Assign more candidates, filter at generation
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 5500; i++) lmiInconsistent.add(indices.get(i));

        // Missing employment (41.8%)
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 4180; i++) missingEmployment.add(indices.get(i));

        // Missing addresses (11.3%)
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 1130; i++) missingAddress.add(indices.get(i));

        // ISO date format (5%)
        Collections.shuffle(indices, assignRng);
        for (int i = 0; i < 500; i++) isoDateLoans.add(indices.get(i));
    }

    // ── Row generation ──────────────────────────────────────────────────────────

    private String[] generateRow(int idx) {
        String[] row = new String[HEADERS.length];

        boolean useIsoDate = isoDateLoans.contains(idx);

        // Account number
        row[0] = String.format("ACT-%07d", idx + 1);

        // Borrower
        String firstName = pick(FIRST_NAMES);
        String lastName = pick(LAST_NAMES);
        row[1] = firstName;
        row[2] = lastName;
        row[3] = formatDate(randomDob(), useIsoDate);
        row[4] = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@" + pickEmailDomain();
        row[5] = "04" + String.format("%08d", rng.nextInt(100_000_000));

        // Co-borrower (40% have one)
        if (rng.nextDouble() < 0.40) {
            row[6] = pick(FIRST_NAMES);
            row[7] = rng.nextDouble() < 0.7 ? lastName : pick(LAST_NAMES);
        } else {
            row[6] = "";
            row[7] = "";
        }

        // Property address
        String state = pickWeighted(STATES, STATE_CDF);
        String[][] suburbs = SUBURBS.get(state);
        String[] suburbData = suburbs[rng.nextInt(suburbs.length)];
        String suburb = suburbData[0];
        String postcode = suburbData[1];

        if (missingAddress.contains(idx)) {
            // Incomplete address — missing street or suburb
            row[8] = rng.nextDouble() < 0.5 ? "" : String.valueOf(rng.nextInt(200) + 1);
            row[9] = rng.nextDouble() < 0.5 ? "" : pick(STREET_NAMES) + " " + pick(STREET_TYPES);
            row[10] = rng.nextDouble() < 0.3 ? "" : suburb;
            row[11] = state;
            row[12] = rng.nextDouble() < 0.2 ? "" : postcode;
        } else {
            row[8] = String.valueOf(rng.nextInt(200) + 1);
            row[9] = pick(STREET_NAMES) + " " + pick(STREET_TYPES);
            row[10] = suburb;
            row[11] = state;
            row[12] = postcode;
        }

        // Property type
        String propType = pickWeighted(PROP_TYPES, PROP_TYPE_CDF);
        row[13] = propType;

        // Origination date — bell curve peaking at 2019
        LocalDate origDate = randomOriginationDate();
        int origYear = origDate.getYear();
        LocalDate settleDate = origDate.plusDays(rng.nextInt(30) + 14);

        // Financials
        double purchasePrice = randomPurchasePrice(state, propType);
        double lvr = randomLvr();
        double origAmount = Math.round(purchasePrice * lvr / 100.0 * 100.0) / 100.0;

        // MR-001: Principal <= 0
        if (principalInvalid.contains(idx)) {
            origAmount = rng.nextDouble() < 0.5 ? 0.0 : -(rng.nextInt(10000) + 1);
        }

        row[14] = String.format("%.2f", purchasePrice);
        row[15] = String.format("%.2f", origAmount);

        // Current balance (some paydown)
        double yearsSinceOrig = (2025 - origYear) + rng.nextDouble();
        double paydownRatio = Math.max(0.1, 1.0 - (yearsSinceOrig * 0.03) - (rng.nextDouble() * 0.1));
        double currentBalance = Math.round(origAmount * paydownRatio * 100.0) / 100.0;
        if (origAmount <= 0) currentBalance = origAmount; // keep invalid
        row[16] = String.format("%.2f", currentBalance);

        // Interest rate (as monthly rate — legacy!)
        String rateType = pickWeighted(RATE_TYPES, RATE_TYPE_CDF);
        double annualRate;
        if (rateOutliers.contains(idx)) {
            // MR-002: Rate outliers — annual > 15% or < 1%
            annualRate = rng.nextDouble() < 0.5
                ? 0.01 + rng.nextDouble() * 0.9  // < 1%
                : 15.0 + rng.nextDouble() * 10.0; // > 15%
        } else {
            annualRate = 3.5 + rng.nextDouble() * 4.0; // 3.5% to 7.5% realistic
        }
        double monthlyRate = annualRate / 12.0;
        row[17] = String.format("%.3f", monthlyRate);
        row[18] = rateType;

        // MR-003: Variable rate incomplete — null out rate-related fields
        if ("Variable".equals(rateType) && variableIncomplete.contains(idx)) {
            row[17] = ""; // blank rate
        }

        // Loan term
        int termMonths = rng.nextDouble() < 0.85 ? 360 : (rng.nextDouble() < 0.5 ? 300 : 180);
        row[19] = String.valueOf(termMonths);

        row[20] = formatDate(origDate, useIsoDate);
        row[21] = formatDate(settleDate, useIsoDate);

        // Status
        String status = pickWeighted(STATUSES, STATUS_CDF);
        row[22] = status;

        // Repayment
        String repayFreq = pickWeighted(REPAY_FREQS, REPAY_FREQ_CDF);
        row[23] = repayFreq;

        double monthlyRepay = calculateRepayment(Math.abs(origAmount), annualRate / 100.0, termMonths);
        double repayAmount = switch (repayFreq) {
            case "Fortnightly" -> monthlyRepay * 12.0 / 26.0;
            case "Weekly" -> monthlyRepay * 12.0 / 52.0;
            default -> monthlyRepay;
        };
        row[24] = String.format("%.2f", repayAmount);

        // LVR
        row[25] = String.format("%.1f", lvr);

        // LMI
        boolean highLvr = lvr > 80.0;
        if (highLvr && lmiInconsistent.contains(idx)) {
            // MR-008: LMI inconsistency — high LVR but LMI_FLAG null
            row[26] = "";
            row[27] = "";
        } else if (highLvr) {
            row[26] = "Y";
            double lmiPremium = origAmount * (0.005 + rng.nextDouble() * 0.025);
            row[27] = String.format("%.2f", lmiPremium);
        } else {
            row[26] = "N";
            row[27] = "";
        }

        // Employment
        if (missingEmployment.contains(idx)) {
            row[28] = "";
            row[29] = rng.nextDouble() < 0.6 ? "" : pickWeighted(EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_CDF);
        } else {
            row[28] = pick(EMPLOYER_NAMES);
            row[29] = pickWeighted(EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_CDF);
        }

        // Income
        if (missingIncome.contains(idx)) {
            row[30] = ""; // MR-007
            row[31] = "";
        } else {
            double grossIncome = 50000 + rng.nextDouble() * 200000;
            row[30] = String.format("%.2f", grossIncome);
            row[31] = String.format("%.2f", grossIncome * 0.72 / 12.0);
        }

        // Expenses
        row[32] = rng.nextDouble() < 0.15 ? "" : String.format("%.2f", 2000 + rng.nextDouble() * 6000);
        row[33] = rng.nextDouble() < 0.30 ? "" : String.format("%.2f", rng.nextDouble() * 15000);
        row[34] = rng.nextDouble() < 0.40 ? "" : String.format("%.2f", rng.nextDouble() * 50000);

        // Property valuation
        double propValue = purchasePrice * (1.0 + (yearsSinceOrig * 0.04) + (rng.nextDouble() * 0.1));
        row[35] = String.format("%.2f", propValue);
        row[36] = rng.nextDouble() < 0.25 ? "" : formatDate(
            origDate.plusMonths(rng.nextInt(24)), useIsoDate);

        // Insurance, broker
        row[37] = rng.nextDouble() < 0.35 ? "" : pick(INSURANCE_PROVIDERS);
        boolean hasBroker = rng.nextDouble() < 0.55;
        row[38] = hasBroker ? pick(BROKER_NAMES) : "";
        row[39] = hasBroker ? "BRK-" + String.format("%04d", rng.nextInt(5000)) : "";

        // Internal fields
        row[40] = "BR-" + String.format("%03d", rng.nextInt(200) + 1);
        row[41] = String.valueOf(300 + rng.nextInt(600));

        // ── Extended columns (42–82) ────────────────────────────────────────────

        // Arrears
        boolean inArrears = "ARREARS".equals(status);
        row[42] = inArrears ? String.valueOf(rng.nextInt(180) + 30) : "0";
        row[43] = inArrears ? String.format("%.2f", repayAmount * (1 + rng.nextInt(5))) : "0.00";

        // Last payment
        LocalDate lastPayDate = LocalDate.of(2025, 1 + rng.nextInt(3), 1 + rng.nextInt(28));
        row[44] = "CLOSED".equals(status) || "DISCHARGED".equals(status)
            ? "" : formatDate(lastPayDate, useIsoDate);
        row[45] = "CLOSED".equals(status) || "DISCHARGED".equals(status)
            ? "" : String.format("%.2f", repayAmount);

        // Offset & redraw
        boolean hasOffset = rng.nextDouble() < 0.35;
        row[46] = hasOffset ? String.format("%.2f", rng.nextDouble() * 80000) : "";
        boolean hasRedraw = rng.nextDouble() < 0.45;
        row[47] = hasRedraw ? String.format("%.2f", rng.nextDouble() * 30000) : "";
        row[48] = hasOffset ? row[0].replace("ACT-", "OFF-") : "";
        row[49] = hasRedraw ? "Y" : "N";

        // Interest only
        boolean isIO = rng.nextDouble() < 0.15;
        row[50] = isIO ? "Y" : "N";
        row[51] = isIO ? formatDate(origDate.plusYears(rng.nextInt(5) + 1), useIsoDate) : "";

        // Fixed rate expiry
        row[52] = "Fixed".equals(rateType)
            ? formatDate(origDate.plusYears(rng.nextInt(5) + 1), useIsoDate) : "";

        // Discount margin
        row[53] = "Variable".equals(rateType)
            ? String.format("%.2f", rng.nextDouble() * 1.5) : "";

        // Product
        int prodIdx = rng.nextInt(PRODUCT_CODES.length);
        row[54] = PRODUCT_CODES[prodIdx];
        row[55] = PRODUCT_NAMES[Math.min(prodIdx, PRODUCT_NAMES.length - 1)];

        // Channel & purpose
        row[56] = hasBroker ? "Broker" : pick(CHANNELS);
        row[57] = pick(PURPOSES);

        // Security & property detail
        row[58] = pick(SECURITY_TYPES);
        row[59] = "CT-" + String.format("%06d", rng.nextInt(999999));
        row[60] = suburb + " Council";
        row[61] = pick(ZONINGS);

        // Bedrooms/bathrooms
        row[62] = "Land".equals(propType) ? "" : String.valueOf(rng.nextInt(4) + 1);
        row[63] = "Land".equals(propType) ? "" : String.valueOf(rng.nextInt(3) + 1);

        // Areas
        row[64] = String.format("%.0f", 200 + rng.nextDouble() * 800);
        row[65] = "Land".equals(propType) ? "" : String.format("%.0f", 80 + rng.nextDouble() * 300);

        // Year built
        row[66] = "Land".equals(propType) ? "" : String.valueOf(1960 + rng.nextInt(65));

        // Strata
        boolean isStrata = "Unit".equals(propType) || ("Townhouse".equals(propType) && rng.nextDouble() < 0.7);
        row[67] = isStrata ? "Y" : "N";
        row[68] = isStrata ? String.format("%.2f", 500 + rng.nextDouble() * 3000) : "";
        row[69] = isStrata ? "SP " + String.format("%05d", rng.nextInt(99999)) : "";

        // Guarantor
        boolean hasGuarantor = rng.nextDouble() < 0.08;
        row[70] = hasGuarantor ? "Y" : "N";
        row[71] = hasGuarantor ? pick(FIRST_NAMES) + " " + pick(LAST_NAMES) : "";
        row[72] = hasGuarantor ? (rng.nextDouble() < 0.7 ? "Parent" : "Spouse") : "";

        // Fees
        String feePkg = pick(FEE_PACKAGES);
        row[73] = feePkg;
        row[74] = "None".equals(feePkg) ? "0.00"
            : String.format("%.2f", switch (feePkg) {
                case "Bronze" -> 195.0;
                case "Silver" -> 295.0;
                case "Gold" -> 395.0;
                case "Platinum" -> 595.0;
                default -> 0.0;
            });

        // Application & group IDs
        row[75] = "APP-" + String.format("%08d", idx + 10000);
        row[76] = "GRP-" + String.format("%06d", (idx / 3) + 1);

        // Settlement & solicitor
        row[77] = pick(SETTLEMENT_AGENTS);
        row[78] = pick(SOLICITOR_NAMES);

        // Discharge (only for CLOSED/DISCHARGED)
        if ("CLOSED".equals(status) || "DISCHARGED".equals(status)) {
            LocalDate dischargeDate = origDate.plusYears(rng.nextInt(10) + 1);
            if (dischargeDate.isAfter(LocalDate.of(2025, 3, 1)))
                dischargeDate = LocalDate.of(2025, 2, rng.nextInt(28) + 1);
            row[79] = formatDate(dischargeDate, useIsoDate);
            row[80] = pick(DISCHARGE_REASONS);
        } else {
            row[79] = "";
            row[80] = "";
        }

        // Notes & last updated
        row[81] = rng.nextDouble() < 0.05 ? "Manual review required" : "";
        row[82] = formatDate(LocalDate.of(2025, 3, rng.nextInt(28) + 1), useIsoDate);

        return row;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String pick(String[] arr) {
        return arr[rng.nextInt(arr.length)];
    }

    private String pickWeighted(String[] values, double[] cdf) {
        double r = rng.nextDouble();
        for (int i = 0; i < cdf.length; i++) {
            if (r < cdf[i]) return values[i];
        }
        return values[values.length - 1];
    }

    private String pickEmailDomain() {
        double r = rng.nextDouble();
        if (r < 0.35) return "gmail.com";
        if (r < 0.55) return "outlook.com";
        if (r < 0.70) return "yahoo.com.au";
        if (r < 0.80) return "hotmail.com";
        if (r < 0.88) return "icloud.com";
        if (r < 0.93) return "bigpond.com";
        if (r < 0.97) return "optusnet.com.au";
        return "protonmail.com";
    }

    private LocalDate randomDob() {
        // Borrowers aged 25-70
        int year = 1955 + rng.nextInt(46);
        int month = rng.nextInt(12) + 1;
        int day = rng.nextInt(28) + 1;
        return LocalDate.of(year, month, day);
    }

    private LocalDate randomOriginationDate() {
        // Bell curve peaking at 2019: use normal distribution centered on 2019
        double yearD = 2019.0 + rng.nextGaussian() * 3.5;
        int year = Math.max(2010, Math.min(2025, (int) Math.round(yearD)));
        int month = rng.nextInt(12) + 1;
        int day = rng.nextInt(28) + 1;
        return LocalDate.of(year, month, day);
    }

    private double randomPurchasePrice(String state, String propType) {
        // Median $450k but vary by state and type
        double base = switch (state) {
            case "NSW" -> 700000;
            case "VIC" -> 600000;
            case "QLD" -> 500000;
            case "WA" -> 450000;
            case "SA" -> 400000;
            case "ACT" -> 550000;
            case "TAS" -> 380000;
            case "NT" -> 350000;
            default -> 450000;
        };
        double typeMultiplier = switch (propType) {
            case "House" -> 1.0;
            case "Unit" -> 0.65;
            case "Townhouse" -> 0.80;
            case "Land" -> 0.40;
            default -> 1.0;
        };
        // Add variation: +/- 50%
        double variation = 0.5 + rng.nextDouble() * 1.0;
        double price = base * typeMultiplier * variation;
        // Clamp to $150k - $2M
        price = Math.max(150_000, Math.min(2_000_000, price));
        return Math.round(price / 100.0) * 100.0; // round to nearest $100
    }

    private double randomLvr() {
        // 40%-95%, median ~72%
        double lvr = 72.0 + rng.nextGaussian() * 12.0;
        return Math.max(40.0, Math.min(95.0, Math.round(lvr * 10.0) / 10.0));
    }

    private double calculateRepayment(double principal, double annualRate, int termMonths) {
        if (principal <= 0 || annualRate <= 0) return 0.0;
        double r = annualRate / 12.0;
        double payment = principal * r * Math.pow(1 + r, termMonths)
            / (Math.pow(1 + r, termMonths) - 1);
        return Math.round(payment * 100.0) / 100.0;
    }

    private String formatDate(LocalDate date, boolean useIso) {
        return useIso ? date.format(ISO_DATE_FMT) : date.format(LEGACY_DATE_FMT);
    }

    // ── CSV writing ─────────────────────────────────────────────────────────────

    private static void writeCsv(Path path, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(String.join(",", HEADERS));
            w.newLine();
            for (String[] row : rows) {
                w.write(Arrays.stream(row)
                    .map(MigrationSeedGenerator::csvEscape)
                    .collect(Collectors.joining(",")));
                w.newLine();
            }
        }
    }

    private static String csvEscape(String val) {
        if (val == null || val.isEmpty()) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    // ── Summary ─────────────────────────────────────────────────────────────────

    private void printSummary() {
        System.out.println("\n=== Quality Issue Summary ===");
        System.out.println("MR-001 Principal <= 0:        " + principalInvalid.size());
        System.out.println("MR-002 Rate outliers:         " + rateOutliers.size());
        System.out.println("MR-003 Variable incomplete:   " + variableIncomplete.size() + " (candidates, actual depends on rate type)");
        System.out.println("MR-007 Missing income:        " + missingIncome.size());
        System.out.println("MR-008 LMI inconsistent:      " + lmiInconsistent.size() + " (candidates, actual depends on LVR > 80%)");
        System.out.println("Missing employment:           " + missingEmployment.size());
        System.out.println("Missing addresses:            " + missingAddress.size());
        System.out.println("ISO date format:              " + isoDateLoans.size());
        System.out.println("\nTotal columns: " + HEADERS.length);
    }
}
