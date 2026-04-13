package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.DecisionRecord;
import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.dto.request.*;
import com.atheryon.mortgages.dto.response.*;
import com.atheryon.mortgages.service.ApplicationService;
import com.atheryon.mortgages.service.AssessmentService;
import com.atheryon.mortgages.service.DecisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Mortgage application lifecycle management")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AssessmentService assessmentService;
    private final DecisionService decisionService;

    public ApplicationController(ApplicationService applicationService,
                                 AssessmentService assessmentService,
                                 DecisionService decisionService) {
        this.applicationService = applicationService;
        this.assessmentService = assessmentService;
        this.decisionService = decisionService;
    }

    @PostMapping
    @Operation(summary = "Create a new mortgage application")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        Product product = new Product();
        product.setId(request.productId());

        LoanApplication entity = LoanApplication.builder()
                .product(product)
                .channel(request.channel())
                .purpose(request.purpose())
                .occupancyType(request.occupancyType())
                .requestedAmount(request.requestedAmount())
                .termMonths(request.termMonths())
                .interestType(request.interestType())
                .repaymentType(request.repaymentType())
                .repaymentFrequency(request.repaymentFrequency())
                .firstHomeBuyer(request.firstHomeBuyer() != null && request.firstHomeBuyer())
                .build();

        LoanApplication app = applicationService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(app));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application by ID")
    public ResponseEntity<ApplicationResponse> getById(@PathVariable UUID id) {
        LoanApplication app = applicationService.getById(id);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @GetMapping
    @Operation(summary = "List applications with optional filters")
    public ResponseEntity<PageResponse<ApplicationResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ApplicationStatus> statuses = status != null ? List.of(status) : null;
        Page<LoanApplication> result = applicationService.list(statuses, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result, ApplicationResponse::from));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update application fields")
    public ResponseEntity<ApplicationResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateApplicationRequest request) {
        LoanApplication updates = new LoanApplication();
        updates.setPurpose(request.purpose());
        updates.setOccupancyType(request.occupancyType());
        updates.setRequestedAmount(request.requestedAmount());
        if (request.termMonths() != null) {
            updates.setTermMonths(request.termMonths());
        }
        updates.setInterestType(request.interestType());
        updates.setRepaymentType(request.repaymentType());
        updates.setRepaymentFrequency(request.repaymentFrequency());

        LoanApplication app = applicationService.update(id, updates);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit application for assessment")
    public ResponseEntity<ApplicationResponse> submit(@PathVariable UUID id) {
        LoanApplication app = applicationService.submit(id);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw application")
    public ResponseEntity<ApplicationResponse> withdraw(@PathVariable UUID id,
                                                        @Valid @RequestBody WithdrawRequest request) {
        LoanApplication app = applicationService.withdraw(id, request.reason(), null);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign application to a handler")
    public ResponseEntity<ApplicationResponse> assign(@PathVariable UUID id,
                                                      @Valid @RequestBody AssignRequest request) {
        LoanApplication app = applicationService.assign(id, request.assignedTo());
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/assess")
    @Operation(summary = "Begin assessment of application")
    public ResponseEntity<ApplicationResponse> beginAssessment(@PathVariable UUID id) {
        LoanApplication app = assessmentService.beginAssessment(id, null);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Complete verification step")
    public ResponseEntity<ApplicationResponse> completeVerification(@PathVariable UUID id) {
        LoanApplication app = assessmentService.completeVerification(id, null);
        return ResponseEntity.ok(ApplicationResponse.from(app));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Run automated decision or record manual decision")
    public ResponseEntity<DecisionResponse> decision(@PathVariable UUID id,
                                                     @RequestBody(required = false) @Valid DecisionRequest request) {
        DecisionRecord record;
        if (request == null) {
            record = decisionService.runAutomatedDecision(id);
        } else {
            record = decisionService.manualDecision(id, request.outcome(), request.decidedBy(),
                    request.reasons(), request.conditions());
        }
        return ResponseEntity.ok(DecisionResponse.from(record));
    }

    @PostMapping("/{id}/decision/override")
    @Operation(summary = "Underwriter override decision")
    public ResponseEntity<DecisionResponse> overrideDecision(@PathVariable UUID id,
                                                             @Valid @RequestBody DecisionRequest request) {
        DecisionRecord record = decisionService.manualDecision(id, request.outcome(), request.decidedBy(),
                request.reasons(), request.conditions());
        return ResponseEntity.ok(DecisionResponse.from(record));
    }

    @GetMapping("/{id}/serviceability")
    @Operation(summary = "Get serviceability calculation")
    public ResponseEntity<ServiceabilityResponse> getServiceability(@PathVariable UUID id) {
        FinancialSnapshot snapshot = assessmentService.calculateServiceability(id);
        ServiceabilityResponse response = new ServiceabilityResponse(
                snapshot.getNetDisposableIncome(),
                snapshot.getDebtServiceRatio(),
                snapshot.getUncommittedMonthlyIncome(),
                snapshot.getAssessmentRate(),
                snapshot.getServiceabilityOutcome()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/conditions/{conditionId}/satisfy")
    @Operation(summary = "Satisfy a decision condition")
    public ResponseEntity<Void> satisfyCondition(@PathVariable UUID id,
                                                  @PathVariable UUID conditionId) {
        decisionService.satisfyCondition(conditionId, null);
        return ResponseEntity.ok().build();
    }
}
