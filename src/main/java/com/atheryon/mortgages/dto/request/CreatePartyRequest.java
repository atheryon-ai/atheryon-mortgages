package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.PartyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePartyRequest(
    @NotNull PartyType partyType,
    @Size(max = 10) String title,
    @NotBlank @Size(max = 100) String firstName,
    @Size(max = 100) String middleNames,
    @NotBlank @Size(max = 100) String surname,
    LocalDate dateOfBirth,
    @Size(max = 20) String gender,
    @Size(max = 50) String residencyStatus,
    @Size(max = 50) String maritalStatus,
    Integer numberOfDependants,
    @Email @Size(max = 255) String email,
    @Size(max = 30) String mobilePhone
) {}
