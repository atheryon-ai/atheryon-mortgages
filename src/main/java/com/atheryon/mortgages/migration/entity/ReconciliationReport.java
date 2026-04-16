package com.atheryon.mortgages.migration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "report_data", nullable = false, columnDefinition = "jsonb")
    private String reportData;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }
}
