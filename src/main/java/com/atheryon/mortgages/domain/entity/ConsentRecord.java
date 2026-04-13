package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.ConsentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentType consentType;

    private boolean granted;

    private LocalDateTime grantedAt;

    private LocalDate expiryDate;

    private LocalDateTime revokedAt;

    private String version;

    private String captureMethod;
}
