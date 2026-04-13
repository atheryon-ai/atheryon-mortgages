package com.atheryon.mortgages.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "party_identifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Party party;

    private String idType;

    private String idNumber;

    private String issuingState;

    private LocalDate expiryDate;

    private boolean verified;
}
