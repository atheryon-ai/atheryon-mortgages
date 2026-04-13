package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DecisionType;
import com.atheryon.mortgages.domain.enums.DelegatedAuthorityLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decision_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionType decisionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionOutcome outcome;

    @Column(nullable = false)
    private LocalDateTime decisionDate;

    private String decidedBy;

    @Enumerated(EnumType.STRING)
    private DelegatedAuthorityLevel delegatedAuthorityLevel;

    private String creditBureau;

    private Integer creditScore;

    private LocalDate creditReportDate;

    private String creditReportReference;

    @Column(precision = 19, scale = 4)
    private BigDecimal maxApprovedAmount;

    @Column(precision = 10, scale = 6)
    private BigDecimal approvedLtv;

    private LocalDate expiryDate;

    @OneToMany(mappedBy = "decisionRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DecisionCondition> conditions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "decision_decline_reasons", joinColumns = @JoinColumn(name = "decision_record_id"))
    @Column(name = "reason")
    @Builder.Default
    private List<String> declineReasons = new ArrayList<>();

    @OneToMany(mappedBy = "decisionRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PolicyRuleResult> policyRuleResults = new ArrayList<>();
}
