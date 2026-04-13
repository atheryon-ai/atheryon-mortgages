package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.DecisionRecord;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DecisionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
    UUID id,
    DecisionType decisionType,
    DecisionOutcome outcome,
    LocalDateTime decisionDate,
    String decidedBy,
    Integer creditScore,
    List<String> conditions,
    List<String> declineReasons
) {
    public static DecisionResponse from(DecisionRecord record) {
        List<String> conditionDescriptions = record.getConditions() == null
            ? List.of()
            : record.getConditions().stream()
                .map(c -> c.getDescription())
                .toList();

        return new DecisionResponse(
            record.getId(),
            record.getDecisionType(),
            record.getOutcome(),
            record.getDecisionDate(),
            record.getDecidedBy(),
            record.getCreditScore(),
            conditionDescriptions,
            record.getDeclineReasons()
        );
    }
}
