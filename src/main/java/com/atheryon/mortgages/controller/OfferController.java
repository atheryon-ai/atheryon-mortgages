package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.Offer;
import com.atheryon.mortgages.dto.request.AcceptOfferRequest;
import com.atheryon.mortgages.dto.response.OfferResponse;
import com.atheryon.mortgages.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offers")
@Tag(name = "Offers", description = "Loan offer generation and acceptance")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/application/{applicationId}")
    @Operation(summary = "Generate an offer for an application")
    public ResponseEntity<OfferResponse> generateOffer(@PathVariable UUID applicationId) {
        Offer offer = offerService.generateOffer(applicationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(OfferResponse.from(offer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get offer by ID")
    public ResponseEntity<OfferResponse> getById(@PathVariable UUID id) {
        Offer offer = offerService.getById(id);
        return ResponseEntity.ok(OfferResponse.from(offer));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept an offer")
    public ResponseEntity<OfferResponse> accept(@PathVariable UUID id,
                                                @Valid @RequestBody AcceptOfferRequest request) {
        Offer offer = offerService.accept(id, request.acceptedBy(), request.acceptanceMethod());
        return ResponseEntity.ok(OfferResponse.from(offer));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline an offer")
    public ResponseEntity<OfferResponse> decline(@PathVariable UUID id) {
        Offer offer = offerService.decline(id);
        return ResponseEntity.ok(OfferResponse.from(offer));
    }
}
