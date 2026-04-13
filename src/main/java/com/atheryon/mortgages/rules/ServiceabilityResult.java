package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;

import java.math.BigDecimal;

public class ServiceabilityResult {

    private final BigDecimal netDisposableIncome;
    private final BigDecimal umi;
    private final BigDecimal dsr;
    private final BigDecimal assessmentRate;
    private final ServiceabilityOutcome outcome;

    public ServiceabilityResult(BigDecimal netDisposableIncome, BigDecimal umi,
                                BigDecimal dsr, BigDecimal assessmentRate,
                                ServiceabilityOutcome outcome) {
        this.netDisposableIncome = netDisposableIncome;
        this.umi = umi;
        this.dsr = dsr;
        this.assessmentRate = assessmentRate;
        this.outcome = outcome;
    }

    public BigDecimal getNetDisposableIncome() {
        return netDisposableIncome;
    }

    public BigDecimal getUmi() {
        return umi;
    }

    public BigDecimal getDsr() {
        return dsr;
    }

    public BigDecimal getAssessmentRate() {
        return assessmentRate;
    }

    public ServiceabilityOutcome getOutcome() {
        return outcome;
    }
}
