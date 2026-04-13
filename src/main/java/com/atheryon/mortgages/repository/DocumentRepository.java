package com.atheryon.mortgages.repository;

import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByApplicationId(UUID applicationId);

    List<Document> findByApplicationIdAndDocumentType(UUID appId, DocumentType type);

    List<Document> findByApplicationIdAndStatus(UUID appId, DocumentStatus status);
}
