package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.LmiQuote;
import com.atheryon.mortgages.domain.entity.PropertySecurity;
import com.atheryon.mortgages.domain.entity.Valuation;
import com.atheryon.mortgages.dto.request.CreateSecurityRequest;
import com.atheryon.mortgages.dto.request.ValuationRequest;
import com.atheryon.mortgages.dto.response.SecurityResponse;
import com.atheryon.mortgages.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/securities")
@Tag(name = "Securities", description = "Property security and valuation management")
public class SecurityController {

    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @PostMapping
    @Operation(summary = "Create a property security")
    public ResponseEntity<SecurityResponse> create(@Valid @RequestBody CreateSecurityRequest request) {
        PropertySecurity security = PropertySecurity.builder()
                .securityType(request.securityType())
                .primaryPurpose(request.primaryPurpose())
                .propertyCategory(request.propertyCategory())
                .streetNumber(request.streetNumber())
                .streetName(request.streetName())
                .streetType(request.streetType())
                .suburb(request.suburb())
                .state(request.state())
                .postcode(request.postcode())
                .numberOfBedrooms(request.numberOfBedrooms())
                .landAreaSqm(request.landAreaSqm())
                .yearBuilt(request.yearBuilt())
                .isNewConstruction(request.isNewConstruction() != null && request.isNewConstruction())
                .purchasePrice(request.purchasePrice())
                .contractDate(request.contractDate())
                .build();
        PropertySecurity created = securityService.create(security);
        return ResponseEntity.status(HttpStatus.CREATED).body(SecurityResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get security by ID")
    public ResponseEntity<SecurityResponse> getById(@PathVariable UUID id) {
        PropertySecurity security = securityService.getById(id);
        return ResponseEntity.ok(SecurityResponse.from(security));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update security details")
    public ResponseEntity<SecurityResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CreateSecurityRequest request) {
        PropertySecurity security = securityService.getById(id);
        if (request.securityType() != null) security.setSecurityType(request.securityType());
        if (request.primaryPurpose() != null) security.setPrimaryPurpose(request.primaryPurpose());
        if (request.propertyCategory() != null) security.setPropertyCategory(request.propertyCategory());
        if (request.streetNumber() != null) security.setStreetNumber(request.streetNumber());
        if (request.streetName() != null) security.setStreetName(request.streetName());
        if (request.streetType() != null) security.setStreetType(request.streetType());
        if (request.suburb() != null) security.setSuburb(request.suburb());
        if (request.state() != null) security.setState(request.state());
        if (request.postcode() != null) security.setPostcode(request.postcode());
        if (request.numberOfBedrooms() != null) security.setNumberOfBedrooms(request.numberOfBedrooms());
        if (request.landAreaSqm() != null) security.setLandAreaSqm(request.landAreaSqm());
        if (request.yearBuilt() != null) security.setYearBuilt(request.yearBuilt());
        if (request.isNewConstruction() != null) security.setNewConstruction(request.isNewConstruction());
        if (request.purchasePrice() != null) security.setPurchasePrice(request.purchasePrice());
        if (request.contractDate() != null) security.setContractDate(request.contractDate());
        // Re-save via create (service handles update)
        PropertySecurity updated = securityService.create(security);
        return ResponseEntity.ok(SecurityResponse.from(updated));
    }

    @PostMapping("/{id}/valuation")
    @Operation(summary = "Request property valuation")
    public ResponseEntity<SecurityResponse> requestValuation(@PathVariable UUID id,
                                                             @Valid @RequestBody ValuationRequest request) {
        Valuation valuation = securityService.requestValuation(id, request.valuationType(), request.provider());
        PropertySecurity security = securityService.getById(id);
        return ResponseEntity.ok(SecurityResponse.from(security));
    }

    @PostMapping("/{id}/lmi-quote")
    @Operation(summary = "Request LMI quote")
    public ResponseEntity<SecurityResponse> requestLmiQuote(@PathVariable UUID id,
                                                            @RequestParam UUID applicationId) {
        LmiQuote quote = securityService.requestLmiQuote(id, applicationId);
        PropertySecurity security = securityService.getById(id);
        return ResponseEntity.ok(SecurityResponse.from(security));
    }
}
