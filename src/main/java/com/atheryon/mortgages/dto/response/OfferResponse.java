package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.Offer;
import com.atheryon.mortgages.domain.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record OfferResponse(
    UUID id,
    OfferStatus offerStatus,
    LocalDate offerDate,
    LocalDate expiryDate,
    BigDecimal approvedAmount,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal estimatedMonthlyRepayment,
    boolean lmiRequired,
    LocalDateTime acceptedDate
) {
    public static OfferResponse from(Offer offer) {
        return new OfferResponse(
            offer.getId(),
            offer.getOfferStatus(),
            offer.getOfferDate(),
            offer.getExpiryDate(),
            offer.getApprovedAmount(),
            offer.getInterestRate(),
            offer.getTermMonths(),
            offer.getEstimatedMonthlyRepayment(),
            offer.isLmiRequired(),
            offer.getAcceptedDate()
        );
    }
}
