package com.atheryon.mortgages.migration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "migration_loan_staging")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationLoanStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "source_data", nullable = false, columnDefinition = "jsonb")
    private String sourceData;

    @Column(name = "mapped_data", columnDefinition = "jsonb")
    private String mappedData;

    @Column(name = "transformed_data", columnDefinition = "jsonb")
    private String transformedData;

    @Column(name = "lixi_data", columnDefinition = "jsonb")
    private String lixiData;

    @Column(name = "validation_result", columnDefinition = "jsonb")
    private String validationResult;

    @Column(length = 10)
    private String classification;

    @Column(name = "quality_score", precision = 5, scale = 4)
    private BigDecimal qualityScore;

    @Column(name = "lifecycle_state", length = 30)
    private String lifecycleState;

    @Column(nullable = false)
    @Builder.Default
    private boolean promoted = false;

    @Column(name = "promoted_id")
    private UUID promotedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
