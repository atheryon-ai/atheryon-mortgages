package com.atheryon.mortgages.exception;

import com.atheryon.mortgages.domain.enums.ApplicationStatus;

public class InvalidStateTransitionException extends RuntimeException {

    private final ApplicationStatus currentStatus;
    private final ApplicationStatus targetStatus;

    public InvalidStateTransitionException(ApplicationStatus currentStatus, ApplicationStatus targetStatus) {
        super(String.format("Invalid state transition from %s to %s", currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public ApplicationStatus getCurrentStatus() {
        return currentStatus;
    }

    public ApplicationStatus getTargetStatus() {
        return targetStatus;
    }
}
