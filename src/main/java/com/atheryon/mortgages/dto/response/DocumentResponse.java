package com.atheryon.mortgages.dto.response;

import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.domain.enums.DocumentCategory;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    DocumentType documentType,
    DocumentCategory documentCategory,
    DocumentStatus status,
    String fileName,
    LocalDateTime uploadedAt,
    LocalDateTime verifiedAt,
    String rejectionReason
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
            doc.getId(),
            doc.getDocumentType(),
            doc.getDocumentCategory(),
            doc.getStatus(),
            doc.getFileName(),
            doc.getUploadedAt(),
            doc.getVerifiedAt(),
            doc.getRejectionReason()
        );
    }
}
