package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.entity.WorkflowEvent;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.ProductRepository;
import com.atheryon.mortgages.repository.WorkflowEventRepository;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class ApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final ProductRepository productRepository;
    private final ApplicationStateMachine stateMachine;
    private final WorkflowEventRepository workflowEventRepository;
    private final AtomicLong sequenceCounter = new AtomicLong(1);

    public ApplicationService(LoanApplicationRepository applicationRepository,
                              ProductRepository productRepository,
                              ApplicationStateMachine stateMachine,
                              WorkflowEventRepository workflowEventRepository) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.stateMachine = stateMachine;
        this.workflowEventRepository = workflowEventRepository;
    }

    public LoanApplication create(LoanApplication request) {
        request.setApplicationNumber(generateApplicationNumber());
        request.setStatus(ApplicationStatus.DRAFT);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        if (request.getProduct() != null && request.getProduct().getId() != null) {
            Product product = productRepository.findById(request.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProduct().getId()));
            request.setProduct(product);
        }

        LoanApplication saved = applicationRepository.save(request);
        recordWorkflowEvent(saved, "APPLICATION_CREATED", "SYSTEM", "SYSTEM", null, ApplicationStatus.DRAFT);
        return saved;
    }

    @Transactional(readOnly = true)
    public LoanApplication getById(UUID id) {
        return applicationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", id));
    }

    @Transactional(readOnly = true)
    public LoanApplication getByApplicationNumber(String num) {
        return applicationRepository.findByApplicationNumber(num)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "applicationNumber", num));
    }

    @Transactional(readOnly = true)
    public Page<LoanApplication> list(List<ApplicationStatus> statuses, Pageable pageable) {
        if (statuses != null && !statuses.isEmpty()) {
            return applicationRepository.findByStatusIn(statuses, pageable);
        }
        return applicationRepository.findAll(pageable);
    }

    public LoanApplication update(UUID id, LoanApplication updates) {
        LoanApplication app = getById(id);

        if (app.getStatus() != ApplicationStatus.DRAFT && app.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new BusinessRuleException("INVALID_UPDATE_STATE",
                    "Application can only be updated in DRAFT or IN_PROGRESS status");
        }

        if (updates.getRequestedAmount() != null) {
            app.setRequestedAmount(updates.getRequestedAmount());
        }
        if (updates.getTermMonths() > 0) {
            app.setTermMonths(updates.getTermMonths());
        }
        if (updates.getPurpose() != null) {
            app.setPurpose(updates.getPurpose());
        }
        if (updates.getInterestType() != null) {
            app.setInterestType(updates.getInterestType());
        }
        if (updates.getRepaymentType() != null) {
            app.setRepaymentType(updates.getRepaymentType());
        }
        if (updates.getChannel() != null) {
            app.setChannel(updates.getChannel());
        }

        app.setUpdatedAt(LocalDateTime.now());
        return applicationRepository.save(app);
    }

    public LoanApplication submit(UUID id) {
        LoanApplication app = getById(id);
        validateForSubmission(app);
        ApplicationStatus prev = app.getStatus();

        // Auto-advance through intermediate states when validation passes.
        // Submission is the user-facing action; the rework lifecycle (DRAFT → IN_PROGRESS →
        // READY_FOR_SUBMISSION) is an internal edit workflow, not a per-step API surface.
        if (app.getStatus() == ApplicationStatus.DRAFT) {
            stateMachine.transition(app, ApplicationStatus.IN_PROGRESS, "SYSTEM", "SYSTEM");
        }
        if (app.getStatus() == ApplicationStatus.IN_PROGRESS) {
            stateMachine.transition(app, ApplicationStatus.READY_FOR_SUBMISSION, "SYSTEM", "SYSTEM");
        }
        stateMachine.transition(app, ApplicationStatus.SUBMITTED, "SYSTEM", "SYSTEM");

        app.setSubmittedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        LoanApplication saved = applicationRepository.save(app);
        recordWorkflowEvent(saved, "APPLICATION_SUBMITTED", "SYSTEM", "SYSTEM", prev, ApplicationStatus.SUBMITTED);
        return saved;
    }

    public LoanApplication withdraw(UUID id, String reason, String actorId) {
        LoanApplication app = getById(id);
        ApplicationStatus prev = app.getStatus();
        stateMachine.transition(app, ApplicationStatus.WITHDRAWN, actorId, "USER");
        app.setUpdatedAt(LocalDateTime.now());
        LoanApplication saved = applicationRepository.save(app);
        recordWorkflowEvent(saved, "APPLICATION_WITHDRAWN", actorId, "USER", prev, ApplicationStatus.WITHDRAWN);
        return saved;
    }

    public LoanApplication assign(UUID id, String assignedTo) {
        LoanApplication app = getById(id);
        app.setAssignedTo(assignedTo);
        app.setUpdatedAt(LocalDateTime.now());
        return applicationRepository.save(app);
    }

    public LoanApplication transitionStatus(UUID id, ApplicationStatus targetStatus,
                                            String actorId, String actorType) {
        LoanApplication app = getById(id);
        ApplicationStatus prev = app.getStatus();
        stateMachine.transition(app, targetStatus, actorId, actorType);
        app.setUpdatedAt(LocalDateTime.now());
        LoanApplication saved = applicationRepository.save(app);
        recordWorkflowEvent(saved, "STATUS_TRANSITION", actorId, actorType, prev, targetStatus);
        return saved;
    }

    private void recordWorkflowEvent(LoanApplication app, String eventType,
                                     String actorId, String actorType,
                                     ApplicationStatus prevState, ApplicationStatus newState) {
        WorkflowEvent event = new WorkflowEvent();
        event.setApplication(app);
        event.setEventType(eventType);
        event.setOccurredAt(LocalDateTime.now());
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setPreviousState(prevState != null ? prevState.name() : null);
        event.setNewState(newState != null ? newState.name() : null);
        event.setCorrelationId(UUID.randomUUID().toString());
        workflowEventRepository.save(event);
    }

    private String generateApplicationNumber() {
        int year = Year.now().getValue();
        long seq = sequenceCounter.getAndIncrement();
        return String.format("ATH-%d-%06d", year, seq);
    }

    private void validateForSubmission(LoanApplication app) {
        List<String> errors = new java.util.ArrayList<>();

        if (app.getRequestedAmount() == null) {
            errors.add("Requested loan amount is required");
        }
        if (app.getTermMonths() <= 0) {
            errors.add("Loan term must be greater than zero");
        }
        if (app.getPurpose() == null) {
            errors.add("Loan purpose is required");
        }
        if (app.getProduct() == null) {
            errors.add("Product selection is required");
        }
        if (app.getApplicationParties() == null || app.getApplicationParties().isEmpty()) {
            errors.add("At least one applicant is required");
        }
        if (app.getSecurities() == null || app.getSecurities().isEmpty()) {
            errors.add("At least one security property is required");
        }

        if (!errors.isEmpty()) {
            throw new BusinessRuleException("SUBMISSION_VALIDATION",
                    "Application is not ready for submission", errors);
        }
    }
}
