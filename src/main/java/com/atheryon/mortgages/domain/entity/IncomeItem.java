package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.IncomeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "income_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_snapshot_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FinancialSnapshot financialSnapshot;

    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomeType incomeType;

    @Column(precision = 19, scale = 4)
    private BigDecimal grossAnnualAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal netAnnualAmount;

    private String frequency;

    private boolean verified;

    private String verificationSource;
}
