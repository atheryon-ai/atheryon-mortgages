package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.ValuationStatus;
import com.atheryon.mortgages.domain.enums.ValuationType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "valuations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Valuation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PropertySecurity security;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValuationType valuationType;

    private String provider;

    private LocalDate requestedDate;

    private LocalDate completedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ValuationStatus status = ValuationStatus.REQUESTED;

    @Column(precision = 19, scale = 4)
    private BigDecimal estimatedValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal forcedSaleValue;

    private String valuationConfidence;

    @Column(precision = 10, scale = 6)
    private BigDecimal calculatedLtv;

    private String reportReference;

    @Column(length = 2000)
    private String valuerComments;

    private LocalDate expiryDate;
}
