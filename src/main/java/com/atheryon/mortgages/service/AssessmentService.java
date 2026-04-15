package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.FinancialSnapshotRepository;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.WorkflowEventRepository;
import com.atheryon.mortgages.rules.ServiceabilityCalculator;
import com.atheryon.mortgages.rules.ServiceabilityResult;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import com.atheryon.mortgages.util.MortgageMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AssessmentService {

    private final LoanApplicationRepository applicationRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final ApplicationStateMachine stateMachine;
    private final ServiceabilityCalculator serviceabilityCalculator;
    private final WorkflowEventRepository workflowEventRepository;

    public AssessmentService(LoanApplicationRepository applicationRepository,
                             FinancialSnapshotRepository snapshotRepository,
                             ApplicationStateMachine stateMachine,
                             ServiceabilityCalculator serviceabilityCalculator,
                             WorkflowEventRepository workflowEventRepository) {
        this.applicationRepository = applicationRepository;
        this.snapshotRepository = snapshotRepository;
        this.stateMachine = stateMachine;
        this.serviceabilityCalculator = serviceabilityCalculator;
        this.workflowEventRepository = workflowEventRepository;
    }

    public LoanApplication beginAssessment(UUID applicationId, String assessorId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        ApplicationStatus prev = app.getStatus();
        stateMachine.transition(app, ApplicationStatus.UNDER_ASSESSMENT, assessorId, "ASSESSOR");
        app.setAssignedTo(assessorId);
        app.setUpdatedAt(LocalDateTime.now());
        LoanApplication saved = applicationRepository.save(app);

        recordEvent(saved, "ASSESSMENT_BEGUN", assessorId, "ASSESSOR", prev, ApplicationStatus.UNDER_ASSESSMENT);
        return saved;
    }

    public LoanApplication completeVerification(UUID applicationId, String assessorId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        ApplicationStatus prev = app.getStatus();
        stateMachine.transition(app, ApplicationStatus.VERIFIED, assessorId, "ASSESSOR");
        app.setUpdatedAt(LocalDateTime.now());
        LoanApplication saved = applicationRepository.save(app);

        recordEvent(saved, "VERIFICATION_COMPLETE", assessorId, "ASSESSOR", prev, ApplicationStatus.VERIFIED);
        return saved;
    }

    public FinancialSnapshot calculateServiceability(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        FinancialSnapshot snapshot = app.getFinancialSnapshot();
        if (snapshot == null) {
            throw new BusinessRuleException("NO_FINANCIAL_DATA",
                    "Financial snapshot is required for serviceability calculation");
        }

        // Calculate proposed monthly repayment (simplified — P&I at product rate)
        BigDecimal productRate = BigDecimal.ZERO;
        if (app.getProduct() != null && app.getProduct().getLendingRates() != null
                && !app.getProduct().getLendingRates().isEmpty()) {
            productRate = app.getProduct().getLendingRates().get(0).getRate();
        }

        BigDecimal proposedRepayment = MortgageMath.monthlyRepayment(
                app.getRequestedAmount(), productRate, app.getTermMonths());

        ServiceabilityResult result = serviceabilityCalculator.calculate(
                snapshot, proposedRepayment, productRate);

        // Update snapshot with results
        snapshot.setNetDisposableIncome(result.getNetDisposableIncome());
        snapshot.setUncommittedMonthlyIncome(result.getUmi());
        snapshot.setDebtServiceRatio(result.getDsr());
        snapshot.setAssessmentRate(result.getAssessmentRate());
        snapshot.setServiceabilityOutcome(result.getOutcome());

        return snapshotRepository.save(snapshot);
    }

    private void recordEvent(LoanApplication app, String eventType, String actorId,
                             String actorType, ApplicationStatus prev, ApplicationStatus next) {
        var event = new com.atheryon.mortgages.domain.entity.WorkflowEvent();
        event.setApplication(app);
        event.setEventType(eventType);
        event.setOccurredAt(LocalDateTime.now());
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setPreviousState(prev != null ? prev.name() : null);
        event.setNewState(next != null ? next.name() : null);
        event.setCorrelationId(UUID.randomUUID().toString());
        workflowEventRepository.save(event);
    }
}
