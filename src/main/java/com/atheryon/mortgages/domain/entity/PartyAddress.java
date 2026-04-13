package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.HousingStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "party_addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Party party;

    private String addressType;

    private String unitNumber;

    private String streetNumber;

    private String streetName;

    private String streetType;

    private String suburb;

    private String state;

    private String postcode;

    @Builder.Default
    private String country = "AU";

    private int yearsAtAddress;

    private int monthsAtAddress;

    @Enumerated(EnumType.STRING)
    private HousingStatus housingStatus;
}
