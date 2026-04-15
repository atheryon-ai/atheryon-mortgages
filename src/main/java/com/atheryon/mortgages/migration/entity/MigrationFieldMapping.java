package com.atheryon.mortgages.migration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "migration_field_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "source_column", nullable = false, length = 200)
    private String sourceColumn;

    @Column(name = "target_path", length = 500)
    private String targetPath;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = "SUGGESTED";

    @Column(name = "transform_type", length = 20)
    private String transformType;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
