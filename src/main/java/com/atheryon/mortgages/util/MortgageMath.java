package com.atheryon.mortgages.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Mortgage math utilities. Kept in BigDecimal end-to-end to avoid
 * cents-level rounding errors that creep in via {@code double}-based
 * implementations of the amortisation formula.
 */
public final class MortgageMath {

    /** Intermediate-precision math context: 16 significant digits, half-up. */
    private static final MathContext MC = MathContext.DECIMAL64;

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private MortgageMath() {
        // utility class
    }

    /**
     * Standard amortisation formula:
     * <pre>
     *     M = P * [ r(1+r)^n ] / [ (1+r)^n - 1 ]
     * </pre>
     * where {@code P} is principal, {@code r} is the monthly interest rate
     * (annual / 12) and {@code n} is the number of monthly payments.
     *
     * <p>Returns {@link BigDecimal#ZERO} when principal is null or term is
     * non-positive. When the annual rate is null or zero, returns the
     * straight-line repayment ({@code principal / termMonths}).
     *
     * <p>Result is scaled to 2 decimal places with HALF_UP rounding.
     */
    public static BigDecimal monthlyRepayment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        if (principal == null || termMonths <= 0) {
            return BigDecimal.ZERO;
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal r = annualRate.divide(TWELVE, MC);
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
        BigDecimal factor = onePlusR.pow(termMonths, MC);

        BigDecimal numerator = principal.multiply(r, MC).multiply(factor, MC);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE, MC);

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
