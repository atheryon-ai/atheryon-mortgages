package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.ConditionStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "decision_conditions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_record_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DecisionRecord decisionRecord;

    private String conditionType;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConditionStatus status = ConditionStatus.OUTSTANDING;

    private LocalDate satisfiedDate;

    private String satisfiedBy;
}
