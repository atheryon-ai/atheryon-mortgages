package com.atheryon.mortgages.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "broker_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanApplication application;

    private String brokerId;

    private String brokerCompany;

    private String aggregatorId;

    private String aggregatorName;

    private String brokerReference;
}
