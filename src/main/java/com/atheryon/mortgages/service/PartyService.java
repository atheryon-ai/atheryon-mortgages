package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.Party;
import com.atheryon.mortgages.domain.enums.KycStatus;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class PartyService {

    private final PartyRepository partyRepository;

    public PartyService(PartyRepository partyRepository) {
        this.partyRepository = partyRepository;
    }

    public Party create(Party party) {
        return partyRepository.save(party);
    }

    @Transactional(readOnly = true)
    public Party getById(UUID id) {
        return partyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", id));
    }

    public Party update(UUID id, Party updates) {
        Party party = getById(id);

        if (updates.getPartyType() != null) {
            party.setPartyType(updates.getPartyType());
        }
        if (updates.getTitle() != null) {
            party.setTitle(updates.getTitle());
        }
        if (updates.getFirstName() != null) {
            party.setFirstName(updates.getFirstName());
        }
        if (updates.getMiddleNames() != null) {
            party.setMiddleNames(updates.getMiddleNames());
        }
        if (updates.getSurname() != null) {
            party.setSurname(updates.getSurname());
        }
        if (updates.getDateOfBirth() != null) {
            party.setDateOfBirth(updates.getDateOfBirth());
        }
        if (updates.getGender() != null) {
            party.setGender(updates.getGender());
        }
        if (updates.getResidencyStatus() != null) {
            party.setResidencyStatus(updates.getResidencyStatus());
        }
        if (updates.getMaritalStatus() != null) {
            party.setMaritalStatus(updates.getMaritalStatus());
        }
        if (updates.getEmail() != null) {
            party.setEmail(updates.getEmail());
        }
        if (updates.getMobilePhone() != null) {
            party.setMobilePhone(updates.getMobilePhone());
        }
        // kycStatus is intentionally NOT updated here — use triggerKyc().

        return partyRepository.save(party);
    }

    public Party triggerKyc(UUID id) {
        Party party = getById(id);
        party.setKycStatus(KycStatus.VERIFIED);
        return partyRepository.save(party);
    }
}
