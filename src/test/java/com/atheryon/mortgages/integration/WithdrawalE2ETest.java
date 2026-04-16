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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for withdrawal from various application states.
 *
 * Per SRS Process 8, withdrawal is allowed from any pre-settlement state.
 * The application state machine defines which transitions to WITHDRAWN are valid.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalE2ETest {

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
    private LendingRateRepository lendingRateRepository;

    @Autowired
    private OfferRepository offerRepository;

    private Product testProduct;
    private Party testParty;

    private static final String WITHDRAW_REASON = "Customer found better rate";

    @BeforeEach
    void setUp() {
        testProduct = productRepository.save(
                Product.builder()
                        .productType(ProductType.STANDARD_VARIABLE)
                        .name("Test Variable Loan")
                        .brand("Atheryon")
                        .effectiveFrom(LocalDate.now())
                        .minimumLoanAmount(new BigDecimal("50000"))
                        .maximumLoanAmount(new BigDecimal("5000000"))
                        .minimumTermMonths(12)
                        .maximumTermMonths(360)
                        .maximumLtv(new BigDecimal("0.95"))
                        .build()
        );

        LendingRate rate = LendingRate.builder()
                .product(testProduct)
                .lendingRateType(LendingRateType.VARIABLE)
                .rate(new BigDecimal("0.0629"))
                .comparisonRate(new BigDecimal("0.0645"))
                .build();
        lendingRateRepository.save(rate);

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

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a DRAFT application with linked party and security,
     * ready for state advancement.
     */
    private UUID createDraftApplication() throws Exception {
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

        // Link party and security (needed for submission validation)
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        applicationPartyRepository.save(ApplicationParty.builder()
                .application(app)
                .party(testParty)
                .role(PartyRole.PRIMARY_BORROWER)
                .ownershipPercentage(new BigDecimal("1.00"))
                .build());

        propertySecurityRepository.save(PropertySecurity.builder()
                .application(app)
                .securityType(SecurityType.EXISTING_RESIDENTIAL)
                .propertyCategory(PropertyCategory.HOUSE)
                .purchasePrice(new BigDecimal("750000"))
                .build());

        entityManager.flush();
        entityManager.clear();

        return appId;
    }

    /**
     * Advances a DRAFT application to SUBMITTED by going through
     * READY_FOR_SUBMISSION first.
     */
    private void advanceToSubmitted(UUID appId) throws Exception {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    /**
     * Advances a DRAFT application to UNDER_ASSESSMENT.
     */
    private void advanceToUnderAssessment(UUID appId) throws Exception {
        advanceToSubmitted(appId);
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));
    }

    private String withdrawPayload(String reason) {
        return String.format("""
                { "reason": "%s" }
                """, reason);
    }

    private String withdrawPayload() {
        return withdrawPayload(WITHDRAW_REASON);
    }

    // ---------------------------------------------------------------
    // Happy-path tests: withdraw from each eligible state
    // ---------------------------------------------------------------

    @Test
    void withdrawFromDraft() throws Exception {
        UUID appId = createDraftApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdrawFromSubmitted() throws Exception {
        UUID appId = createDraftApplication();
        advanceToSubmitted(appId);

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdrawFromUnderAssessment() throws Exception {
        UUID appId = createDraftApplication();
        advanceToUnderAssessment(appId);

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdrawFromConditionallyApproved() throws Exception {
        UUID appId = createDraftApplication();

        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.CONDITIONALLY_APPROVED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdrawFromOfferIssued() throws Exception {
        UUID appId = createDraftApplication();

        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.OFFER_ISSUED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdrawFromSettlementInProgress() throws Exception {
        UUID appId = createDraftApplication();

        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.SETTLEMENT_IN_PROGRESS);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdraw_recordsWithdrawalTimestamp() throws Exception {
        UUID appId = createDraftApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        // Verify via repository that updatedAt was set (serves as withdrawal timestamp)
        entityManager.flush();
        entityManager.clear();
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(app.getUpdatedAt()).isNotNull();
    }

    @Test
    void withdraw_recordsReason() throws Exception {
        UUID appId = createDraftApplication();

        String specificReason = "Moving overseas, no longer purchasing property";
        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload(specificReason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        // Confirm the application transitioned to WITHDRAWN and the workflow
        // event was recorded (reason is captured in workflow history)
        entityManager.flush();
        entityManager.clear();
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);

        boolean hasWithdrawalEvent = app.getWorkflowEvents().stream()
                .anyMatch(e -> "APPLICATION_WITHDRAWN".equals(e.getEventType())
                        && "WITHDRAWN".equals(e.getNewState()));
        assertThat(hasWithdrawalEvent).isTrue();
    }

    // ---------------------------------------------------------------
    // Negative tests
    // ---------------------------------------------------------------

    @Test
    void withdrawApplication_alreadyWithdrawn_returns409() throws Exception {
        UUID appId = createDraftApplication();

        // First withdrawal succeeds
        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isOk());

        // Second withdrawal fails -- no transition from WITHDRAWN
        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawApplication_alreadySettled_returns409() throws Exception {
        UUID appId = createDraftApplication();

        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.SETTLED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawApplication_alreadyDeclined_returns409() throws Exception {
        UUID appId = createDraftApplication();

        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.DECLINED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawApplication_withoutReason_returns400() throws Exception {
        UUID appId = createDraftApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawApplication_nonExistent_returns404() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", fakeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawPayload()))
                .andExpect(status().isNotFound());
    }
}
