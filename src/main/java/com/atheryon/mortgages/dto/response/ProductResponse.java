package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.enums.ProductType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    ProductType productType,
    String name,
    String brand,
    LocalDate effectiveFrom,
    BigDecimal maximumLtv,
    BigDecimal minimumLoanAmount,
    BigDecimal maximumLoanAmount
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getProductType(),
            product.getName(),
            product.getBrand(),
            product.getEffectiveFrom(),
            product.getMaximumLtv(),
            product.getMinimumLoanAmount(),
            product.getMaximumLoanAmount()
        );
    }
}
