package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.DecisionCondition;
import com.atheryon.mortgages.domain.entity.DecisionRecord;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DecisionType;
import com.atheryon.mortgages.repository.DecisionConditionRepository;
import com.atheryon.mortgages.repository.DecisionRecordRepository;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.rules.DecisionEngine;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private DecisionRecordRepository decisionRecordRepository;

    @Mock
    private DecisionConditionRepository conditionRepository;

    @Mock
    private DecisionEngine decisionEngine;

    @Mock
    private ApplicationStateMachine stateMachine;

    private DecisionService service;

    @BeforeEach
    void setUp() {
        service = new DecisionService(applicationRepository, decisionRecordRepository,
                conditionRepository, decisionEngine, stateMachine);
    }

    @Test
    void manualDecision_decisionedApp_updatesExistingDecisionRecordAndConditions() {
        UUID appId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();

        DecisionRecord existingRecord = new DecisionRecord();
        existingRecord.setId(decisionId);
        existingRecord.setOutcome(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        existingRecord.setDeclineReasons(new ArrayList<>(List.of("Old decline reason")));

        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DECISIONED);
        app.setDecisionRecord(existingRecord);

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(decisionRecordRepository.save(any(DecisionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conditionRepository.save(any(DecisionCondition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionRecord result = service.manualDecision(
                appId,
                DecisionOutcome.APPROVED,
                "underwriter-1",
                List.of("Should be cleared"),
                List.of("Provide updated payslip")
        );

        assertThat(result).isSameAs(existingRecord);
        assertThat(result.getId()).isEqualTo(decisionId);
        assertThat(result.getDecisionType()).isEqualTo(DecisionType.MANUAL);
        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.APPROVED);
        assertThat(result.getDecidedBy()).isEqualTo("underwriter-1");
        assertThat(result.getDeclineReasons()).isEmpty();
        assertThat(result.getConditions()).hasSize(1);
        assertThat(result.getConditions().get(0).getDescription()).isEqualTo("Provide updated payslip");

        verify(stateMachine, never()).transition(any(), any(), any(), any());
        verify(conditionRepository).deleteByDecisionRecordId(decisionId);
        verify(applicationRepository).save(app);
    }

    @Test
    void manualDecision_verifiedApp_transitionsToDecisionedAndCreatesRecord() {
        UUID appId = UUID.randomUUID();

        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.VERIFIED);

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(stateMachine.transition(any(), eq(ApplicationStatus.DECISIONED), eq("underwriter-2"), eq("UNDERWRITER")))
                .thenAnswer(inv -> {
                    app.setStatus(ApplicationStatus.DECISIONED);
                    return ApplicationStatus.DECISIONED;
                });
        when(decisionRecordRepository.save(any(DecisionRecord.class))).thenAnswer(inv -> {
            DecisionRecord record = inv.getArgument(0);
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            return record;
        });
        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        DecisionRecord result = service.manualDecision(
                appId,
                DecisionOutcome.DECLINED,
                "underwriter-2",
                List.of("Credit policy mismatch"),
                List.of()
        );

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.DECISIONED);
        assertThat(result.getDecisionType()).isEqualTo(DecisionType.MANUAL);
        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.DECLINED);
        assertThat(result.getDeclineReasons()).containsExactly("Credit policy mismatch");

        verify(stateMachine).transition(app, ApplicationStatus.DECISIONED, "underwriter-2", "UNDERWRITER");
        verify(conditionRepository, never()).deleteByDecisionRecordId(any());
    }
}
