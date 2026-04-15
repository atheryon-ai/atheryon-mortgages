package com.atheryon.mortgages.migration.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
 * Generates a 500-row migration seed CSV with realistic Australian mortgage data
 * and deliberate quality issues for demo purposes.
 *
 * Simplified 26-column schema matching common loan book exports.
 * Quality issues are embedded at controlled percentages for migration pipeline testing.
 */
@Component
@Profile("dev")
public class PortfolioSeedGenerator {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSeedGenerator.class);

    private static final int TOTAL_ROWS = 500;
    private static final long SEED = 42L;

    private static final Path OUTPUT_PATH = Paths.get("src/main/resources/migration/seed-portfolio.csv");

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MM_DD_YYYY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private static final String[] HEADERS = {
        "loan_ref", "borrower_name", "borrower_dob", "borrower_email", "borrower_phone",
        "co_borrower_name", "address_street", "address_suburb", "address_state",
        "address_postcode", "property_type", "bedrooms", "purchase_price", "loan_amount",
        "interest_rate", "loan_term_months", "interest_type", "repayment_type",
        "repayment_frequency", "product_name", "settlement_date", "loan_status", "lvr",
        "employer", "income_gross_annual", "expenses_monthly"
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
        "Coles Group", "NSW Health", "VIC Department of Education", "QLD Government",
        "University of Sydney", "University of Melbourne", "PwC Australia",
        "Google Australia", "Microsoft Australia", "Atlassian", "Canva",
        "Lendlease", "Stockland", "Mirvac Group"
    };

    private static final String[] PRODUCT_NAMES = {
        "Standard Variable", "Standard Fixed", "Package Variable", "Package Fixed",
        "Investor Variable", "Investor Fixed", "First Home Buyer Variable",
        "Interest Only Variable", "Interest Only Fixed", "Low Doc Variable"
    };

    private static final String[] PROPERTY_TYPES = {"House", "Unit", "Townhouse", "Apartment", "Vacant Land"};
    private static final double[] PROP_TYPE_CDF = {0.45, 0.70, 0.85, 0.95, 1.0};

    private static final String[] INTEREST_TYPES = {"Variable", "Fixed", "Split"};
    private static final double[] INTEREST_TYPE_CDF = {0.55, 0.88, 1.0};

    private static final String[] REPAYMENT_TYPES = {"Principal and Interest", "Interest Only"};
    private static final double[] REPAY_TYPE_CDF = {0.82, 1.0};

    private static final String[] REPAYMENT_FREQS = {"Monthly", "Fortnightly", "Weekly"};
    private static final double[] REPAY_FREQ_CDF = {0.65, 0.88, 1.0};

    // Valid enum statuses + legacy values for quality issue #8
    private static final String[] VALID_STATUSES = {"SETTLED", "DRAFT", "UNDER_ASSESSMENT", "APPROVED"};
    private static final String[] LEGACY_STATUSES = {"Active", "Closed", "Arrears"};

    // State -> suburb/postcode pairs
    private static final Map<String, String[][]> SUBURBS = new LinkedHashMap<>();
    static {
        SUBURBS.put("NSW", new String[][]{
            {"Parramatta", "2150"}, {"Penrith", "2750"}, {"Liverpool", "2170"},
            {"Blacktown", "2148"}, {"Chatswood", "2067"}, {"Hornsby", "2077"},
            {"Bondi", "2026"}, {"Surry Hills", "2010"}, {"Newtown", "2042"},
            {"Castle Hill", "2154"}, {"Epping", "2121"}, {"Ryde", "2112"},
            {"Wollongong", "2500"}, {"Newcastle", "2300"}, {"Gosford", "2250"}
        });
        SUBURBS.put("VIC", new String[][]{
            {"Richmond", "3121"}, {"South Yarra", "3141"}, {"Carlton", "3053"},
            {"Brunswick", "3056"}, {"Footscray", "3011"}, {"Box Hill", "3128"},
            {"Doncaster", "3108"}, {"Glen Waverley", "3150"}, {"Frankston", "3199"},
            {"Geelong", "3220"}, {"Hawthorn", "3122"}, {"Toorak", "3142"},
            {"Brighton", "3186"}, {"Dandenong", "3175"}, {"Cranbourne", "3977"}
        });
        SUBURBS.put("QLD", new String[][]{
            {"Southbank", "4101"}, {"West End", "4101"}, {"Paddington", "4064"},
            {"Toowong", "4066"}, {"Indooroopilly", "4068"}, {"Chermside", "4032"},
            {"Gold Coast", "4217"}, {"Surfers Paradise", "4217"}, {"Cairns", "4870"},
            {"Toowoomba", "4350"}, {"Ipswich", "4305"}, {"Redcliffe", "4020"}
        });
        SUBURBS.put("WA", new String[][]{
            {"Fremantle", "6160"}, {"Subiaco", "6008"}, {"Joondalup", "6027"},
            {"Rockingham", "6168"}, {"Mandurah", "6210"}, {"Scarborough", "6019"},
            {"Cottesloe", "6011"}, {"Claremont", "6010"}
        });
        SUBURBS.put("SA", new String[][]{
            {"Glenelg", "5045"}, {"Norwood", "5067"}, {"Unley", "5061"},
            {"Prospect", "5082"}, {"Salisbury", "5108"}, {"Mount Barker", "5251"}
        });
        SUBURBS.put("TAS", new String[][]{
            {"Sandy Bay", "7005"}, {"Glenorchy", "7010"}, {"Launceston", "7250"}
        });
        SUBURBS.put("ACT", new String[][]{
            {"Belconnen", "2617"}, {"Woden", "2606"}, {"Tuggeranong", "2900"},
            {"Gungahlin", "2912"}, {"Braddon", "2612"}
        });
    }

    private static final String[] STATES = {"NSW", "VIC", "QLD", "WA", "SA", "TAS", "ACT"};
    private static final double[] STATE_CDF = {0.32, 0.60, 0.78, 0.88, 0.95, 0.97, 1.0};

    private static final String[] STREET_NAMES = {
        "High", "George", "Victoria", "King", "Queen", "Elizabeth", "Station",
        "Church", "Main", "Park", "Bridge", "William", "Albert", "Charles",
        "Railway", "Pacific", "Ocean", "Harbour", "Wattle", "Banksia"
    };

    private static final String[] STREET_TYPES = {
        "Street", "Road", "Avenue", "Drive", "Court", "Place", "Crescent",
        "Lane", "Way", "Boulevard", "Parade", "Close"
    };

    // ── Quality issue tracking ──────────────────────────────────────────────────

    private Random rng;
    private Set<Integer> missingEmail;         // ~10% = 50 rows
    private Set<Integer> invalidDates;         // ~5%  = 25 rows
    private Set<Integer> missingPrice;         // ~8%  = 40 rows
    private Set<Integer> duplicateRefs;        // ~3%  = 15 rows
    private Set<Integer> negativeLoanAmt;      // ~2%  = 10 rows
    private Set<Integer> missingAddress;       // ~7%  = 35 rows
    private Set<Integer> impossibleLvr;        // ~3%  = 15 rows
    private Set<Integer> legacyStatus;         // ~15% = 75 rows
    private Set<Integer> mixedDateFmt;         // ~20% = 100 rows
    private Set<Integer> whitespaceNames;      // ~5%  = 25 rows

    @EventListener(ApplicationReadyEvent.class)
    public void generate() {
        rng = new Random(SEED);
        preAssignQualityIssues();

        try {
            Files.createDirectories(OUTPUT_PATH.getParent());

            List<String[]> rows = new ArrayList<>(TOTAL_ROWS);
            for (int i = 0; i < TOTAL_ROWS; i++) {
                rows.add(generateRow(i));
            }

            writeCsv(OUTPUT_PATH, rows);
            log.info("Migration seed portfolio generated: {} ({} rows)", OUTPUT_PATH, rows.size());
            printQualitySummary();
        } catch (IOException e) {
            log.error("Failed to generate seed portfolio CSV", e);
        }
    }

    private void preAssignQualityIssues() {
        Random assignRng = new Random(SEED + 100);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < TOTAL_ROWS; i++) indices.add(i);

        missingEmail = assignIssue(indices, assignRng, 50);
        invalidDates = assignIssue(indices, assignRng, 25);
        missingPrice = assignIssue(indices, assignRng, 40);
        duplicateRefs = assignIssue(indices, assignRng, 15);
        negativeLoanAmt = assignIssue(indices, assignRng, 10);
        missingAddress = assignIssue(indices, assignRng, 35);
        impossibleLvr = assignIssue(indices, assignRng, 15);
        legacyStatus = assignIssue(indices, assignRng, 75);
        mixedDateFmt = assignIssue(indices, assignRng, 100);
        whitespaceNames = assignIssue(indices, assignRng, 25);
    }

    private Set<Integer> assignIssue(List<Integer> indices, Random assignRng, int count) {
        Collections.shuffle(indices, assignRng);
        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < count && i < indices.size(); i++) {
            result.add(indices.get(i));
        }
        return result;
    }

    // ── Row generation ──────────────────────────────────────────────────────────

    private String[] generateRow(int idx) {
        String[] row = new String[HEADERS.length];

        // Date format for this row: default DD/MM/YYYY, mixed gets YYYY-MM-DD or MM-DD-YYYY
        int dateFmt = 0; // 0=DD/MM/YYYY, 1=YYYY-MM-DD, 2=MM-DD-YYYY
        if (mixedDateFmt.contains(idx)) {
            dateFmt = rng.nextBoolean() ? 1 : 2;
        }

        // loan_ref — duplicates use a previously seen ref
        if (duplicateRefs.contains(idx) && idx > 15) {
            int sourceIdx = rng.nextInt(idx - 10) + 1;
            row[0] = String.format("MIG-%06d", sourceIdx);
        } else {
            row[0] = String.format("MIG-%06d", idx + 1);
        }

        // borrower_name
        String firstName = pick(FIRST_NAMES);
        String lastName = pick(LAST_NAMES);
        String borrowerName = firstName + " " + lastName;
        if (whitespaceNames.contains(idx)) {
            borrowerName = applyWhitespaceIssue(borrowerName);
        }
        row[1] = borrowerName;

        // borrower_dob
        if (invalidDates.contains(idx)) {
            row[2] = pickInvalidDate();
        } else {
            row[2] = formatDate(randomDob(), dateFmt);
        }

        // borrower_email
        if (missingEmail.contains(idx)) {
            row[3] = "";
        } else {
            row[3] = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@" + pickEmailDomain();
        }

        // borrower_phone
        row[4] = "04" + String.format("%08d", rng.nextInt(100_000_000));

        // co_borrower_name (40% have one)
        if (rng.nextDouble() < 0.40) {
            String coFirst = pick(FIRST_NAMES);
            String coLast = rng.nextDouble() < 0.7 ? lastName : pick(LAST_NAMES);
            row[5] = coFirst + " " + coLast;
        } else {
            row[5] = "";
        }

        // Address
        String state = pickWeighted(STATES, STATE_CDF);
        String[][] suburbs = SUBURBS.get(state);
        String[] suburbData = suburbs[rng.nextInt(suburbs.length)];
        String suburb = suburbData[0];
        String postcode = suburbData[1];

        if (missingAddress.contains(idx)) {
            row[6] = rng.nextDouble() < 0.4 ? "" : (rng.nextInt(200) + 1) + " " + pick(STREET_NAMES) + " " + pick(STREET_TYPES);
            row[7] = rng.nextDouble() < 0.5 ? "" : suburb;
            row[8] = rng.nextDouble() < 0.3 ? "" : state;
            row[9] = rng.nextDouble() < 0.4 ? "" : postcode;
        } else {
            row[6] = (rng.nextInt(200) + 1) + " " + pick(STREET_NAMES) + " " + pick(STREET_TYPES);
            row[7] = suburb;
            row[8] = state;
            row[9] = postcode;
        }

        // property_type
        String propType = pickWeighted(PROPERTY_TYPES, PROP_TYPE_CDF);
        row[10] = propType;

        // bedrooms
        row[11] = "Vacant Land".equals(propType) ? "" : String.valueOf(rng.nextInt(4) + 1);

        // purchase_price
        double purchasePrice = randomPurchasePrice(state, propType);
        if (missingPrice.contains(idx)) {
            row[12] = "";
        } else {
            row[12] = String.format("%.2f", purchasePrice);
        }

        // loan_amount
        double lvr = randomLvr();
        double loanAmount = Math.round(purchasePrice * lvr / 100.0 * 100.0) / 100.0;
        if (negativeLoanAmt.contains(idx)) {
            loanAmount = -(rng.nextInt(500000) + 50000);
        }
        row[13] = String.format("%.2f", loanAmount);

        // interest_rate (annual %)
        double rate = 5.5 + rng.nextDouble() * 2.0; // 5.5% - 7.5%
        row[14] = String.format("%.2f", rate);

        // loan_term_months
        int term = rng.nextDouble() < 0.80 ? 360 : (rng.nextDouble() < 0.5 ? 300 : 120);
        row[15] = String.valueOf(term);

        // interest_type
        row[16] = pickWeighted(INTEREST_TYPES, INTEREST_TYPE_CDF);

        // repayment_type
        row[17] = pickWeighted(REPAYMENT_TYPES, REPAY_TYPE_CDF);

        // repayment_frequency
        row[18] = pickWeighted(REPAYMENT_FREQS, REPAY_FREQ_CDF);

        // product_name
        row[19] = pick(PRODUCT_NAMES);

        // settlement_date
        LocalDate settleDate = randomSettlementDate();
        if (invalidDates.contains(idx) && rng.nextDouble() < 0.3) {
            row[20] = pickInvalidDate();
        } else {
            row[20] = formatDate(settleDate, dateFmt);
        }

        // loan_status
        if (legacyStatus.contains(idx)) {
            row[21] = pick(LEGACY_STATUSES);
        } else {
            row[21] = pick(VALID_STATUSES);
        }

        // lvr
        if (impossibleLvr.contains(idx)) {
            row[22] = String.format("%.1f", 102.0 + rng.nextDouble() * 48.0); // 102-150%
        } else {
            row[22] = String.format("%.1f", lvr);
        }

        // employer
        row[23] = rng.nextDouble() < 0.08 ? "" : pick(EMPLOYER_NAMES);

        // income_gross_annual
        double income = 55000 + rng.nextDouble() * 195000;
        row[24] = String.format("%.2f", income);

        // expenses_monthly
        double expenses = 2500 + rng.nextDouble() * 5500;
        row[25] = String.format("%.2f", expenses);

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
        int year = 1955 + rng.nextInt(46);
        int month = rng.nextInt(12) + 1;
        int day = rng.nextInt(28) + 1;
        return LocalDate.of(year, month, day);
    }

    private LocalDate randomSettlementDate() {
        double yearD = 2020.0 + rng.nextGaussian() * 2.5;
        int year = Math.max(2015, Math.min(2025, (int) Math.round(yearD)));
        int month = rng.nextInt(12) + 1;
        int day = rng.nextInt(28) + 1;
        return LocalDate.of(year, month, day);
    }

    private double randomPurchasePrice(String state, String propType) {
        double base = switch (state) {
            case "NSW" -> 750000;
            case "VIC" -> 650000;
            case "QLD" -> 520000;
            case "WA" -> 480000;
            case "SA" -> 420000;
            case "ACT" -> 580000;
            case "TAS" -> 400000;
            default -> 450000;
        };
        double typeMultiplier = switch (propType) {
            case "House" -> 1.0;
            case "Unit" -> 0.65;
            case "Townhouse" -> 0.80;
            case "Apartment" -> 0.60;
            case "Vacant Land" -> 0.35;
            default -> 1.0;
        };
        double variation = 0.5 + rng.nextDouble() * 1.0;
        double price = base * typeMultiplier * variation;
        price = Math.max(150_000, Math.min(2_000_000, price));
        return Math.round(price / 100.0) * 100.0;
    }

    private double randomLvr() {
        double lvr = 72.0 + rng.nextGaussian() * 12.0;
        return Math.max(40.0, Math.min(95.0, Math.round(lvr * 10.0) / 10.0));
    }

    private String formatDate(LocalDate date, int format) {
        return switch (format) {
            case 1 -> date.format(YYYY_MM_DD);
            case 2 -> date.format(MM_DD_YYYY);
            default -> date.format(DD_MM_YYYY);
        };
    }

    private String pickInvalidDate() {
        double r = rng.nextDouble();
        if (r < 0.3) return "31/02/2020";
        if (r < 0.5) return "not-a-date";
        if (r < 0.65) return "29/02/2019";
        if (r < 0.8) return "00/13/2021";
        if (r < 0.9) return "2020-13-45";
        return "##/##/####";
    }

    private String applyWhitespaceIssue(String name) {
        double r = rng.nextDouble();
        if (r < 0.25) return "  " + name;              // leading spaces
        if (r < 0.50) return name + "  ";               // trailing spaces
        if (r < 0.70) return name.replace(" ", "  ");   // double spaces
        if (r < 0.85) return name + "\t";               // tab character
        return name.replace(" ", "\u00A0");              // non-breaking space
    }

    // ── CSV writing ─────────────────────────────────────────────────────────────

    private void writeCsv(Path path, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(String.join(",", HEADERS));
            w.newLine();
            for (String[] row : rows) {
                w.write(Arrays.stream(row)
                    .map(PortfolioSeedGenerator::csvEscape)
                    .collect(Collectors.joining(",")));
                w.newLine();
            }
        }
    }

    private static String csvEscape(String val) {
        if (val == null || val.isEmpty()) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\t")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private void printQualitySummary() {
        log.info("=== Seed Portfolio Quality Issues ===");
        log.info("Missing email:       {} ({}%)", missingEmail.size(), pct(missingEmail.size()));
        log.info("Invalid dates:       {} ({}%)", invalidDates.size(), pct(invalidDates.size()));
        log.info("Missing price:       {} ({}%)", missingPrice.size(), pct(missingPrice.size()));
        log.info("Duplicate refs:      {} ({}%)", duplicateRefs.size(), pct(duplicateRefs.size()));
        log.info("Negative loan amt:   {} ({}%)", negativeLoanAmt.size(), pct(negativeLoanAmt.size()));
        log.info("Missing address:     {} ({}%)", missingAddress.size(), pct(missingAddress.size()));
        log.info("Impossible LVR:      {} ({}%)", impossibleLvr.size(), pct(impossibleLvr.size()));
        log.info("Legacy status:       {} ({}%)", legacyStatus.size(), pct(legacyStatus.size()));
        log.info("Mixed date format:   {} ({}%)", mixedDateFmt.size(), pct(mixedDateFmt.size()));
        log.info("Whitespace names:    {} ({}%)", whitespaceNames.size(), pct(whitespaceNames.size()));
    }

    private String pct(int count) {
        return String.format("%.1f", count * 100.0 / TOTAL_ROWS);
    }
}
