package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "financial_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    private LocalDateTime capturedAt;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalGrossAnnualIncome;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalNetAnnualIncome;

    @Column(precision = 19, scale = 4)
    private BigDecimal declaredMonthlyExpenses;

    @Column(precision = 19, scale = 4)
    private BigDecimal hemMonthlyBenchmark;

    @Column(precision = 19, scale = 4)
    private BigDecimal assessedMonthlyExpenses;

    @Column(precision = 19, scale = 4)
    private BigDecimal netDisposableIncome;

    @Column(precision = 10, scale = 6)
    private BigDecimal debtServiceRatio;

    @Column(precision = 19, scale = 4)
    private BigDecimal uncommittedMonthlyIncome;

    @Column(precision = 10, scale = 6)
    private BigDecimal assessmentRate;

    @Column(precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal bufferRate = new BigDecimal("0.03");

    @Enumerated(EnumType.STRING)
    private ServiceabilityOutcome serviceabilityOutcome;

    @OneToMany(mappedBy = "financialSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IncomeItem> incomeItems = new ArrayList<>();

    @OneToMany(mappedBy = "financialSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExpenseItem> expenseItems = new ArrayList<>();

    @OneToMany(mappedBy = "financialSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Asset> assets = new ArrayList<>();

    @OneToMany(mappedBy = "financialSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Liability> liabilities = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
