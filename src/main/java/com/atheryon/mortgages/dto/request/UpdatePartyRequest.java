package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.PartyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH semantics: every field is nullable. Only non-null fields are applied.
 * Note: kycStatus is intentionally excluded — it is mutated only via the
 * dedicated KYC endpoint, not via generic updates.
 */
public record UpdatePartyRequest(
    PartyType partyType,
    @Size(max = 10) String title,
    @Size(max = 100) String firstName,
    @Size(max = 100) String middleNames,
    @Size(max = 100) String surname,
    LocalDate dateOfBirth,
    @Size(max = 20) String gender,
    @Size(max = 50) String residencyStatus,
    @Size(max = 50) String maritalStatus,
    Integer numberOfDependants,
    @Email @Size(max = 255) String email,
    @Size(max = 30) String mobilePhone
) {}
