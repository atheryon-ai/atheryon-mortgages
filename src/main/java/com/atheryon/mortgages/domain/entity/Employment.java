package com.atheryon.mortgages.domain.entity;

import com.atheryon.mortgages.domain.enums.EmploymentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType;

    private String employerName;

    private String occupation;

    private String industry;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal annualBaseSalary;

    @Column(precision = 19, scale = 4)
    private BigDecimal annualOvertime;

    @Column(precision = 19, scale = 4)
    private BigDecimal annualBonus;

    @Column(precision = 19, scale = 4)
    private BigDecimal annualCommission;

    private boolean isCurrent;
}
