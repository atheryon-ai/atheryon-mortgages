package com.atheryon.mortgages.rules;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
public class LtvCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    // LTV band limits (percentage of property value as max loan)
    private static final BigDecimal BAND_80 = new BigDecimal("0.80");
    private static final BigDecimal BAND_85 = new BigDecimal("0.85");
    private static final BigDecimal BAND_90 = new BigDecimal("0.90");
    private static final BigDecimal BAND_95 = new BigDecimal("0.95");

    public LtvResult calculate(BigDecimal loanAmount, BigDecimal propertyValue,
                               BigDecimal lmiPremium, boolean capitalised) {
        if (propertyValue == null || propertyValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Property value must be greater than zero");
        }

        // Effective loan = loanAmount + capitalised LMI premium
        BigDecimal effectiveLoan = loanAmount;
        if (capitalised && lmiPremium != null) {
            effectiveLoan = effectiveLoan.add(lmiPremium);
        }

        // LTV = effectiveLoan / propertyValue
        BigDecimal ltv = effectiveLoan.divide(propertyValue, 6, RoundingMode.HALF_UP);
        BigDecimal ltvPercent = ltv.multiply(ONE_HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);

        // Determine band
        String ltvBand;
        boolean lmiRequired;
        BigDecimal maxLoanRatio;

        if (ltvPercent.compareTo(new BigDecimal("80.00")) <= 0) {
            ltvBand = "0-80.00";
            lmiRequired = false;
            maxLoanRatio = BAND_80;
        } else if (ltvPercent.compareTo(new BigDecimal("85.00")) <= 0) {
            ltvBand = "80.01-85.00";
            lmiRequired = true;
            maxLoanRatio = BAND_85;
        } else if (ltvPercent.compareTo(new BigDecimal("90.00")) <= 0) {
            ltvBand = "85.01-90.00";
            lmiRequired = true;
            maxLoanRatio = BAND_90;
        } else if (ltvPercent.compareTo(new BigDecimal("95.00")) <= 0) {
            ltvBand = "90.01-95.00";
            lmiRequired = true;
            maxLoanRatio = BAND_95;
        } else {
            ltvBand = "95.01+";
            lmiRequired = true;
            maxLoanRatio = BAND_95; // Hard cap at 95%
        }

        BigDecimal maxLoan = propertyValue.multiply(maxLoanRatio, MC).setScale(2, RoundingMode.HALF_UP);

        return new LtvResult(ltvPercent, ltvBand, lmiRequired, maxLoan);
    }
}
