package com.atheryon.mortgages.migration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "migration_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "source_filename", nullable = false, length = 500)
    private String sourceFilename;

    @Column(name = "source_row_count", nullable = false)
    private long sourceRowCount;

    @Column(name = "source_column_count", nullable = false)
    private int sourceColumnCount;

    @Column(name = "lender_code", length = 50)
    private String lenderCode;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "CREATED";

    @Column(name = "quality_report", columnDefinition = "jsonb")
    private String qualityReport;

    @Column(columnDefinition = "jsonb")
    private String reconciliation;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
