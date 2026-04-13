package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.PartyRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "application_parties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationParty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyRole role;

    @Column(precision = 10, scale = 6)
    private BigDecimal ownershipPercentage;
}
