package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.PropertyCategory;
import com.atheryon.mortgages.domain.enums.SecurityType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "securities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySecurity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityType securityType;

    private String primaryPurpose;

    @Enumerated(EnumType.STRING)
    private PropertyCategory propertyCategory;

    private String streetNumber;

    private String streetName;

    private String streetType;

    private String suburb;

    private String state;

    private String postcode;

    private Integer numberOfBedrooms;

    @Column(precision = 19, scale = 4)
    private BigDecimal landAreaSqm;

    private Integer yearBuilt;

    private boolean isNewConstruction;

    @Column(precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    private LocalDate contractDate;

    @OneToOne(mappedBy = "security", cascade = CascadeType.ALL, orphanRemoval = true)
    private Valuation valuation;

    @OneToOne(mappedBy = "security", cascade = CascadeType.ALL, orphanRemoval = true)
    private LmiQuote lmiQuote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
