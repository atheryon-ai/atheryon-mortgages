package com.atheryon.mortgages.integration;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.*;
import com.atheryon.mortgages.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for SRS Process 3 - Application Submission.
 *
 * Validates the full submission flow including state machine transitions,
 * business rule validation for required parties and securities, and
 * error handling for invalid state transitions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationSubmissionE2ETest {

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

    private Product testProduct;
    private Party testParty;

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

    // ----------------------------------------------------------------
    // Helper: create an application via POST and return its UUID
    // ----------------------------------------------------------------

    private UUID createApplication(Channel channel, LoanPurpose purpose) throws Exception {
        String payload = String.format("""
                {
                    "productId": "%s",
                    "channel": "%s",
                    "purpose": "%s",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId(), channel, purpose);

        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    private UUID createApplication() throws Exception {
        return createApplication(Channel.DIRECT_ONLINE, LoanPurpose.PURCHASE);
    }

    // ----------------------------------------------------------------
    // Helper: link party and security, advance to READY_FOR_SUBMISSION
    // ----------------------------------------------------------------

    private void linkPartyToApplication(LoanApplication app) {
        applicationPartyRepository.save(
                ApplicationParty.builder()
                        .application(app)
                        .party(testParty)
                        .role(PartyRole.PRIMARY_BORROWER)
                        .ownershipPercentage(new BigDecimal("1.00"))
                        .build()
        );
    }

    private void linkSecurityToApplication(LoanApplication app) {
        propertySecurityRepository.save(
                PropertySecurity.builder()
                        .application(app)
                        .securityType(SecurityType.EXISTING_RESIDENTIAL)
                        .propertyCategory(PropertyCategory.HOUSE)
                        .streetNumber("42")
                        .streetName("Test")
                        .streetType("Street")
                        .suburb("Sydney")
                        .state("NSW")
                        .postcode("2000")
                        .purchasePrice(new BigDecimal("750000"))
                        .build()
        );
    }

    private void advanceToReadyForSubmission(UUID appId) {
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Full preparation pipeline: create app via POST, link party + security,
     * set to READY_FOR_SUBMISSION, flush and clear persistence context.
     */
    private UUID prepareReadyApplication(Channel channel, LoanPurpose purpose) throws Exception {
        UUID appId = createApplication(channel, purpose);
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        linkPartyToApplication(app);
        linkSecurityToApplication(app);
        entityManager.flush();
        entityManager.clear();
        advanceToReadyForSubmission(appId);
        return appId;
    }

    private UUID prepareReadyApplication() throws Exception {
        return prepareReadyApplication(Channel.DIRECT_ONLINE, LoanPurpose.PURCHASE);
    }

    // ================================================================
    // Happy-path tests
    // ================================================================

    @Test
    void submitApplication_withAllRequirements_returnsSubmitted() throws Exception {
        UUID appId = prepareReadyApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void submitApplication_setsSubmittedTimestamp() throws Exception {
        UUID appId = prepareReadyApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }

    @Test
    void submitApplication_brokerChannel() throws Exception {
        UUID appId = prepareReadyApplication(Channel.BROKER, LoanPurpose.PURCHASE);

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.channel").value("BROKER"));
    }

    @Test
    void submitApplication_refinancePurpose() throws Exception {
        UUID appId = prepareReadyApplication(Channel.DIRECT_ONLINE, LoanPurpose.REFINANCE);

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.purpose").value("REFINANCE"));
    }

    // ================================================================
    // Negative tests
    // ================================================================

    @Test
    void submitApplication_withoutParty_returns422() throws Exception {
        UUID appId = createApplication();
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();

        // Link security but NOT party
        linkSecurityToApplication(app);
        entityManager.flush();
        entityManager.clear();

        advanceToReadyForSubmission(appId);

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void submitApplication_withoutSecurity_returns422() throws Exception {
        UUID appId = createApplication();
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();

        // Link party but NOT security
        linkPartyToApplication(app);
        entityManager.flush();
        entityManager.clear();

        advanceToReadyForSubmission(appId);

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void submitApplication_withoutPartyOrSecurity_returns422() throws Exception {
        UUID appId = createApplication();

        advanceToReadyForSubmission(appId);

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void submitApplication_fromDraftWithoutReadyState_returns409() throws Exception {
        // Create app, link party + security, but do NOT advance to READY_FOR_SUBMISSION.
        // The app remains in DRAFT, so the state machine rejects DRAFT -> SUBMITTED.
        UUID appId = createApplication();
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        linkPartyToApplication(app);
        linkSecurityToApplication(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void submitApplication_alreadySubmitted_returns409() throws Exception {
        UUID appId = prepareReadyApplication();

        // First submission succeeds
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Second submission from SUBMITTED state should be rejected
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void submitApplication_withdrawnApplication_returns409() throws Exception {
        UUID appId = prepareReadyApplication();

        // Submit
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk());

        // Withdraw
        mockMvc.perform(post("/api/v1/applications/{id}/withdraw", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Changed my mind" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        // Try to submit again from WITHDRAWN -- no valid transition
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isConflict());
    }
}
