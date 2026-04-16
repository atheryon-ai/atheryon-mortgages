package com.atheryon.mortgages.integration;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.*;
import com.atheryon.mortgages.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DecisioningE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private ApplicationPartyRepository applicationPartyRepository;

    @Autowired
    private PropertySecurityRepository propertySecurityRepository;

    @Autowired
    private FinancialSnapshotRepository financialSnapshotRepository;

    @Autowired
    private DecisionRecordRepository decisionRecordRepository;

    @Autowired
    private DecisionConditionRepository decisionConditionRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private Product testProduct;
    private Party testParty;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .productType(ProductType.STANDARD_VARIABLE)
                .name("Test Variable Home Loan")
                .brand("Atheryon")
                .effectiveFrom(LocalDate.now())
                .minimumLoanAmount(new BigDecimal("50000"))
                .maximumLoanAmount(new BigDecimal("5000000"))
                .minimumTermMonths(12)
                .maximumTermMonths(360)
                .maximumLtv(new BigDecimal("0.95"))
                .build();

        LendingRate rate = LendingRate.builder()
                .product(testProduct)
                .lendingRateType(LendingRateType.VARIABLE)
                .rate(new BigDecimal("0.0629"))
                .comparisonRate(new BigDecimal("0.0645"))
                .build();
        testProduct.getLendingRates().add(rate);
        testProduct = productRepository.save(testProduct);

        testParty = partyRepository.save(
                Party.builder()
                        .partyType(PartyType.INDIVIDUAL)
                        .firstName("Jane")
                        .surname("Smith")
                        .email("jane.smith@example.com")
                        .dateOfBirth(LocalDate.of(1985, 6, 15))
                        .build()
        );
    }

    // -----------------------------------------------------------------------
    // Helper: Create an application, link party + security, advance to VERIFIED
    // -----------------------------------------------------------------------

    private UUID createVerifiedApplication(BigDecimal requestedAmount, BigDecimal purchasePrice) throws Exception {
        String createPayload = String.format("""
                {
                    "productId": "%s",
                    "channel": "DIRECT_ONLINE",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": %s,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId(), requestedAmount.toPlainString());

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(json.get("id").asText());

        // Link party
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        applicationPartyRepository.save(ApplicationParty.builder()
                .application(app)
                .party(testParty)
                .role(PartyRole.PRIMARY_BORROWER)
                .ownershipPercentage(new BigDecimal("1.00"))
                .build());

        // Link security
        propertySecurityRepository.save(PropertySecurity.builder()
                .application(app)
                .securityType(SecurityType.EXISTING_RESIDENTIAL)
                .propertyCategory(PropertyCategory.HOUSE)
                .streetNumber("42")
                .streetName("Wallaby")
                .streetType("Way")
                .suburb("Sydney")
                .state("NSW")
                .postcode("2000")
                .purchasePrice(purchasePrice)
                .build());

        // Set status to READY_FOR_SUBMISSION
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        // Submit
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Assess
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));

        // Verify
        mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        return appId;
    }

    private void addFinancialSnapshot(UUID appId, BigDecimal grossIncome, BigDecimal netIncome,
                                      BigDecimal declaredExpenses, BigDecimal hemBenchmark) {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();

        FinancialSnapshot snapshot = FinancialSnapshot.builder()
                .application(app)
                .totalGrossAnnualIncome(grossIncome)
                .totalNetAnnualIncome(netIncome)
                .declaredMonthlyExpenses(declaredExpenses)
                .hemMonthlyBenchmark(hemBenchmark)
                .assessedMonthlyExpenses(declaredExpenses.max(hemBenchmark))
                .capturedAt(LocalDateTime.now())
                .build();

        IncomeItem incomeItem = IncomeItem.builder()
                .financialSnapshot(snapshot)
                .incomeType(IncomeType.SALARY)
                .grossAnnualAmount(grossIncome)
                .netAnnualAmount(netIncome)
                .frequency("MONTHLY")
                .verified(true)
                .build();
        snapshot.getIncomeItems().add(incomeItem);

        financialSnapshotRepository.save(snapshot);
        entityManager.flush();
        entityManager.clear();
    }

    private void addCreditScore(UUID appId, int creditScore) {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        DecisionRecord record = DecisionRecord.builder()
                .application(app)
                .decisionType(DecisionType.AUTOMATED)
                .outcome(DecisionOutcome.REFERRED_TO_UNDERWRITER)
                .decisionDate(LocalDateTime.now())
                .decidedBy("SYSTEM")
                .creditScore(creditScore)
                .build();
        decisionRecordRepository.save(record);
        app.setDecisionRecord(record);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();
    }

    private void addVerifiedDocument(UUID appId) {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        Document doc = Document.builder()
                .application(app)
                .documentType(DocumentType.PAYSLIP)
                .documentCategory(DocumentCategory.INCOME)
                .status(DocumentStatus.VERIFIED)
                .fileName("payslip_202603.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(102400)
                .storageReference("s3://docs/payslip_202603.pdf")
                .uploadedBy("jane.smith@example.com")
                .verifiedBy("assessor@atheryon.com")
                .verifiedAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);
        entityManager.flush();
        entityManager.clear();
    }

    private void addAllConsents(UUID appId) {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        for (ConsentType type : ConsentType.values()) {
            consentRecordRepository.save(ConsentRecord.builder()
                    .application(app)
                    .consentType(type)
                    .granted(true)
                    .grantedAt(LocalDateTime.now())
                    .build());
        }
        entityManager.flush();
        entityManager.clear();
    }

    // =======================================================================
    // AUTOMATED DECISION TESTS
    // =======================================================================

    @Test
    void automatedDecision_autoApprove_allConditionsMet() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        // Financial: good income, low expenses, no liabilities
        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        // Credit score 750 (>= 700 threshold)
        addCreditScore(appId, 750);

        // All documents verified
        addVerifiedDocument(appId);

        // All consents granted
        addAllConsents(appId);

        // Run serviceability calculation first to populate snapshot outcomes
        mockMvc.perform(get("/api/v1/applications/{id}/serviceability", appId))
                .andExpect(status().isOk());

        // POST decision (automated, no body)
        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String outcome = decisionJson.get("outcome").asText();
        // LTV = 500000/750000 = 66.7% (under 80%), credit 750, good serviceability
        assertThat(outcome).isIn("APPROVED", "REFERRED_TO_UNDERWRITER");
    }

    @Test
    void automatedDecision_autoDecline_serviceabilityFail() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("1000000"), new BigDecimal("750000"));

        // Very low income, high expenses
        addFinancialSnapshot(appId,
                new BigDecimal("30000"), new BigDecimal("22000"),
                new BigDecimal("5000"), new BigDecimal("4000"));

        addVerifiedDocument(appId);
        addAllConsents(appId);

        // Run serviceability to populate FAIL outcome
        mockMvc.perform(get("/api/v1/applications/{id}/serviceability", appId))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String outcome = decisionJson.get("outcome").asText();
        assertThat(outcome).isEqualTo("DECLINED");
    }

    @Test
    void automatedDecision_autoDecline_ltvOver95() throws Exception {
        // LTV = 720000 / 750000 = 96% (> 95%)
        UUID appId = createVerifiedApplication(new BigDecimal("720000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        addVerifiedDocument(appId);
        addAllConsents(appId);

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(decisionJson.get("outcome").asText()).isEqualTo("DECLINED");
    }

    @Test
    void automatedDecision_autoDecline_lowCreditScore() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        // Credit score 350 (< 400 threshold)
        addCreditScore(appId, 350);

        addVerifiedDocument(appId);
        addAllConsents(appId);

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(decisionJson.get("outcome").asText()).isEqualTo("DECLINED");
    }

    @Test
    void automatedDecision_referToUnderwriter_marginalCase() throws Exception {
        // LTV = 650000 / 750000 = 86.7% (between 80-95%, will fail auto-approve ltvOk check)
        UUID appId = createVerifiedApplication(new BigDecimal("650000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        // Credit score 600 (between 400-699, not auto-approve but not decline either)
        addCreditScore(appId, 600);

        addVerifiedDocument(appId);
        addAllConsents(appId);

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(decisionJson.get("outcome").asText()).isEqualTo("REFERRED_TO_UNDERWRITER");
    }

    @Test
    void automatedDecision_referToUnderwriter_highLtv() throws Exception {
        // LTV = 690000 / 750000 = 92% (> 90% but <= 95%)
        UUID appId = createVerifiedApplication(new BigDecimal("690000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        addCreditScore(appId, 750);

        addVerifiedDocument(appId);
        addAllConsents(appId);

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("AUTOMATED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(decisionJson.get("outcome").asText()).isEqualTo("REFERRED_TO_UNDERWRITER");
    }

    // =======================================================================
    // MANUAL DECISION TESTS
    // =======================================================================

    @Test
    void manualDecision_approve() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        String decisionPayload = """
                {
                    "outcome": "APPROVED",
                    "decidedBy": "underwriter1"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decisionType").value("MANUAL"))
                .andReturn();

        // Verify application status is APPROVED
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void manualDecision_conditionallyApproveWithConditions() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        String decisionPayload = """
                {
                    "outcome": "CONDITIONALLY_APPROVED",
                    "decidedBy": "underwriter1",
                    "conditions": ["Provide updated payslips", "Complete property valuation"]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONDITIONALLY_APPROVED"))
                .andExpect(jsonPath("$.decisionType").value("MANUAL"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode conditions = decisionJson.get("conditions");
        assertThat(conditions).isNotNull();
        assertThat(conditions.size()).isEqualTo(2);
        assertThat(conditions.get(0).asText()).isEqualTo("Provide updated payslips");
        assertThat(conditions.get(1).asText()).isEqualTo("Complete property valuation");

        // Verify application status
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONDITIONALLY_APPROVED"));
    }

    @Test
    void manualDecision_decline() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        String decisionPayload = """
                {
                    "outcome": "DECLINED",
                    "decidedBy": "underwriter1",
                    "reasons": ["Insufficient income", "Poor credit history"]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DECLINED"))
                .andExpect(jsonPath("$.decisionType").value("MANUAL"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode declineReasons = decisionJson.get("declineReasons");
        assertThat(declineReasons).isNotNull();
        assertThat(declineReasons.size()).isEqualTo(2);
        assertThat(declineReasons.get(0).asText()).isEqualTo("Insufficient income");
        assertThat(declineReasons.get(1).asText()).isEqualTo("Poor credit history");

        // Verify application status
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void overrideDecision_referredToApproved() throws Exception {
        // LTV = 650000 / 750000 = 86.7% — will get REFERRED
        UUID appId = createVerifiedApplication(new BigDecimal("650000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        addCreditScore(appId, 600);
        addVerifiedDocument(appId);
        addAllConsents(appId);

        // Run automated decision — expect REFERRED_TO_UNDERWRITER
        MvcResult autoResult = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REFERRED_TO_UNDERWRITER"))
                .andReturn();

        // Override to APPROVED
        String overridePayload = """
                {
                    "outcome": "APPROVED",
                    "decidedBy": "senior_underwriter1"
                }
                """;

        mockMvc.perform(post("/api/v1/applications/{id}/decision/override", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overridePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decisionType").value("MANUAL"));

        // Verify application status is APPROVED
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // =======================================================================
    // CONDITION SATISFACTION
    // =======================================================================

    @Test
    void satisfyCondition_marksConditionSatisfied() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        // Create a conditionally approved decision with conditions
        String decisionPayload = """
                {
                    "outcome": "CONDITIONALLY_APPROVED",
                    "decidedBy": "underwriter1",
                    "conditions": ["Provide updated payslips"]
                }
                """;

        MvcResult decisionResult = mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONDITIONALLY_APPROVED"))
                .andReturn();

        JsonNode decisionJson = objectMapper.readTree(decisionResult.getResponse().getContentAsString());
        UUID decisionId = UUID.fromString(decisionJson.get("id").asText());

        // Get condition ID from the database
        entityManager.flush();
        entityManager.clear();
        DecisionRecord savedDecision = decisionRecordRepository.findById(decisionId).orElseThrow();
        assertThat(savedDecision.getConditions()).isNotEmpty();
        UUID conditionId = savedDecision.getConditions().get(0).getId();

        // Satisfy the condition
        mockMvc.perform(post("/api/v1/applications/{id}/conditions/{conditionId}/satisfy", appId, conditionId))
                .andExpect(status().isOk());

        // Verify condition is satisfied in DB
        entityManager.flush();
        entityManager.clear();
        DecisionCondition condition = decisionConditionRepository.findById(conditionId).orElseThrow();
        assertThat(condition.getStatus()).isEqualTo(ConditionStatus.SATISFIED);
        assertThat(condition.getSatisfiedDate()).isNotNull();
    }

    // =======================================================================
    // SERVICEABILITY
    // =======================================================================

    @Test
    void getServiceability_returnsCalculation() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        addFinancialSnapshot(appId,
                new BigDecimal("120000"), new BigDecimal("90000"),
                new BigDecimal("2000"), new BigDecimal("1750"));

        MvcResult result = mockMvc.perform(get("/api/v1/applications/{id}/serviceability", appId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode serviceJson = objectMapper.readTree(result.getResponse().getContentAsString());

        // Verify all serviceability fields are present
        assertThat(serviceJson.has("netDisposableIncome")).isTrue();
        assertThat(serviceJson.has("debtServiceRatio")).isTrue();
        assertThat(serviceJson.has("uncommittedMonthlyIncome")).isTrue();
        assertThat(serviceJson.has("outcome")).isTrue();

        // NDI should be positive with good income
        BigDecimal ndi = new BigDecimal(serviceJson.get("netDisposableIncome").asText());
        assertThat(ndi).isGreaterThan(BigDecimal.ZERO);

        String outcome = serviceJson.get("outcome").asText();
        assertThat(outcome).isIn("PASS", "FAIL", "MARGINAL");
    }

    @Test
    void getServiceability_withoutFinancialData_returns422() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        // No financial snapshot added — should return 422
        mockMvc.perform(get("/api/v1/applications/{id}/serviceability", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    // =======================================================================
    // NEGATIVE TESTS
    // =======================================================================

    @Test
    void decision_onDraftApplication_returns422() throws Exception {
        // Create app but do NOT advance past DRAFT
        String createPayload = String.format("""
                {
                    "productId": "%s",
                    "channel": "DIRECT_ONLINE",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId());

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(json.get("id").asText());

        // Attempt automated decision on DRAFT — should fail with 422
        mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void decision_onSubmittedApplication_returns422() throws Exception {
        // Create and submit, but do NOT verify
        String createPayload = String.format("""
                {
                    "productId": "%s",
                    "channel": "DIRECT_ONLINE",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId());

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(json.get("id").asText());

        // Link party and security for submit validation
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        applicationPartyRepository.save(ApplicationParty.builder()
                .application(app)
                .party(testParty)
                .role(PartyRole.PRIMARY_BORROWER)
                .build());
        propertySecurityRepository.save(PropertySecurity.builder()
                .application(app)
                .securityType(SecurityType.EXISTING_RESIDENTIAL)
                .purchasePrice(new BigDecimal("750000"))
                .build());
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        // Submit
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Attempt automated decision on SUBMITTED — should fail with 422
        mockMvc.perform(post("/api/v1/applications/{id}/decision", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void decision_onAlreadyDeclined_returns409or422() throws Exception {
        UUID appId = createVerifiedApplication(new BigDecimal("500000"), new BigDecimal("750000"));

        // Manually decline the application first
        String declinePayload = """
                {
                    "outcome": "DECLINED",
                    "decidedBy": "underwriter1",
                    "reasons": ["Insufficient income"]
                }
                """;

        mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(declinePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DECLINED"));

        // Attempt another decision on already DECLINED app — should get 409 or 422
        String secondPayload = """
                {
                    "outcome": "APPROVED",
                    "decidedBy": "underwriter2"
                }
                """;

        mockMvc.perform(post("/api/v1/applications/{id}/decision", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().is4xxClientError());
    }
}
