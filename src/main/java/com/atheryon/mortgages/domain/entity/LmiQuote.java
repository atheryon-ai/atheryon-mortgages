package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.LmiQuoteStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lmi_quotes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LmiQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PropertySecurity security;

    private UUID applicationId;

    private String insurer;

    private LocalDate quoteDate;

    private String ltvBand;

    @Column(precision = 19, scale = 4)
    private BigDecimal loanAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal propertyValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(precision = 19, scale = 4)
    private BigDecimal stampDutyOnPremium;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalCost;

    private boolean capitalised;

    private String quoteReference;

    private LocalDate quoteExpiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LmiQuoteStatus status = LmiQuoteStatus.QUOTED;
}
