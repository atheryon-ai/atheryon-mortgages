package com.atheryon.mortgages.integration;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.Party;
import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.*;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.PartyRepository;
import com.atheryon.mortgages.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E tests for SRS Process 2 - Application Initiation/Capture.
 *
 * Validates the full application CRUD lifecycle: creation across channels
 * and purposes, updates, retrieval, pagination, filtering, assignment,
 * and negative/edge cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationCaptureE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    private Product testProduct;
    private Party testParty;

    @BeforeEach
    void setUp() {
        testProduct = productRepository.save(Product.builder()
                .productType(ProductType.STANDARD_VARIABLE)
                .name("Test Variable Loan")
                .brand("Atheryon")
                .effectiveFrom(LocalDate.now())
                .minimumLoanAmount(new BigDecimal("50000"))
                .maximumLoanAmount(new BigDecimal("5000000"))
                .minimumTermMonths(12)
                .maximumTermMonths(360)
                .maximumLtv(new BigDecimal("0.95"))
                .build());

        testParty = partyRepository.save(Party.builder()
                .partyType(PartyType.INDIVIDUAL)
                .firstName("Jane")
                .surname("Smith")
                .email("jane.smith@example.com")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .build());
    }

    // -----------------------------------------------------------------------
    // Happy-path tests
    // -----------------------------------------------------------------------

    @Test
    void createApplication_returnsCreatedWithDraftStatus() throws Exception {
        String payload = String.format("""
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

        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.applicationNumber").isNotEmpty())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String applicationNumber = json.get("applicationNumber").asText();
        assertThat(applicationNumber).startsWith("ATH-");
    }

    @Test
    void createApplication_allChannels() throws Exception {
        String[] channels = {"BROKER", "DIRECT_ONLINE", "DIRECT_BRANCH", "DIRECT_PHONE"};

        for (String channel : channels) {
            String payload = String.format("""
                    {
                        "productId": "%s",
                        "channel": "%s",
                        "purpose": "PURCHASE",
                        "occupancyType": "OWNER_OCCUPIED",
                        "requestedAmount": 500000,
                        "termMonths": 360,
                        "interestType": "VARIABLE",
                        "repaymentType": "PRINCIPAL_AND_INTEREST"
                    }
                    """, testProduct.getId(), channel);

            mockMvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.channel").value(channel));
        }
    }

    @Test
    void createApplication_allPurposes() throws Exception {
        String[] purposes = {"PURCHASE", "REFINANCE", "CONSTRUCTION", "EQUITY_RELEASE"};

        for (String purpose : purposes) {
            String payload = String.format("""
                    {
                        "productId": "%s",
                        "channel": "DIRECT_ONLINE",
                        "purpose": "%s",
                        "occupancyType": "OWNER_OCCUPIED",
                        "requestedAmount": 500000,
                        "termMonths": 360,
                        "interestType": "VARIABLE",
                        "repaymentType": "PRINCIPAL_AND_INTEREST"
                    }
                    """, testProduct.getId(), purpose);

            mockMvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.purpose").value(purpose));
        }
    }

    @Test
    void updateApplication_changesFields() throws Exception {
        // Create
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

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(createJson.get("id").asText());

        // Update
        String updatePayload = """
                {
                    "requestedAmount": 600000,
                    "termMonths": 300
                }
                """;

        mockMvc.perform(patch("/api/v1/applications/{id}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAmount").value(600000))
                .andExpect(jsonPath("$.termMonths").value(300));
    }

    @Test
    void getApplicationById_returnsCorrectData() throws Exception {
        // Create
        String createPayload = String.format("""
                {
                    "productId": "%s",
                    "channel": "BROKER",
                    "purpose": "REFINANCE",
                    "occupancyType": "INVESTMENT",
                    "requestedAmount": 750000,
                    "termMonths": 240,
                    "interestType": "FIXED",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId());

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(createJson.get("id").asText());

        // Retrieve
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.channel").value("BROKER"))
                .andExpect(jsonPath("$.purpose").value("REFINANCE"))
                .andExpect(jsonPath("$.occupancyType").value("INVESTMENT"))
                .andExpect(jsonPath("$.requestedAmount").value(750000))
                .andExpect(jsonPath("$.termMonths").value(240))
                .andExpect(jsonPath("$.interestType").value("FIXED"))
                .andExpect(jsonPath("$.repaymentType").value("PRINCIPAL_AND_INTEREST"))
                .andExpect(jsonPath("$.applicationNumber").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void listApplications_returnsPaginatedResults() throws Exception {
        // Create 3 applications
        for (int i = 0; i < 3; i++) {
            String payload = String.format("""
                    {
                        "productId": "%s",
                        "channel": "DIRECT_ONLINE",
                        "purpose": "PURCHASE",
                        "occupancyType": "OWNER_OCCUPIED",
                        "requestedAmount": %d,
                        "termMonths": 360,
                        "interestType": "VARIABLE",
                        "repaymentType": "PRINCIPAL_AND_INTEREST"
                    }
                    """, testProduct.getId(), 400000 + (i * 100000));

            mockMvc.perform(post("/api/v1/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated());
        }

        // List all
        MvcResult listResult = mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andReturn();

        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(listJson.get("content").size()).isGreaterThanOrEqualTo(3);
        assertThat(listJson.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void listApplications_filterByStatus() throws Exception {
        // Create an application (DRAFT status)
        String payload = String.format("""
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

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Filter by DRAFT status
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = json.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        for (JsonNode app : content) {
            assertThat(app.get("status").asText()).isEqualTo("DRAFT");
        }
    }

    @Test
    void assignApplication_setsAssignee() throws Exception {
        // Create
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

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(createJson.get("id").asText());

        // Assign
        String assignPayload = """
                { "assignedTo": "assessor1" }
                """;

        mockMvc.perform(post("/api/v1/applications/{id}/assign", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value("assessor1"));
    }

    // -----------------------------------------------------------------------
    // Negative tests
    // -----------------------------------------------------------------------

    @Test
    void createApplication_missingProductId_returns400() throws Exception {
        String payload = """
                {
                    "channel": "DIRECT_ONLINE",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """;

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createApplication_missingChannel_returns400() throws Exception {
        String payload = String.format("""
                {
                    "productId": "%s",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 360,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createApplication_invalidTermMonths_returns400() throws Exception {
        String payload = String.format("""
                {
                    "productId": "%s",
                    "channel": "DIRECT_ONLINE",
                    "purpose": "PURCHASE",
                    "occupancyType": "OWNER_OCCUPIED",
                    "requestedAmount": 500000,
                    "termMonths": 0,
                    "interestType": "VARIABLE",
                    "repaymentType": "PRINCIPAL_AND_INTEREST"
                }
                """, testProduct.getId());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateApplication_afterSubmission_returns422() throws Exception {
        // Create application via API
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

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(createJson.get("id").asText());

        // Set status to SUBMITTED directly via repo (bypassing submit validation)
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.SUBMITTED);
        loanApplicationRepository.save(app);

        // Attempt to update should be rejected
        String updatePayload = """
                {
                    "requestedAmount": 600000,
                    "termMonths": 300
                }
                """;

        mockMvc.perform(patch("/api/v1/applications/{id}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getApplication_nonExistent_returns404() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/applications/{id}", fakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createApplication_nonExistentProduct_handledGracefully() throws Exception {
        UUID fakeProductId = UUID.randomUUID();

        String payload = String.format("""
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
                """, fakeProductId);

        // ApplicationService.create() throws ResourceNotFoundException for unknown product,
        // which the GlobalExceptionHandler maps to 404
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }
}
