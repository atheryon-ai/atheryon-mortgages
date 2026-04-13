package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.LendingRateType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lending_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LendingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LendingRateType lendingRateType;

    @Column(precision = 10, scale = 6, nullable = false)
    private BigDecimal rate;

    @Column(precision = 10, scale = 6)
    private BigDecimal comparisonRate;

    private String calculationFrequency;

    private String applicationFrequency;

    private Integer fixedTermMonths;
}
