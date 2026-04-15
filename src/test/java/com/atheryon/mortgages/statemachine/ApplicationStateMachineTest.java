package com.atheryon.mortgages.statemachine;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.atheryon.mortgages.domain.enums.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ApplicationStateMachineTest {

    private ApplicationStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ApplicationStateMachine();
    }

    private LoanApplication appWithStatus(ApplicationStatus status) {
        LoanApplication app = new LoanApplication();
        app.setStatus(status);
        return app;
    }

    // --- Forward lifecycle transitions ---

    @Test
    void transition_draftToInProgress_valid() {
        LoanApplication app = appWithStatus(DRAFT);
        ApplicationStatus result = stateMachine.transition(app, IN_PROGRESS, "user1", "USER");
        assertThat(result).isEqualTo(IN_PROGRESS);
        assertThat(app.getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void transition_inProgressToReadyForSubmission_valid() {
        LoanApplication app = appWithStatus(IN_PROGRESS);
        ApplicationStatus result = stateMachine.transition(app, READY_FOR_SUBMISSION, "user1", "USER");
        assertThat(result).isEqualTo(READY_FOR_SUBMISSION);
    }

    @Test
    void transition_readyForSubmissionToSubmitted_valid() {
        LoanApplication app = appWithStatus(READY_FOR_SUBMISSION);
        ApplicationStatus result = stateMachine.transition(app, SUBMITTED, "user1", "SYSTEM");
        assertThat(result).isEqualTo(SUBMITTED);
    }

    @Test
    void transition_submittedToUnderAssessment_valid() {
        LoanApplication app = appWithStatus(SUBMITTED);
        ApplicationStatus result = stateMachine.transition(app, UNDER_ASSESSMENT, "assessor1", "ASSESSOR");
        assertThat(result).isEqualTo(UNDER_ASSESSMENT);
    }

    @Test
    void transition_underAssessmentToVerified_valid() {
        LoanApplication app = appWithStatus(UNDER_ASSESSMENT);
        ApplicationStatus result = stateMachine.transition(app, VERIFIED, "assessor1", "ASSESSOR");
        assertThat(result).isEqualTo(VERIFIED);
    }

    @Test
    void transition_verifiedToDecisioned_valid() {
        LoanApplication app = appWithStatus(VERIFIED);
        ApplicationStatus result = stateMachine.transition(app, DECISIONED, "system", "SYSTEM");
        assertThat(result).isEqualTo(DECISIONED);
    }

    @Test
    void transition_decisionedToOfferIssued_valid() {
        LoanApplication app = appWithStatus(DECISIONED);
        ApplicationStatus result = stateMachine.transition(app, OFFER_ISSUED, "system", "SYSTEM");
        assertThat(result).isEqualTo(OFFER_ISSUED);
    }

    @Test
    void transition_offerIssuedToAccepted_valid() {
        LoanApplication app = appWithStatus(OFFER_ISSUED);
        ApplicationStatus result = stateMachine.transition(app, ACCEPTED, "customer", "CUSTOMER");
        assertThat(result).isEqualTo(ACCEPTED);
    }

    @Test
    void transition_offerIssuedToLapsed_valid() {
        LoanApplication app = appWithStatus(OFFER_ISSUED);
        ApplicationStatus result = stateMachine.transition(app, LAPSED, "system", "SYSTEM");
        assertThat(result).isEqualTo(LAPSED);
    }

    @Test
    void transition_acceptedToSettlementInProgress_valid() {
        LoanApplication app = appWithStatus(ACCEPTED);
        ApplicationStatus result = stateMachine.transition(app, SETTLEMENT_IN_PROGRESS, "system", "SYSTEM");
        assertThat(result).isEqualTo(SETTLEMENT_IN_PROGRESS);
    }

    @Test
    void transition_settlementInProgressToSettled_valid() {
        LoanApplication app = appWithStatus(SETTLEMENT_IN_PROGRESS);
        ApplicationStatus result = stateMachine.transition(app, SETTLED, "system", "SYSTEM");
        assertThat(result).isEqualTo(SETTLED);
    }

    @Test
    void transition_settledToServicing_valid() {
        LoanApplication app = appWithStatus(SETTLED);
        ApplicationStatus result = stateMachine.transition(app, SERVICING, "system", "SYSTEM");
        assertThat(result).isEqualTo(SERVICING);
    }

    // --- Cross-cutting WITHDRAWN terminal ---

    @Test
    void transition_draftToWithdrawn_valid() {
        LoanApplication app = appWithStatus(DRAFT);
        ApplicationStatus result = stateMachine.transition(app, WITHDRAWN, "user1", "USER");
        assertThat(result).isEqualTo(WITHDRAWN);
    }

    @Test
    void transition_submittedToWithdrawn_valid() {
        LoanApplication app = appWithStatus(SUBMITTED);
        assertThat(stateMachine.transition(appWithStatus(SUBMITTED), WITHDRAWN, "u", "USER")).isEqualTo(WITHDRAWN);
    }

    @Test
    void transition_decisionedToWithdrawn_valid() {
        assertThat(stateMachine.transition(appWithStatus(DECISIONED), WITHDRAWN, "u", "USER")).isEqualTo(WITHDRAWN);
    }

    @Test
    void transition_acceptedToWithdrawn_valid() {
        assertThat(stateMachine.transition(appWithStatus(ACCEPTED), WITHDRAWN, "u", "USER")).isEqualTo(WITHDRAWN);
    }

    // --- Step-back transitions (rework) ---

    @Test
    void transition_inProgressToDraft_valid() {
        LoanApplication app = appWithStatus(IN_PROGRESS);
        assertThat(stateMachine.transition(app, DRAFT, "u", "USER")).isEqualTo(DRAFT);
    }

    @Test
    void transition_readyForSubmissionToInProgress_valid() {
        LoanApplication app = appWithStatus(READY_FOR_SUBMISSION);
        assertThat(stateMachine.transition(app, IN_PROGRESS, "u", "USER")).isEqualTo(IN_PROGRESS);
    }

    // --- canTransition checks ---

    @Test
    void canTransition_validTransition_returnsTrue() {
        assertThat(stateMachine.canTransition(DRAFT, IN_PROGRESS)).isTrue();
        assertThat(stateMachine.canTransition(SUBMITTED, UNDER_ASSESSMENT)).isTrue();
        assertThat(stateMachine.canTransition(DECISIONED, OFFER_ISSUED)).isTrue();
        assertThat(stateMachine.canTransition(OFFER_ISSUED, ACCEPTED)).isTrue();
    }

    @Test
    void canTransition_invalidTransition_returnsFalse() {
        assertThat(stateMachine.canTransition(DRAFT, SUBMITTED)).isFalse();
        assertThat(stateMachine.canTransition(SETTLED, DRAFT)).isFalse();
        assertThat(stateMachine.canTransition(DECISIONED, ACCEPTED)).isFalse();
        assertThat(stateMachine.canTransition(SUBMITTED, OFFER_ISSUED)).isFalse();
    }

    // --- Terminal states have no outgoing transitions ---

    @Test
    void canTransition_fromWithdrawn_alwaysFalse() {
        assertThat(stateMachine.canTransition(WITHDRAWN, DRAFT)).isFalse();
        assertThat(stateMachine.canTransition(WITHDRAWN, IN_PROGRESS)).isFalse();
    }

    @Test
    void canTransition_fromLapsed_alwaysFalse() {
        assertThat(stateMachine.canTransition(LAPSED, OFFER_ISSUED)).isFalse();
    }

    @Test
    void canTransition_fromServicing_alwaysFalse() {
        assertThat(stateMachine.canTransition(SERVICING, SETTLED)).isFalse();
    }

    // --- Invalid transitions throw exception ---

    @Test
    void transition_draftToSubmitted_throwsInvalidStateTransitionException() {
        LoanApplication app = appWithStatus(DRAFT);

        assertThatThrownBy(() -> stateMachine.transition(app, SUBMITTED, "user1", "USER"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DRAFT")
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    void transition_settledToDraft_throwsInvalidStateTransitionException() {
        LoanApplication app = appWithStatus(SETTLED);

        assertThatThrownBy(() -> stateMachine.transition(app, DRAFT, "user1", "USER"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("SETTLED")
                .hasMessageContaining("DRAFT");
    }

    @Test
    void transition_withdrawnToDraft_throwsInvalidStateTransitionException() {
        LoanApplication app = appWithStatus(WITHDRAWN);

        assertThatThrownBy(() -> stateMachine.transition(app, DRAFT, "user1", "USER"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("WITHDRAWN");
    }
}
