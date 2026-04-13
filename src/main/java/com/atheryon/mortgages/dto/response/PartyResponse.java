package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.Party;
import com.atheryon.mortgages.domain.enums.KycStatus;
import com.atheryon.mortgages.domain.enums.PartyRole;
import com.atheryon.mortgages.domain.enums.PartyType;

import java.util.UUID;

public record PartyResponse(
    UUID id,
    PartyType partyType,
    PartyRole role,
    String firstName,
    String surname,
    String email,
    KycStatus kycStatus
) {
    public static PartyResponse from(Party party, PartyRole role) {
        return new PartyResponse(
            party.getId(),
            party.getPartyType(),
            role,
            party.getFirstName(),
            party.getSurname(),
            party.getEmail(),
            party.getKycStatus()
        );
    }

    public static PartyResponse from(Party party) {
        return from(party, null);
    }
}
