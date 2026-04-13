package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.LiabilityType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "liabilities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Liability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_snapshot_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FinancialSnapshot financialSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiabilityType liabilityType;

    private String lender;

    @Column(precision = 19, scale = 4)
    private BigDecimal outstandingBalance;

    @Column(precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(precision = 19, scale = 4)
    private BigDecimal monthlyRepayment;

    @Column(precision = 10, scale = 6)
    private BigDecimal interestRate;

    private boolean toBeRefinanced;

    private UUID partyId;
}
