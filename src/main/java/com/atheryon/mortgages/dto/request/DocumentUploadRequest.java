package com.atheryon.mortgages.dto.request;

import com.atheryon.mortgages.domain.enums.DocumentCategory;
import com.atheryon.mortgages.domain.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentUploadRequest(
    @NotNull UUID applicationId,
    UUID partyId,
    @NotNull DocumentType documentType,
    @NotNull DocumentCategory documentCategory,
    @NotBlank String fileName,
    @NotBlank String mimeType,
    long fileSizeBytes,
    @NotBlank String storageReference,
    @NotBlank String uploadedBy
) {}
