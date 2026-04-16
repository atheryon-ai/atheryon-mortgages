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
 * End-to-end integration tests for SRS Process 6 - Offer and Acceptance.
 *
 * Covers offer generation from approved applications, offer acceptance/decline,
 * LMI determination based on LTV ratio, and negative/edge-case scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfferAcceptanceE2ETest {

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
    // Helper: create an application and advance it to APPROVED status
    // ---------------------------------------------------------------

    private UUID createApprovedApplication(BigDecimal requestedAmount, BigDecimal purchasePrice) throws Exception {
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
                .purchasePrice(purchasePrice)
                .build());

        // Advance to READY_FOR_SUBMISSION
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        // Submit
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk());

        // Begin assessment
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk());

        // Complete verification
        mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isOk());

        // Shortcut: set status to APPROVED via repo (decision engine needs
        // lending rates and financial data to auto-approve)
        entityManager.flush();
        entityManager.clear();
        app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.APPROVED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        return appId;
    }

    private UUID createApprovedApplication() throws Exception {
        return createApprovedApplication(
                new BigDecimal("500000"),
                new BigDecimal("750000")
        );
    }

    // ---------------------------------------------------------------
    // Happy-path tests
    // ---------------------------------------------------------------

    @Test
    void generateOffer_forApprovedApplication_returnsIssued() throws Exception {
        UUID appId = createApprovedApplication();

        mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offerStatus").value("ISSUED"))
                .andExpect(jsonPath("$.approvedAmount").isNotEmpty())
                .andExpect(jsonPath("$.interestRate").isNotEmpty())
                .andExpect(jsonPath("$.expiryDate").isNotEmpty())
                .andExpect(jsonPath("$.estimatedMonthlyRepayment").isNumber());
    }

    @Test
    void generateOffer_setsCorrectTerms() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult result = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offer = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(offer.get("termMonths").asInt()).isEqualTo(360);
        assertThat(new BigDecimal(offer.get("interestRate").asText()))
                .isEqualByComparingTo(new BigDecimal("0.0629"));
    }

    @Test
    void acceptOffer_changesStatusToAccepted() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        String acceptPayload = """
                {
                    "acceptedBy": "jane.smith@example.com",
                    "acceptanceMethod": "ELECTRONIC"
                }
                """;

        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTED"));
    }

    @Test
    void acceptOffer_updatesApplicationStatus() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        String acceptPayload = """
                {
                    "acceptedBy": "jane.smith@example.com",
                    "acceptanceMethod": "ELECTRONIC"
                }
                """;

        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER_ACCEPTED"));
    }

    @Test
    void getOffer_returnsOfferDetails() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        mockMvc.perform(get("/api/v1/offers/{id}", offerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(offerId.toString()))
                .andExpect(jsonPath("$.offerStatus").value("ISSUED"))
                .andExpect(jsonPath("$.approvedAmount").isNotEmpty())
                .andExpect(jsonPath("$.interestRate").isNotEmpty())
                .andExpect(jsonPath("$.termMonths").isNumber())
                .andExpect(jsonPath("$.estimatedMonthlyRepayment").isNumber())
                .andExpect(jsonPath("$.offerDate").isNotEmpty())
                .andExpect(jsonPath("$.expiryDate").isNotEmpty());
    }

    @Test
    void declineOffer_changesOfferAndApplicationStatus() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        mockMvc.perform(post("/api/v1/offers/{id}/decline", offerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("DECLINED"));

        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void generateOffer_lmiRequired_whenHighLtv() throws Exception {
        // LTV = 680000 / 750000 = 90.7% -> LMI required
        UUID appId = createApprovedApplication(
                new BigDecimal("680000"),
                new BigDecimal("750000")
        );

        mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lmiRequired").value(true));
    }

    @Test
    void generateOffer_lmiNotRequired_whenLowLtv() throws Exception {
        // LTV = 500000 / 750000 = 66.7% -> no LMI
        UUID appId = createApprovedApplication(
                new BigDecimal("500000"),
                new BigDecimal("750000")
        );

        mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lmiRequired").value(false));
    }

    // ---------------------------------------------------------------
    // Negative tests
    // ---------------------------------------------------------------

    @Test
    void generateOffer_forNonApprovedApp_returns422() throws Exception {
        // Create a DRAFT application (don't advance it)
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

        mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void generateOffer_forDeclinedApp_returns422() throws Exception {
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

        // Set status to DECLINED via repo
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.DECLINED);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void acceptOffer_alreadyAccepted_returns422() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        String acceptPayload = """
                {
                    "acceptedBy": "jane.smith@example.com",
                    "acceptanceMethod": "ELECTRONIC"
                }
                """;

        // First accept succeeds
        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk());

        // Second accept fails
        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void acceptOffer_expiredOffer_returns422() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        // Set expiry date to yesterday
        Offer offer = offerRepository.findById(offerId).orElseThrow();
        offer.setExpiryDate(LocalDate.now().minusDays(1));
        offerRepository.save(offer);
        entityManager.flush();
        entityManager.clear();

        String acceptPayload = """
                {
                    "acceptedBy": "jane.smith@example.com",
                    "acceptanceMethod": "ELECTRONIC"
                }
                """;

        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getOffer_nonExistent_returns404() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/offers/{id}", fakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptOffer_missingAcceptedBy_returns400() throws Exception {
        UUID appId = createApprovedApplication();

        MvcResult offerResult = mockMvc.perform(post("/api/v1/offers/application/{applicationId}", appId))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode offerJson = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        UUID offerId = UUID.fromString(offerJson.get("id").asText());

        // Empty body
        mockMvc.perform(post("/api/v1/offers/{id}/accept", offerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
