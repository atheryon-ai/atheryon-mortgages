package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.DocumentCategory;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.DocumentType;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.DocumentRepository;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final LoanApplicationRepository applicationRepository;

    public DocumentService(DocumentRepository documentRepository,
                           LoanApplicationRepository applicationRepository) {
        this.documentRepository = documentRepository;
        this.applicationRepository = applicationRepository;
    }

    public Document upload(UUID applicationId, UUID partyId, DocumentType type,
                           DocumentCategory category, String fileName, String mimeType,
                           long size, String storageRef, String uploadedBy) {
        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        Document document = new Document();
        document.setApplication(application);
        document.setDocumentType(type);
        document.setDocumentCategory(category);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setFileName(fileName);
        document.setStorageReference(storageRef);
        document.setUploadedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", id));
    }

    public Document verify(UUID documentId, String verifiedBy) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (doc.getStatus() != DocumentStatus.UPLOADED && doc.getStatus() != DocumentStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("INVALID_DOC_STATUS",
                    "Document can only be verified from UPLOADED or UNDER_REVIEW status");
        }

        doc.setStatus(DocumentStatus.VERIFIED);
        doc.setVerifiedAt(LocalDateTime.now());
        return documentRepository.save(doc);
    }

    public Document reject(UUID documentId, String reason) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        doc.setStatus(DocumentStatus.REJECTED);
        doc.setRejectionReason(reason);
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<Document> getByApplicationId(UUID applicationId) {
        return documentRepository.findByApplicationId(applicationId);
    }
}
