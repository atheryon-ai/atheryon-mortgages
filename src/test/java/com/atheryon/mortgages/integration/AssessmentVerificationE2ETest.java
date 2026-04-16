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
 * E2E tests for SRS Process 4 - Assessment and Verification.
 *
 * Validates the assessment pipeline (SUBMITTED -> UNDER_ASSESSMENT -> VERIFIED),
 * document upload/verify/reject lifecycle, and error handling for invalid
 * state transitions and business rule violations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssessmentVerificationE2ETest {

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
    private DocumentRepository documentRepository;

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
    // Helper: create a submitted application (full pipeline)
    // ----------------------------------------------------------------

    private UUID createSubmittedApplication() throws Exception {
        // 1. Create application via POST
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

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID appId = UUID.fromString(json.get("id").asText());

        // 2. Link party
        LoanApplication app = loanApplicationRepository.findById(appId).orElseThrow();
        applicationPartyRepository.save(
                ApplicationParty.builder()
                        .application(app)
                        .party(testParty)
                        .role(PartyRole.PRIMARY_BORROWER)
                        .ownershipPercentage(new BigDecimal("1.00"))
                        .build()
        );

        // 3. Link security
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

        entityManager.flush();
        entityManager.clear();

        // 4. Set status to READY_FOR_SUBMISSION
        app = loanApplicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.READY_FOR_SUBMISSION);
        loanApplicationRepository.save(app);
        entityManager.flush();
        entityManager.clear();

        // 5. Submit
        mockMvc.perform(post("/api/v1/applications/{id}/submit", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        return appId;
    }

    /**
     * Create a DRAFT application (no submit) and return its UUID.
     */
    private UUID createDraftApplication() throws Exception {
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
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    /**
     * Upload a document for the given application and return its UUID.
     */
    private UUID uploadDocument(UUID appId, String fileName) throws Exception {
        String docPayload = String.format("""
                {
                    "applicationId": "%s",
                    "documentType": "PAYSLIP",
                    "documentCategory": "INCOME",
                    "fileName": "%s",
                    "mimeType": "application/pdf",
                    "fileSizeBytes": 102400,
                    "storageReference": "s3://docs/%s",
                    "uploadedBy": "jane@example.com"
                }
                """, appId, fileName, fileName);

        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(docPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    // ================================================================
    // Happy-path tests
    // ================================================================

    @Test
    void assessApplication_submittedToUnderAssessment() throws Exception {
        UUID appId = createSubmittedApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));
    }

    @Test
    void verifyApplication_underAssessmentToVerified() throws Exception {
        UUID appId = createSubmittedApplication();

        // Assess first
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk());

        // Then verify
        mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void fullAssessmentPipeline_submitAssessVerify() throws Exception {
        UUID appId = createSubmittedApplication();

        // Verify the starting state is SUBMITTED
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Assess
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));

        // Verify
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andReturn();

        // Confirm final state via GET
        mockMvc.perform(get("/api/v1/applications/{id}", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void uploadDocument_returnsUploadedStatus() throws Exception {
        UUID appId = createSubmittedApplication();

        String docPayload = String.format("""
                {
                    "applicationId": "%s",
                    "documentType": "PAYSLIP",
                    "documentCategory": "INCOME",
                    "fileName": "payslip.pdf",
                    "mimeType": "application/pdf",
                    "fileSizeBytes": 102400,
                    "storageReference": "s3://docs/payslip.pdf",
                    "uploadedBy": "jane@example.com"
                }
                """, appId);

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(docPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentType").value("PAYSLIP"))
                .andExpect(jsonPath("$.documentCategory").value("INCOME"))
                .andExpect(jsonPath("$.fileName").value("payslip.pdf"));
    }

    @Test
    void verifyDocument_changesStatusToVerified() throws Exception {
        UUID appId = createSubmittedApplication();
        UUID docId = uploadDocument(appId, "payslip.pdf");

        mockMvc.perform(post("/api/v1/documents/{id}/verify", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "verifiedBy": "assessor@atheryon.com" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
    }

    @Test
    void listDocuments_returnsApplicationDocuments() throws Exception {
        UUID appId = createSubmittedApplication();

        // Upload two documents
        uploadDocument(appId, "payslip_march.pdf");
        uploadDocument(appId, "payslip_april.pdf");

        MvcResult listResult = mockMvc.perform(get("/api/v1/documents/application/{applicationId}", appId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode docs = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(docs.isArray()).isTrue();
        assertThat(docs.size()).isEqualTo(2);
    }

    // ================================================================
    // Negative tests
    // ================================================================

    @Test
    void assessApplication_fromDraft_returns409() throws Exception {
        UUID appId = createDraftApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void assessApplication_alreadyUnderAssessment_returns409() throws Exception {
        UUID appId = createSubmittedApplication();

        // First assess succeeds
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));

        // Second assess from UNDER_ASSESSMENT is not a valid transition
        mockMvc.perform(post("/api/v1/applications/{id}/assess", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyApplication_fromSubmitted_returns409() throws Exception {
        UUID appId = createSubmittedApplication();

        // Try to verify without assessing first (SUBMITTED -> VERIFIED is not allowed)
        mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyApplication_fromDraft_returns409() throws Exception {
        UUID appId = createDraftApplication();

        mockMvc.perform(post("/api/v1/applications/{id}/verify", appId))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyDocument_alreadyVerified_returns422() throws Exception {
        UUID appId = createSubmittedApplication();
        UUID docId = uploadDocument(appId, "payslip.pdf");

        String verifyPayload = """
                { "verifiedBy": "assessor@atheryon.com" }
                """;

        // First verify succeeds
        mockMvc.perform(post("/api/v1/documents/{id}/verify", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // Second verify on an already-VERIFIED doc should fail
        mockMvc.perform(post("/api/v1/documents/{id}/verify", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyPayload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectDocument_withReason() throws Exception {
        UUID appId = createSubmittedApplication();
        UUID docId = uploadDocument(appId, "expired_licence.pdf");

        MvcResult rejectResult = mockMvc.perform(post("/api/v1/documents/{id}/reject", docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Document is expired" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andReturn();

        JsonNode json = objectMapper.readTree(rejectResult.getResponse().getContentAsString());
        assertThat(json.get("rejectionReason").asText()).isEqualTo("Document is expired");
    }

    @Test
    void getDocument_nonExistent_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/documents/{id}", randomId))
                .andExpect(status().isNotFound());
    }
}
