package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.PropertyCategory;
import com.atheryon.mortgages.domain.enums.SecurityType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSecurityRequest(
    @NotNull UUID applicationId,
    @NotNull SecurityType securityType,
    String primaryPurpose,
    PropertyCategory propertyCategory,
    String streetNumber,
    String streetName,
    String streetType,
    String suburb,
    String state,
    String postcode,
    Integer numberOfBedrooms,
    BigDecimal landAreaSqm,
    Integer yearBuilt,
    Boolean isNewConstruction,
    BigDecimal purchasePrice,
    LocalDate contractDate
) {}
