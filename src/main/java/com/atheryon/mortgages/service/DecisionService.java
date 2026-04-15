package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.DecisionCondition;
import com.atheryon.mortgages.domain.entity.DecisionRecord;
import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.PropertySecurity;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.domain.enums.ConditionStatus;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DecisionType;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.DecisionConditionRepository;
import com.atheryon.mortgages.repository.DecisionRecordRepository;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.rules.DecisionEngine;
import com.atheryon.mortgages.rules.DecisionResult;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DecisionService {

    private final LoanApplicationRepository applicationRepository;
    private final DecisionRecordRepository decisionRecordRepository;
    private final DecisionConditionRepository conditionRepository;
    private final DecisionEngine decisionEngine;
    private final ApplicationStateMachine stateMachine;

    public DecisionService(LoanApplicationRepository applicationRepository,
                           DecisionRecordRepository decisionRecordRepository,
                           DecisionConditionRepository conditionRepository,
                           DecisionEngine decisionEngine,
                           ApplicationStateMachine stateMachine) {
        this.applicationRepository = applicationRepository;
        this.decisionRecordRepository = decisionRecordRepository;
        this.conditionRepository = conditionRepository;
        this.decisionEngine = decisionEngine;
        this.stateMachine = stateMachine;
    }

    public DecisionRecord runAutomatedDecision(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        if (app.getStatus() != ApplicationStatus.VERIFIED) {
            throw new BusinessRuleException("INVALID_DECISION_STATE",
                    "Application must be in VERIFIED status for automated decision");
        }

        FinancialSnapshot snapshot = app.getFinancialSnapshot();
        Integer creditScore = (app.getDecisionRecord() != null) ? app.getDecisionRecord().getCreditScore() : null;

        // Calculate LTV from first security
        BigDecimal ltv = null;
        if (app.getSecurities() != null && !app.getSecurities().isEmpty()) {
            PropertySecurity security = app.getSecurities().get(0);
            if (security.getPurchasePrice() != null && security.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
                ltv = app.getRequestedAmount()
                        .divide(security.getPurchasePrice(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
        }

        DecisionResult result = decisionEngine.evaluate(app, snapshot, creditScore, ltv);

        // Status advances to DECISIONED regardless of outcome.
        // Decision outcomes (APPROVED / CONDITIONALLY_APPROVED / DECLINED / REFERRED_TO_UNDERWRITER)
        // are recorded on DecisionRecord.outcome, not as status values.
        stateMachine.transition(app, ApplicationStatus.DECISIONED, "SYSTEM", "SYSTEM");

        DecisionRecord record = new DecisionRecord();
        record.setApplication(app);
        record.setDecisionType(DecisionType.AUTOMATED);
        record.setOutcome(result.getOutcome());
        record.setDecidedBy("SYSTEM");
        record.setDecisionDate(LocalDateTime.now());
        record.setDelegatedAuthorityLevel(result.getSuggestedDelegatedAuthority());
        record.setCreditScore(creditScore);
        if (result.getOutcome() == DecisionOutcome.DECLINED) {
            record.setDeclineReasons(result.getReasons());
        }

        DecisionRecord saved = decisionRecordRepository.save(record);
        app.setDecisionRecord(saved);

        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        return saved;
    }

    public DecisionRecord manualDecision(UUID applicationId, DecisionOutcome outcome,
                                         String decidedBy, List<String> reasons,
                                         List<String> conditions) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        // Allow manual decision from VERIFIED or after automated referral (DECISIONED)
        if (app.getStatus() != ApplicationStatus.VERIFIED && app.getStatus() != ApplicationStatus.DECISIONED) {
            throw new BusinessRuleException("INVALID_DECISION_STATE",
                    "Application must be in VERIFIED or DECISIONED status for manual decision");
        }

        // If coming from VERIFIED, transition through DECISIONED
        if (app.getStatus() == ApplicationStatus.VERIFIED) {
            stateMachine.transition(app, ApplicationStatus.DECISIONED, decidedBy, "UNDERWRITER");
        }

        DecisionRecord record = new DecisionRecord();
        record.setApplication(app);
        record.setDecisionType(DecisionType.MANUAL);
        record.setOutcome(outcome);
        record.setDecidedBy(decidedBy);
        record.setDecisionDate(LocalDateTime.now());

        if (outcome == DecisionOutcome.DECLINED) {
            record.setDeclineReasons(reasons);
        }

        DecisionRecord saved = decisionRecordRepository.save(record);
        app.setDecisionRecord(saved);

        // Create conditions if provided
        if (conditions != null && !conditions.isEmpty()) {
            List<DecisionCondition> conditionList = new ArrayList<>();
            for (String desc : conditions) {
                DecisionCondition condition = new DecisionCondition();
                condition.setDecisionRecord(saved);
                condition.setDescription(desc);
                condition.setStatus(ConditionStatus.OUTSTANDING);
                conditionList.add(conditionRepository.save(condition));
            }
            saved.setConditions(conditionList);
        }

        // Status stays at DECISIONED. Outcome is recorded on DecisionRecord.outcome above.
        // OfferService.generateOffer gates on decisionRecord.outcome ∈ {APPROVED, CONDITIONALLY_APPROVED}.
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        return saved;
    }

    public DecisionCondition satisfyCondition(UUID conditionId, String satisfiedBy) {
        DecisionCondition condition = conditionRepository.findById(conditionId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionCondition", "id", conditionId));

        condition.setStatus(ConditionStatus.SATISFIED);
        condition.setSatisfiedDate(LocalDate.now());
        condition.setSatisfiedBy(satisfiedBy);
        return conditionRepository.save(condition);
    }

}
