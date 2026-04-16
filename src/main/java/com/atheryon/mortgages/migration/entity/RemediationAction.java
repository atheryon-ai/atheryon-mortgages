package com.atheryon.mortgages.migration.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "remediation_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "rule_id", nullable = false, length = 20)
    private String ruleId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String condition;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String transform;

    @Column(name = "affected_rows", nullable = false)
    private int affectedRows;

    @Column(name = "quality_before", nullable = false, precision = 5, scale = 4)
    private BigDecimal qualityBefore;

    @Column(name = "quality_after", nullable = false, precision = 5, scale = 4)
    private BigDecimal qualityAfter;

    @Column(name = "actor_id", nullable = false, length = 100)
    private String actorId;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        if (appliedAt == null) {
            appliedAt = OffsetDateTime.now();
        }
    }
}
