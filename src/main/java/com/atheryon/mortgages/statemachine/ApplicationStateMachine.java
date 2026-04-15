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
            Map.entry(IN_PROGRESS, Set.of(READY_FOR_SUBMISSION, DRAFT, WITHDRAWN)),
            Map.entry(READY_FOR_SUBMISSION, Set.of(SUBMITTED, IN_PROGRESS, WITHDRAWN)),
            Map.entry(SUBMITTED, Set.of(UNDER_ASSESSMENT, WITHDRAWN)),
            Map.entry(UNDER_ASSESSMENT, Set.of(VERIFIED, WITHDRAWN)),
            Map.entry(VERIFIED, Set.of(DECISIONED, WITHDRAWN)),
            Map.entry(DECISIONED, Set.of(OFFER_ISSUED, WITHDRAWN)),
            Map.entry(OFFER_ISSUED, Set.of(ACCEPTED, LAPSED, WITHDRAWN)),
            Map.entry(ACCEPTED, Set.of(SETTLEMENT_IN_PROGRESS, WITHDRAWN)),
            Map.entry(SETTLEMENT_IN_PROGRESS, Set.of(SETTLED, WITHDRAWN)),
            Map.entry(SETTLED, Set.of(SERVICING))
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
