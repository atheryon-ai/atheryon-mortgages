package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.Party;
import com.atheryon.mortgages.domain.enums.KycStatus;
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
    public ResponseEntity<PartyResponse> create(@Valid @RequestBody Party party) {
        Party created = partyService.create(party);
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
                                                @Valid @RequestBody Party party) {
        Party updated = partyService.update(id, party);
        return ResponseEntity.ok(PartyResponse.from(updated));
    }

    @PostMapping("/{id}/kyc")
    @Operation(summary = "Trigger KYC verification")
    public ResponseEntity<PartyResponse> triggerKyc(@PathVariable UUID id) {
        Party party = partyService.getById(id);
        party.setKycStatus(KycStatus.VERIFIED);
        Party updated = partyService.update(id, party);
        return ResponseEntity.ok(PartyResponse.from(updated));
    }
}
