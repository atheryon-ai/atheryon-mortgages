package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.PartyRole;
import com.atheryon.mortgages.domain.enums.PartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PartyRequest(
    UUID partyId,
    @NotNull PartyRole role,
    BigDecimal ownershipPercentage,
    @NotBlank String firstName,
    @NotBlank String surname,
    LocalDate dateOfBirth,
    PartyType partyType,
    String email,
    String mobilePhone
) {}
