package com.atheryon.mortgages.statemachine;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.atheryon.mortgages.domain.enums.ApplicationStatus.*;

@Service
public class ApplicationStateMachine {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT, Set.of(IN_PROGRESS, WITHDRAWN)),
            Map.entry(IN_PROGRESS, Set.of(READY_FOR_SUBMISSION, WITHDRAWN)),
            Map.entry(READY_FOR_SUBMISSION, Set.of(SUBMITTED, IN_PROGRESS)),
            Map.entry(SUBMITTED, Set.of(UNDER_ASSESSMENT, WITHDRAWN)),
            Map.entry(UNDER_ASSESSMENT, Set.of(VERIFIED, DECLINED, WITHDRAWN)),
            Map.entry(VERIFIED, Set.of(DECISIONED)),
            Map.entry(DECISIONED, Set.of(APPROVED, CONDITIONALLY_APPROVED, DECLINED)),
            Map.entry(CONDITIONALLY_APPROVED, Set.of(APPROVED, DECLINED, WITHDRAWN)),
            Map.entry(APPROVED, Set.of(OFFER_ISSUED)),
            Map.entry(OFFER_ISSUED, Set.of(OFFER_ACCEPTED, LAPSED, WITHDRAWN)),
            Map.entry(OFFER_ACCEPTED, Set.of(SETTLEMENT_IN_PROGRESS)),
            Map.entry(SETTLEMENT_IN_PROGRESS, Set.of(SETTLED, WITHDRAWN))
    );

    public boolean canTransition(ApplicationStatus from, ApplicationStatus to) {
        Set<ApplicationStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public ApplicationStatus transition(LoanApplication app, ApplicationStatus targetStatus,
                                        String actorId, String actorType) {
        ApplicationStatus currentStatus = app.getStatus();
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus);
        }
        app.setStatus(targetStatus);
        return targetStatus;
    }
}
