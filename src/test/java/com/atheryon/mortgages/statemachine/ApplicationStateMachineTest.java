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

    // --- Valid transitions ---

    @Test
    void transition_draftToInProgress_valid() {
        LoanApplication app = appWithStatus(DRAFT);
        ApplicationStatus result = stateMachine.transition(app, IN_PROGRESS, "user1", "USER");
        assertThat(result).isEqualTo(IN_PROGRESS);
        assertThat(app.getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void transition_draftToWithdrawn_valid() {
        LoanApplication app = appWithStatus(DRAFT);
        ApplicationStatus result = stateMachine.transition(app, WITHDRAWN, "user1", "USER");
        assertThat(result).isEqualTo(WITHDRAWN);
        assertThat(app.getStatus()).isEqualTo(WITHDRAWN);
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
    void transition_underAssessmentToDeclined_valid() {
        LoanApplication app = appWithStatus(UNDER_ASSESSMENT);
        ApplicationStatus result = stateMachine.transition(app, DECLINED, "assessor1", "ASSESSOR");
        assertThat(result).isEqualTo(DECLINED);
    }

    @Test
    void transition_verifiedToDecisioned_valid() {
        LoanApplication app = appWithStatus(VERIFIED);
        ApplicationStatus result = stateMachine.transition(app, DECISIONED, "system", "SYSTEM");
        assertThat(result).isEqualTo(DECISIONED);
    }

    @Test
    void transition_decisionedToApproved_valid() {
        LoanApplication app = appWithStatus(DECISIONED);
        ApplicationStatus result = stateMachine.transition(app, APPROVED, "system", "SYSTEM");
        assertThat(result).isEqualTo(APPROVED);
    }

    @Test
    void transition_decisionedToConditionallyApproved_valid() {
        LoanApplication app = appWithStatus(DECISIONED);
        ApplicationStatus result = stateMachine.transition(app, CONDITIONALLY_APPROVED, "system", "SYSTEM");
        assertThat(result).isEqualTo(CONDITIONALLY_APPROVED);
    }

    @Test
    void transition_decisionedToDeclined_valid() {
        LoanApplication app = appWithStatus(DECISIONED);
        ApplicationStatus result = stateMachine.transition(app, DECLINED, "system", "SYSTEM");
        assertThat(result).isEqualTo(DECLINED);
    }

    @Test
    void transition_conditionallyApprovedToApproved_valid() {
        LoanApplication app = appWithStatus(CONDITIONALLY_APPROVED);
        ApplicationStatus result = stateMachine.transition(app, APPROVED, "underwriter", "UNDERWRITER");
        assertThat(result).isEqualTo(APPROVED);
    }

    @Test
    void transition_conditionallyApprovedToDeclined_valid() {
        LoanApplication app = appWithStatus(CONDITIONALLY_APPROVED);
        ApplicationStatus result = stateMachine.transition(app, DECLINED, "underwriter", "UNDERWRITER");
        assertThat(result).isEqualTo(DECLINED);
    }

    @Test
    void transition_approvedToOfferIssued_valid() {
        LoanApplication app = appWithStatus(APPROVED);
        ApplicationStatus result = stateMachine.transition(app, OFFER_ISSUED, "system", "SYSTEM");
        assertThat(result).isEqualTo(OFFER_ISSUED);
    }

    @Test
    void transition_offerIssuedToOfferAccepted_valid() {
        LoanApplication app = appWithStatus(OFFER_ISSUED);
        ApplicationStatus result = stateMachine.transition(app, OFFER_ACCEPTED, "customer", "CUSTOMER");
        assertThat(result).isEqualTo(OFFER_ACCEPTED);
    }

    @Test
    void transition_offerIssuedToLapsed_valid() {
        LoanApplication app = appWithStatus(OFFER_ISSUED);
        ApplicationStatus result = stateMachine.transition(app, LAPSED, "system", "SYSTEM");
        assertThat(result).isEqualTo(LAPSED);
    }

    @Test
    void transition_settlementInProgressToSettled_valid() {
        LoanApplication app = appWithStatus(SETTLEMENT_IN_PROGRESS);
        ApplicationStatus result = stateMachine.transition(app, SETTLED, "system", "SYSTEM");
        assertThat(result).isEqualTo(SETTLED);
    }

    // --- canTransition checks ---

    @Test
    void canTransition_validTransition_returnsTrue() {
        assertThat(stateMachine.canTransition(DRAFT, IN_PROGRESS)).isTrue();
        assertThat(stateMachine.canTransition(SUBMITTED, UNDER_ASSESSMENT)).isTrue();
        assertThat(stateMachine.canTransition(DECISIONED, APPROVED)).isTrue();
    }

    @Test
    void canTransition_invalidTransition_returnsFalse() {
        assertThat(stateMachine.canTransition(DRAFT, SUBMITTED)).isFalse();
        assertThat(stateMachine.canTransition(SETTLED, DRAFT)).isFalse();
        assertThat(stateMachine.canTransition(DECLINED, APPROVED)).isFalse();
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
    void transition_declinedToApproved_throwsInvalidStateTransitionException() {
        LoanApplication app = appWithStatus(DECLINED);

        assertThatThrownBy(() -> stateMachine.transition(app, APPROVED, "user1", "USER"))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DECLINED")
                .hasMessageContaining("APPROVED");
    }
}
