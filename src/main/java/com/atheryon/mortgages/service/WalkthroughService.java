package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.*;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("dev")
public class WalkthroughService {

    private final Map<String, WalkthroughSession> sessions = new ConcurrentHashMap<>();

    private final ApplicationService applicationService;
    private final PartyService partyService;
    private final SecurityService securityService;
    private final DocumentService documentService;
    private final AssessmentService assessmentService;
    private final DecisionService decisionService;
    private final OfferService offerService;
    private final ProductRepository productRepository;
    private final ApplicationPartyRepository applicationPartyRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final ConsentRecordRepository consentRepository;
    private final WorkflowEventRepository eventRepository;
    private final LoanApplicationRepository applicationRepository;

    static final String[] STEP_NAMES = {
            "Create Application",
            "Add Borrower",
            "Add Property Security",
            "Upload Documents",
            "Submit Application",
            "Begin Assessment",
            "Verify Documents",
            "Complete Verification",
            "Add Financial Data",
            "Run Decision",
            "Generate Offer",
            "Accept Offer",
            "Begin Settlement",
            "Complete Settlement"
    };

    public WalkthroughService(ApplicationService applicationService,
                              PartyService partyService,
                              SecurityService securityService,
                              DocumentService documentService,
                              AssessmentService assessmentService,
                              DecisionService decisionService,
                              OfferService offerService,
                              ProductRepository productRepository,
                              ApplicationPartyRepository applicationPartyRepository,
                              FinancialSnapshotRepository snapshotRepository,
                              ConsentRecordRepository consentRepository,
                              WorkflowEventRepository eventRepository,
                              LoanApplicationRepository applicationRepository) {
        this.applicationService = applicationService;
        this.partyService = partyService;
        this.securityService = securityService;
        this.documentService = documentService;
        this.assessmentService = assessmentService;
        this.decisionService = decisionService;
        this.offerService = offerService;
        this.productRepository = productRepository;
        this.applicationPartyRepository = applicationPartyRepository;
        this.snapshotRepository = snapshotRepository;
        this.consentRepository = consentRepository;
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public Map<String, Object> startWalkthrough() {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        WalkthroughSession session = new WalkthroughSession();
        session.sessionId = sessionId;

        Product product = new Product();
        product.setProductType(ProductType.STANDARD_VARIABLE);
        product.setName("Atheryon Home Saver Variable");
        product.setBrand("Atheryon");
        product.setEffectiveFrom(LocalDate.now().minusYears(1));
        product.setMinimumLoanAmount(new BigDecimal("100000"));
        product.setMaximumLoanAmount(new BigDecimal("5000000"));
        product.setMinimumTermMonths(60);
        product.setMaximumTermMonths(360);
        product.setMaximumLtv(new BigDecimal("0.95"));
        product.setFeatures(List.of("OFFSET_ACCOUNT", "REDRAW_FACILITY", "EXTRA_REPAYMENTS"));

        LendingRate rate = new LendingRate();
        rate.setLendingRateType(LendingRateType.VARIABLE);
        rate.setRate(new BigDecimal("0.0599"));
        rate.setComparisonRate(new BigDecimal("0.0625"));
        rate.setCalculationFrequency("DAILY");
        rate.setApplicationFrequency("MONTHLY");
        rate.setProduct(product);
        product.setLendingRates(new ArrayList<>(List.of(rate)));

        product = productRepository.save(product);
        session.productId = product.getId();

        sessions.put(sessionId, session);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("currentStep", 0);
        result.put("totalSteps", STEP_NAMES.length);
        result.put("steps", STEP_NAMES);
        result.put("status", "ready");
        result.put("productCreated", Map.of(
                "id", product.getId(),
                "name", product.getName(),
                "rate", "5.99% p.a. variable"
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> executeNextStep(String sessionId) {
        WalkthroughSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("WalkthroughSession", "id", sessionId);
        }
        if (session.currentStep >= session.totalSteps) {
            throw new BusinessRuleException("WALKTHROUGH_COMPLETE", "All 14 steps completed");
        }

        int step = session.currentStep + 1;
        String previousState = null;
        if (session.applicationId != null) {
            previousState = applicationRepository.findById(session.applicationId)
                    .map(a -> a.getStatus().name()).orElse(null);
        }

        Map<String, Object> result = executeStep(session, step);

        String newState = null;
        if (session.applicationId != null) {
            newState = applicationRepository.findById(session.applicationId)
                    .map(a -> a.getStatus().name()).orElse(null);
        }

        result.put("previousState", previousState);
        result.put("newState", newState);
        result.put("stepsRemaining", session.totalSteps - step);

        if (session.applicationId != null) {
            result.put("applicationSnapshot", buildApplicationSnapshot(session.applicationId));
            result.put("workflowEvents", getWorkflowEvents(session.applicationId));
        }

        session.currentStep = step;
        session.history.add(result);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getState(String sessionId) {
        WalkthroughSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("WalkthroughSession", "id", sessionId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.sessionId);
        result.put("currentStep", session.currentStep);
        result.put("totalSteps", session.totalSteps);
        result.put("steps", STEP_NAMES);
        result.put("history", session.history);

        if (session.applicationId != null) {
            result.put("applicationSnapshot", buildApplicationSnapshot(session.applicationId));
            result.put("workflowEvents", getWorkflowEvents(session.applicationId));
        }

        return result;
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private Map<String, Object> executeStep(WalkthroughSession session, int step) {
        return switch (step) {
            case 1 -> stepCreateApplication(session);
            case 2 -> stepAddBorrower(session);
            case 3 -> stepAddSecurity(session);
            case 4 -> stepUploadDocuments(session);
            case 5 -> stepSubmitApplication(session);
            case 6 -> stepBeginAssessment(session);
            case 7 -> stepVerifyDocuments(session);
            case 8 -> stepCompleteVerification(session);
            case 9 -> stepAddFinancialData(session);
            case 10 -> stepRunDecision(session);
            case 11 -> stepGenerateOffer(session);
            case 12 -> stepAcceptOffer(session);
            case 13 -> stepBeginSettlement(session);
            case 14 -> stepCompleteSettlement(session);
            default -> throw new BusinessRuleException("INVALID_STEP", "Unknown step: " + step);
        };
    }

    // Step 1: Create a new mortgage application
    private Map<String, Object> stepCreateApplication(WalkthroughSession session) {
        LoanApplication entity = LoanApplication.builder()
                .product(productRepository.getReferenceById(session.productId))
                .channel(Channel.DIRECT_ONLINE)
                .purpose(LoanPurpose.PURCHASE)
                .occupancyType(OccupancyType.OWNER_OCCUPIED)
                .requestedAmount(new BigDecimal("650000"))
                .termMonths(360)
                .interestType(InterestType.VARIABLE)
                .repaymentType(RepaymentType.PRINCIPAL_AND_INTEREST)
                .repaymentFrequency(RepaymentFrequency.MONTHLY)
                .firstHomeBuyer(true)
                .build();

        LoanApplication app = applicationService.create(entity);
        session.applicationId = app.getId();

        return buildStepResult(1,
                Map.of("method", "POST", "path", "/api/v1/applications",
                        "body", Map.of("productId", session.productId, "channel", "DIRECT_ONLINE",
                                "purpose", "PURCHASE", "occupancyType", "OWNER_OCCUPIED",
                                "requestedAmount", 650000, "termMonths", 360,
                                "interestType", "VARIABLE", "repaymentType", "PRINCIPAL_AND_INTEREST")),
                List.of("id", "applicationNumber", "status", "createdAt"));
    }

    // Step 2: Add a primary borrower (party)
    private Map<String, Object> stepAddBorrower(WalkthroughSession session) {
        Party party = Party.builder()
                .partyType(PartyType.INDIVIDUAL)
                .title("Ms")
                .firstName("Sarah")
                .middleNames("Jane")
                .surname("Mitchell")
                .dateOfBirth(LocalDate.of(1988, 3, 15))
                .gender("Female")
                .residencyStatus("CITIZEN")
                .maritalStatus("SINGLE")
                .numberOfDependants(0)
                .email("sarah.mitchell@email.com")
                .mobilePhone("0412345678")
                .build();

        party = partyService.create(party);
        session.partyId = party.getId();

        LoanApplication app = applicationRepository.findById(session.applicationId).orElseThrow();
        ApplicationParty ap = ApplicationParty.builder()
                .application(app)
                .party(party)
                .role(PartyRole.PRIMARY_BORROWER)
                .ownershipPercentage(new BigDecimal("100"))
                .build();
        applicationPartyRepository.save(ap);

        return buildStepResult(2,
                Map.of("method", "POST", "path", "/api/v1/parties",
                        "body", Map.of("partyType", "INDIVIDUAL", "firstName", "Sarah",
                                "surname", "Mitchell", "dateOfBirth", "1988-03-15",
                                "email", "sarah.mitchell@email.com"),
                        "then", "Link party to application as BORROWER"),
                List.of("parties"));
    }

    // Step 3: Add property security
    private Map<String, Object> stepAddSecurity(WalkthroughSession session) {
        LoanApplication app = applicationRepository.findById(session.applicationId).orElseThrow();

        PropertySecurity security = PropertySecurity.builder()
                .application(app)
                .securityType(SecurityType.EXISTING_RESIDENTIAL)
                .primaryPurpose("OWNER_OCCUPIED")
                .propertyCategory(PropertyCategory.HOUSE)
                .streetNumber("42")
                .streetName("Harbour")
                .streetType("Avenue")
                .suburb("Mosman")
                .state("NSW")
                .postcode("2088")
                .numberOfBedrooms(3)
                .landAreaSqm(new BigDecimal("450"))
                .yearBuilt(1995)
                .purchasePrice(new BigDecimal("900000"))
                .contractDate(LocalDate.now().minusDays(5))
                .build();

        security = securityService.create(security);
        session.securityId = security.getId();

        return buildStepResult(3,
                Map.of("method", "POST", "path", "/api/v1/securities",
                        "body", Map.of("applicationId", session.applicationId,
                                "securityType", "EXISTING_RESIDENTIAL", "suburb", "Mosman",
                                "state", "NSW", "purchasePrice", 900000,
                                "propertyCategory", "HOUSE", "numberOfBedrooms", 3)),
                List.of("securities"));
    }

    // Step 4: Upload documents (payslip + ID)
    private Map<String, Object> stepUploadDocuments(WalkthroughSession session) {
        Document payslip = documentService.upload(session.applicationId, session.partyId,
                DocumentType.PAYSLIP, DocumentCategory.INCOME,
                "sarah_mitchell_payslip_march2026.pdf", "application/pdf",
                245760, "s3://docs/payslip-001.pdf", "sarah.mitchell@email.com");
        session.documentIds.add(payslip.getId());

        Document idDoc = documentService.upload(session.applicationId, session.partyId,
                DocumentType.DRIVERS_LICENCE, DocumentCategory.IDENTITY,
                "sarah_mitchell_drivers_licence.pdf", "application/pdf",
                128000, "s3://docs/id-001.pdf", "sarah.mitchell@email.com");
        session.documentIds.add(idDoc.getId());

        return buildStepResult(4,
                Map.of("method", "POST", "path", "/api/v1/documents",
                        "body", List.of(
                                Map.of("documentType", "PAYSLIP", "category", "INCOME",
                                        "fileName", "sarah_mitchell_payslip_march2026.pdf"),
                                Map.of("documentType", "DRIVERS_LICENCE", "category", "IDENTITY",
                                        "fileName", "sarah_mitchell_drivers_licence.pdf")),
                        "note", "Two documents uploaded"),
                List.of("documents"));
    }

    // Step 5: Submit application (transitions through DRAFT → IN_PROGRESS → READY_FOR_SUBMISSION → SUBMITTED)
    private Map<String, Object> stepSubmitApplication(WalkthroughSession session) {
        UUID appId = session.applicationId;

        // DRAFT → IN_PROGRESS
        applicationService.transitionStatus(appId, ApplicationStatus.IN_PROGRESS, "SYSTEM", "SYSTEM");
        // IN_PROGRESS → READY_FOR_SUBMISSION
        applicationService.transitionStatus(appId, ApplicationStatus.READY_FOR_SUBMISSION, "SYSTEM", "SYSTEM");
        // READY_FOR_SUBMISSION → SUBMITTED
        LoanApplication app = applicationService.submit(appId);

        Map<String, Object> result = buildStepResult(5,
                Map.of("method", "POST", "path", "/api/v1/applications/" + appId + "/submit",
                        "body", "null",
                        "note", "State transitions: DRAFT → IN_PROGRESS → READY_FOR_SUBMISSION → SUBMITTED"),
                List.of("status", "submittedAt"));
        result.put("stateTransitions", List.of("DRAFT → IN_PROGRESS", "IN_PROGRESS → READY_FOR_SUBMISSION",
                "READY_FOR_SUBMISSION → SUBMITTED"));
        return result;
    }

    // Step 6: Begin assessment
    private Map<String, Object> stepBeginAssessment(WalkthroughSession session) {
        assessmentService.beginAssessment(session.applicationId, "assessor-001");

        return buildStepResult(6,
                Map.of("method", "POST", "path", "/api/v1/applications/" + session.applicationId + "/assess",
                        "body", "null",
                        "note", "Application assigned to assessor-001"),
                List.of("status", "assignedTo"));
    }

    // Step 7: Verify all documents
    private Map<String, Object> stepVerifyDocuments(WalkthroughSession session) {
        for (UUID docId : session.documentIds) {
            documentService.verify(docId, "assessor-001");
        }

        return buildStepResult(7,
                Map.of("method", "POST", "path", "/api/v1/documents/{id}/verify",
                        "body", Map.of("verifiedBy", "assessor-001"),
                        "note", "All " + session.documentIds.size() + " documents verified"),
                List.of("documents[*].status"));
    }

    // Step 8: Complete verification
    private Map<String, Object> stepCompleteVerification(WalkthroughSession session) {
        assessmentService.completeVerification(session.applicationId, "assessor-001");

        return buildStepResult(8,
                Map.of("method", "POST", "path", "/api/v1/applications/" + session.applicationId + "/verify",
                        "body", "null"),
                List.of("status"));
    }

    // Step 9: Add financial data + consents + run serviceability
    private Map<String, Object> stepAddFinancialData(WalkthroughSession session) {
        LoanApplication app = applicationRepository.findById(session.applicationId).orElseThrow();

        // Create financial snapshot
        FinancialSnapshot snapshot = FinancialSnapshot.builder()
                .application(app)
                .capturedAt(LocalDateTime.now())
                .totalGrossAnnualIncome(new BigDecimal("150000"))
                .totalNetAnnualIncome(new BigDecimal("110000"))
                .declaredMonthlyExpenses(new BigDecimal("4000"))
                .hemMonthlyBenchmark(new BigDecimal("3200"))
                .assessedMonthlyExpenses(new BigDecimal("4000"))
                .bufferRate(new BigDecimal("0.03"))
                .build();

        // Add income items
        IncomeItem salary = IncomeItem.builder()
                .financialSnapshot(snapshot)
                .partyId(session.partyId)
                .incomeType(IncomeType.SALARY)
                .grossAnnualAmount(new BigDecimal("150000"))
                .netAnnualAmount(new BigDecimal("110000"))
                .frequency("ANNUAL")
                .verified(true)
                .verificationSource("PAYSLIP")
                .build();
        snapshot.setIncomeItems(new ArrayList<>(List.of(salary)));

        // Add expense items
        ExpenseItem rent = ExpenseItem.builder()
                .financialSnapshot(snapshot)
                .category("RENT")
                .monthlyAmount(new BigDecimal("2400"))
                .frequency("MONTHLY")
                .source("DECLARED")
                .build();
        ExpenseItem utilities = ExpenseItem.builder()
                .financialSnapshot(snapshot)
                .category("UTILITIES")
                .monthlyAmount(new BigDecimal("350"))
                .frequency("MONTHLY")
                .source("DECLARED")
                .build();
        ExpenseItem groceries = ExpenseItem.builder()
                .financialSnapshot(snapshot)
                .category("GROCERIES")
                .monthlyAmount(new BigDecimal("800"))
                .frequency("MONTHLY")
                .source("DECLARED")
                .build();
        ExpenseItem insurance = ExpenseItem.builder()
                .financialSnapshot(snapshot)
                .category("INSURANCE")
                .monthlyAmount(new BigDecimal("250"))
                .frequency("MONTHLY")
                .source("DECLARED")
                .build();
        ExpenseItem transport = ExpenseItem.builder()
                .financialSnapshot(snapshot)
                .category("TRANSPORT")
                .monthlyAmount(new BigDecimal("200"))
                .frequency("MONTHLY")
                .source("DECLARED")
                .build();
        snapshot.setExpenseItems(new ArrayList<>(List.of(rent, utilities, groceries, insurance, transport)));

        snapshotRepository.save(snapshot);
        app.setFinancialSnapshot(snapshot);
        applicationRepository.save(app);

        // Create consent records
        for (ConsentType type : List.of(ConsentType.CREDIT_CHECK, ConsentType.CDR_DATA_SHARING,
                ConsentType.PRIVACY_COLLECTION, ConsentType.ELECTRONIC_COMMUNICATION)) {
            ConsentRecord consent = ConsentRecord.builder()
                    .application(app)
                    .partyId(session.partyId)
                    .consentType(type)
                    .granted(true)
                    .grantedAt(LocalDateTime.now())
                    .expiryDate(LocalDate.now().plusYears(1))
                    .version("1.0")
                    .captureMethod("ONLINE")
                    .build();
            consentRepository.save(consent);
        }

        // Run serviceability calculation
        assessmentService.calculateServiceability(session.applicationId);

        return buildStepResult(9,
                Map.of("method", "POST", "path", "/api/v1/applications/" + session.applicationId + "/financials",
                        "body", Map.of("totalGrossAnnualIncome", 150000, "totalNetAnnualIncome", 110000,
                                "declaredMonthlyExpenses", 4000, "incomeItems", 1, "expenseItems", 5),
                        "note", "Financial snapshot + 4 consents created, serviceability calculated"),
                List.of("financialSnapshot", "consents"));
    }

    // Step 10: Run automated decision → REFERRED, then manual override → APPROVED
    private Map<String, Object> stepRunDecision(WalkthroughSession session) {
        // First: automated decision (will be REFERRED because no credit score)
        DecisionRecord autoResult = decisionService.runAutomatedDecision(session.applicationId);
        String autoOutcome = autoResult.getOutcome().name();

        // Then: manual override to APPROVED
        DecisionRecord manualResult = decisionService.manualDecision(
                session.applicationId, DecisionOutcome.APPROVED,
                "underwriter-001", null, null);

        Map<String, Object> result = buildStepResult(10,
                Map.of("method", "POST", "path", "/api/v1/applications/" + session.applicationId + "/decision",
                        "body", "null (automated first, then manual override)",
                        "note", "Automated engine → " + autoOutcome +
                                " (no credit bureau). Underwriter override → APPROVED"),
                List.of("status", "decisionRecord"));
        result.put("automatedOutcome", autoOutcome);
        result.put("finalOutcome", "APPROVED");
        result.put("decisionFlow", List.of(
                "VERIFIED → DECISIONED (automated engine runs)",
                "Engine result: " + autoOutcome + " (credit score unavailable)",
                "DECISIONED → APPROVED (underwriter manual override)"));
        return result;
    }

    // Step 11: Generate offer
    private Map<String, Object> stepGenerateOffer(WalkthroughSession session) {
        Offer offer = offerService.generateOffer(session.applicationId);
        session.offerId = offer.getId();

        return buildStepResult(11,
                Map.of("method", "POST",
                        "path", "/api/v1/offers/application/" + session.applicationId,
                        "body", "null"),
                List.of("status", "offer"));
    }

    // Step 12: Accept offer
    private Map<String, Object> stepAcceptOffer(WalkthroughSession session) {
        offerService.accept(session.offerId, "sarah.mitchell@email.com", "ONLINE");

        Map<String, Object> result = buildStepResult(12,
                Map.of("method", "POST",
                        "path", "/api/v1/offers/" + session.offerId + "/accept",
                        "body", Map.of("acceptedBy", "sarah.mitchell@email.com",
                                "acceptanceMethod", "ONLINE")),
                List.of("status", "offer.offerStatus", "offer.acceptedDate"));
        return result;
    }

    // Step 13: Begin settlement
    private Map<String, Object> stepBeginSettlement(WalkthroughSession session) {
        applicationService.transitionStatus(session.applicationId,
                ApplicationStatus.SETTLEMENT_IN_PROGRESS, "settlement-officer-001", "SYSTEM");

        return buildStepResult(13,
                Map.of("method", "POST",
                        "path", "/api/v1/applications/" + session.applicationId + "/settle",
                        "body", "null",
                        "note", "Settlement process initiated, assigned to settlement officer"),
                List.of("status"));
    }

    // Step 14: Complete settlement
    private Map<String, Object> stepCompleteSettlement(WalkthroughSession session) {
        applicationService.transitionStatus(session.applicationId,
                ApplicationStatus.SETTLED, "settlement-officer-001", "SYSTEM");

        Map<String, Object> result = buildStepResult(14,
                Map.of("method", "POST",
                        "path", "/api/v1/applications/" + session.applicationId + "/settle/complete",
                        "body", "null",
                        "note", "Funds disbursed, mortgage registered, settlement complete"),
                List.of("status", "settledAt"));
        result.put("complete", true);
        result.put("message", "Mortgage application journey complete! Loan is now SETTLED.");
        return result;
    }

    private Map<String, Object> buildStepResult(int stepNumber, Map<String, Object> apiCall,
                                                 List<String> changedFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stepNumber", stepNumber);
        result.put("stepName", STEP_NAMES[stepNumber - 1]);
        result.put("apiCall", apiCall);
        result.put("changedFields", changedFields);
        return result;
    }

    private Map<String, Object> buildApplicationSnapshot(UUID applicationId) {
        LoanApplication app = applicationService.getById(applicationId);
        Map<String, Object> s = new LinkedHashMap<>();

        s.put("id", app.getId());
        s.put("applicationNumber", app.getApplicationNumber());
        s.put("status", app.getStatus());
        s.put("channel", app.getChannel());
        s.put("purpose", app.getPurpose());
        s.put("occupancyType", app.getOccupancyType());
        s.put("requestedAmount", app.getRequestedAmount());
        s.put("termMonths", app.getTermMonths());
        s.put("interestType", app.getInterestType());
        s.put("repaymentType", app.getRepaymentType());
        s.put("firstHomeBuyer", app.isFirstHomeBuyer());
        s.put("assignedTo", app.getAssignedTo());
        s.put("createdAt", app.getCreatedAt());
        s.put("updatedAt", app.getUpdatedAt());
        s.put("submittedAt", app.getSubmittedAt());

        if (app.getProduct() != null) {
            s.put("product", Map.of(
                    "id", app.getProduct().getId(),
                    "name", app.getProduct().getName(),
                    "type", app.getProduct().getProductType()));
        }

        try {
            if (app.getApplicationParties() != null && !app.getApplicationParties().isEmpty()) {
                s.put("parties", app.getApplicationParties().stream().map(ap -> {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("role", ap.getRole());
                    pm.put("partyId", ap.getParty().getId());
                    pm.put("firstName", ap.getParty().getFirstName());
                    pm.put("surname", ap.getParty().getSurname());
                    pm.put("email", ap.getParty().getEmail());
                    pm.put("dateOfBirth", ap.getParty().getDateOfBirth());
                    return pm;
                }).toList());
            }
        } catch (Exception ignored) {}

        try {
            if (app.getSecurities() != null && !app.getSecurities().isEmpty()) {
                s.put("securities", app.getSecurities().stream().map(sec -> {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("id", sec.getId());
                    sm.put("securityType", sec.getSecurityType());
                    sm.put("propertyCategory", sec.getPropertyCategory());
                    sm.put("address", sec.getStreetNumber() + " " + sec.getStreetName() + " " +
                            sec.getStreetType() + ", " + sec.getSuburb() + " " + sec.getState() +
                            " " + sec.getPostcode());
                    sm.put("purchasePrice", sec.getPurchasePrice());
                    sm.put("numberOfBedrooms", sec.getNumberOfBedrooms());
                    return sm;
                }).toList());
            }
        } catch (Exception ignored) {}

        try {
            if (app.getDocuments() != null && !app.getDocuments().isEmpty()) {
                s.put("documents", app.getDocuments().stream().map(d -> {
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("id", d.getId());
                    dm.put("documentType", d.getDocumentType());
                    dm.put("documentCategory", d.getDocumentCategory());
                    dm.put("status", d.getStatus());
                    dm.put("fileName", d.getFileName());
                    return dm;
                }).toList());
            }
        } catch (Exception ignored) {}

        try {
            FinancialSnapshot fs = app.getFinancialSnapshot();
            if (fs != null) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("totalGrossAnnualIncome", fs.getTotalGrossAnnualIncome());
                fm.put("totalNetAnnualIncome", fs.getTotalNetAnnualIncome());
                fm.put("declaredMonthlyExpenses", fs.getDeclaredMonthlyExpenses());
                fm.put("assessedMonthlyExpenses", fs.getAssessedMonthlyExpenses());
                fm.put("serviceabilityOutcome", fs.getServiceabilityOutcome());
                fm.put("uncommittedMonthlyIncome", fs.getUncommittedMonthlyIncome());
                fm.put("debtServiceRatio", fs.getDebtServiceRatio());
                fm.put("netDisposableIncome", fs.getNetDisposableIncome());
                fm.put("assessmentRate", fs.getAssessmentRate());
                s.put("financialSnapshot", fm);
            }
        } catch (Exception ignored) {}

        try {
            DecisionRecord dr = app.getDecisionRecord();
            if (dr != null) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("id", dr.getId());
                dm.put("decisionType", dr.getDecisionType());
                dm.put("outcome", dr.getOutcome());
                dm.put("decidedBy", dr.getDecidedBy());
                dm.put("decisionDate", dr.getDecisionDate());
                dm.put("delegatedAuthorityLevel", dr.getDelegatedAuthorityLevel());
                if (dr.getDeclineReasons() != null && !dr.getDeclineReasons().isEmpty()) {
                    dm.put("declineReasons", dr.getDeclineReasons());
                }
                s.put("decisionRecord", dm);
            }
        } catch (Exception ignored) {}

        try {
            Offer offer = app.getOffer();
            if (offer != null) {
                Map<String, Object> om = new LinkedHashMap<>();
                om.put("id", offer.getId());
                om.put("offerStatus", offer.getOfferStatus());
                om.put("approvedAmount", offer.getApprovedAmount());
                om.put("interestRate", offer.getInterestRate());
                om.put("estimatedMonthlyRepayment", offer.getEstimatedMonthlyRepayment());
                om.put("termMonths", offer.getTermMonths());
                om.put("lmiRequired", offer.isLmiRequired());
                om.put("expiryDate", offer.getExpiryDate());
                om.put("offerDate", offer.getOfferDate());
                om.put("acceptedDate", offer.getAcceptedDate());
                om.put("acceptedBy", offer.getAcceptedBy());
                s.put("offer", om);
            }
        } catch (Exception ignored) {}

        try {
            if (app.getConsents() != null && !app.getConsents().isEmpty()) {
                s.put("consents", app.getConsents().stream().map(c -> Map.of(
                        "consentType", (Object) c.getConsentType(),
                        "granted", c.isGranted()
                )).toList());
            }
        } catch (Exception ignored) {}

        return s;
    }

    private List<Map<String, Object>> getWorkflowEvents(UUID applicationId) {
        return eventRepository.findByApplicationIdOrderByOccurredAtAsc(applicationId).stream()
                .map(e -> {
                    Map<String, Object> em = new LinkedHashMap<>();
                    em.put("id", e.getId());
                    em.put("eventType", e.getEventType());
                    em.put("occurredAt", e.getOccurredAt());
                    em.put("actorType", e.getActorType());
                    em.put("actorId", e.getActorId());
                    em.put("previousState", e.getPreviousState());
                    em.put("newState", e.getNewState());
                    return em;
                }).toList();
    }

    static class WalkthroughSession {
        String sessionId;
        int currentStep = 0;
        int totalSteps = 14;
        UUID applicationId;
        UUID partyId;
        UUID productId;
        UUID securityId;
        List<UUID> documentIds = new ArrayList<>();
        UUID offerId;
        List<Map<String, Object>> history = new ArrayList<>();
    }
}
