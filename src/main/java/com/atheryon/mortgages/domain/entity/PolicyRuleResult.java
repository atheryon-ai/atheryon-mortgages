package com.atheryon.mortgages.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "policy_rule_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRuleResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_record_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DecisionRecord decisionRecord;

    @Column(nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private String result;

    private String detail;
}
