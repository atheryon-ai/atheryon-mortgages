package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.dto.request.DocumentRejectRequest;
import com.atheryon.mortgages.dto.request.DocumentUploadRequest;
import com.atheryon.mortgages.dto.request.DocumentVerifyRequest;
import com.atheryon.mortgages.dto.response.DocumentResponse;
import com.atheryon.mortgages.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Document upload and verification")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @Operation(summary = "Upload document metadata")
    public ResponseEntity<DocumentResponse> upload(@Valid @RequestBody DocumentUploadRequest request) {
        Document doc = documentService.upload(
                request.applicationId(),
                request.partyId(),
                request.documentType(),
                request.documentCategory(),
                request.fileName(),
                request.mimeType(),
                request.fileSizeBytes(),
                request.storageReference(),
                request.uploadedBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata by ID")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID id) {
        Document doc = documentService.getById(id);
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify a document")
    public ResponseEntity<DocumentResponse> verify(@PathVariable UUID id,
                                                   @Valid @RequestBody DocumentVerifyRequest request) {
        Document doc = documentService.verify(id, request.verifiedBy());
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a document")
    public ResponseEntity<DocumentResponse> reject(@PathVariable UUID id,
                                                   @Valid @RequestBody DocumentRejectRequest request) {
        Document doc = documentService.reject(id, request.reason());
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @GetMapping("/application/{applicationId}")
    @Operation(summary = "List documents for an application")
    public ResponseEntity<List<DocumentResponse>> getByApplicationId(@PathVariable UUID applicationId) {
        List<Document> docs = documentService.getByApplicationId(applicationId);
        List<DocumentResponse> responses = docs.stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
