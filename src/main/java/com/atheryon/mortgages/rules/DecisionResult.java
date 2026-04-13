package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.enums.DelegatedAuthorityLevel;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;

import java.util.List;

public class DecisionResult {

    private final DecisionOutcome outcome;
    private final List<String> reasons;
    private final DelegatedAuthorityLevel suggestedDelegatedAuthority;

    public DecisionResult(DecisionOutcome outcome, List<String> reasons,
                          DelegatedAuthorityLevel suggestedDelegatedAuthority) {
        this.outcome = outcome;
        this.reasons = reasons != null ? reasons : List.of();
        this.suggestedDelegatedAuthority = suggestedDelegatedAuthority;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public DelegatedAuthorityLevel getSuggestedDelegatedAuthority() {
        return suggestedDelegatedAuthority;
    }
}
