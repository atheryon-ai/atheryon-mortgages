package com.atheryon.mortgages.rules;

import java.math.BigDecimal;

public class LtvResult {

    private final BigDecimal ltv;
    private final String ltvBand;
    private final boolean lmiRequired;
    private final BigDecimal maxLoan;

    public LtvResult(BigDecimal ltv, String ltvBand, boolean lmiRequired, BigDecimal maxLoan) {
        this.ltv = ltv;
        this.ltvBand = ltvBand;
        this.lmiRequired = lmiRequired;
        this.maxLoan = maxLoan;
    }

    public BigDecimal getLtv() {
        return ltv;
    }

    public String getLtvBand() {
        return ltvBand;
    }

    public boolean isLmiRequired() {
        return lmiRequired;
    }

    public BigDecimal getMaxLoan() {
        return maxLoan;
    }
}
