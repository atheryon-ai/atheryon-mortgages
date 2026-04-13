package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.OfferStatus;
import com.atheryon.mortgages.domain.enums.RepaymentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

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
    @Builder.Default
    private OfferStatus offerStatus = OfferStatus.DRAFT;

    private LocalDate offerDate;

    private LocalDate expiryDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(precision = 10, scale = 6)
    private BigDecimal interestRate;

    @Column(precision = 10, scale = 6)
    private BigDecimal comparisonRate;

    private String rateType;

    private Integer fixedTermMonths;

    private int termMonths;

    @Enumerated(EnumType.STRING)
    private RepaymentType repaymentType;

    @Column(precision = 19, scale = 4)
    private BigDecimal estimatedMonthlyRepayment;

    private boolean lmiRequired;

    @Column(precision = 19, scale = 4)
    private BigDecimal lmiPremium;

    private Boolean lmiCapitalised;

    private LocalDateTime acceptedDate;

    private String acceptedBy;

    private String acceptanceMethod;

    private LocalDate coolingOffExpiryDate;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
