package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.Party;
import com.atheryon.mortgages.dto.request.CreatePartyRequest;
import com.atheryon.mortgages.dto.request.UpdatePartyRequest;
import com.atheryon.mortgages.dto.response.PartyResponse;
import com.atheryon.mortgages.service.PartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties")
@Tag(name = "Parties", description = "Party (applicant/guarantor) management")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @PostMapping
    @Operation(summary = "Create a new party")
    public ResponseEntity<PartyResponse> create(@Valid @RequestBody CreatePartyRequest request) {
        Party entity = Party.builder()
                .partyType(request.partyType())
                .title(request.title())
                .firstName(request.firstName())
                .middleNames(request.middleNames())
                .surname(request.surname())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .residencyStatus(request.residencyStatus())
                .maritalStatus(request.maritalStatus())
                .numberOfDependants(request.numberOfDependants() != null ? request.numberOfDependants() : 0)
                .email(request.email())
                .mobilePhone(request.mobilePhone())
                .build();
        Party created = partyService.create(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(PartyResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get party by ID")
    public ResponseEntity<PartyResponse> getById(@PathVariable UUID id) {
        Party party = partyService.getById(id);
        return ResponseEntity.ok(PartyResponse.from(party));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update party details")
    public ResponseEntity<PartyResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdatePartyRequest request) {
        Party updates = new Party();
        updates.setPartyType(request.partyType());
        updates.setTitle(request.title());
        updates.setFirstName(request.firstName());
        updates.setMiddleNames(request.middleNames());
        updates.setSurname(request.surname());
        updates.setDateOfBirth(request.dateOfBirth());
        updates.setGender(request.gender());
        updates.setResidencyStatus(request.residencyStatus());
        updates.setMaritalStatus(request.maritalStatus());
        updates.setEmail(request.email());
        updates.setMobilePhone(request.mobilePhone());

        Party updated = partyService.update(id, updates);
        return ResponseEntity.ok(PartyResponse.from(updated));
    }

    @PostMapping("/{id}/kyc")
    @Operation(summary = "Trigger KYC verification")
    public ResponseEntity<PartyResponse> triggerKyc(@PathVariable UUID id) {
        Party updated = partyService.triggerKyc(id);
        return ResponseEntity.ok(PartyResponse.from(updated));
    }
}
