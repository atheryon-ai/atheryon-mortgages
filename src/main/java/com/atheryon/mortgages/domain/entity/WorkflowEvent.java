package com.atheryon.mortgages.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private String actorType;

    private String actorId;

    private String actorName;

    private String previousState;

    private String newState;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private String ipAddress;

    private String userAgent;

    private String correlationId;
}
