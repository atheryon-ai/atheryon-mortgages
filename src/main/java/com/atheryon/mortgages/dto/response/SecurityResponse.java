package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.PropertySecurity;
import com.atheryon.mortgages.domain.enums.PropertyCategory;
import com.atheryon.mortgages.domain.enums.SecurityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record SecurityResponse(
    UUID id,
    SecurityType securityType,
    PropertyCategory propertyCategory,
    String suburb,
    String state,
    String postcode,
    BigDecimal purchasePrice,
    BigDecimal valuationValue,
    BigDecimal calculatedLtv
) {
    public static SecurityResponse from(PropertySecurity security, BigDecimal loanAmount) {
        BigDecimal valValue = null;
        if (security.getValuation() != null) {
            valValue = security.getValuation().getEstimatedValue();
        }

        BigDecimal ltv = null;
        if (loanAmount != null && valValue != null && valValue.compareTo(BigDecimal.ZERO) > 0) {
            ltv = loanAmount.divide(valValue, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        }

        return new SecurityResponse(
            security.getId(),
            security.getSecurityType(),
            security.getPropertyCategory(),
            security.getSuburb(),
            security.getState(),
            security.getPostcode(),
            security.getPurchasePrice(),
            valValue,
            ltv
        );
    }

    public static SecurityResponse from(PropertySecurity security) {
        return from(security, null);
    }
}
