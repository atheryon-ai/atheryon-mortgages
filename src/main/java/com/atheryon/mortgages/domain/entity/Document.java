package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.DocumentCategory;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentCategory documentCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.REQUESTED;

    private String fileName;

    private String mimeType;

    private long fileSizeBytes;

    private String storageReference;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private String verifiedBy;

    private LocalDateTime verifiedAt;

    private String rejectionReason;

    private LocalDate expiryDate;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
