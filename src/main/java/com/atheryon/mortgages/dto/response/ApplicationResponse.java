package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationResponse(
    UUID id,
    String applicationNumber,
    ApplicationStatus status,
    Channel channel,
    UUID productId,
    LoanPurpose purpose,
    OccupancyType occupancyType,
    BigDecimal requestedAmount,
    int termMonths,
    InterestType interestType,
    RepaymentType repaymentType,
    boolean firstHomeBuyer,
    String assignedTo,
    List<PartyResponse> parties,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime submittedAt,
    LocalDateTime decisionedAt
) {
    public static ApplicationResponse from(LoanApplication app) {
        List<PartyResponse> partyResponses;
        try {
            partyResponses = app.getApplicationParties() == null
                ? List.of()
                : app.getApplicationParties().stream()
                    .map(ap -> PartyResponse.from(ap.getParty(), ap.getRole()))
                    .toList();
        } catch (org.hibernate.LazyInitializationException e) {
            partyResponses = List.of();
        }

        return new ApplicationResponse(
            app.getId(),
            app.getApplicationNumber(),
            app.getStatus(),
            app.getChannel(),
            app.getProduct() != null ? app.getProduct().getId() : null,
            app.getPurpose(),
            app.getOccupancyType(),
            app.getRequestedAmount(),
            app.getTermMonths(),
            app.getInterestType(),
            app.getRepaymentType(),
            app.isFirstHomeBuyer(),
            app.getAssignedTo(),
            partyResponses,
            app.getCreatedAt(),
            app.getUpdatedAt(),
            app.getSubmittedAt(),
            app.getDecisionedAt()
        );
    }
}
