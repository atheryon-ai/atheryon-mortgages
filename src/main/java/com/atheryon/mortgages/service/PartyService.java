package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.Party;
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

        if (updates.getFirstName() != null) {
            party.setFirstName(updates.getFirstName());
        }
        if (updates.getSurname() != null) {
            party.setSurname(updates.getSurname());
        }
        if (updates.getEmail() != null) {
            party.setEmail(updates.getEmail());
        }
        if (updates.getDateOfBirth() != null) {
            party.setDateOfBirth(updates.getDateOfBirth());
        }
        if (updates.getKycStatus() != null) {
            party.setKycStatus(updates.getKycStatus());
        }

        return partyRepository.save(party);
    }
}
