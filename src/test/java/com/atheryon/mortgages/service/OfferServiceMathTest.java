package com.atheryon.mortgages.service;

import com.atheryon.mortgages.util.MortgageMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the BigDecimal amortisation math used by {@link OfferService} and
 * {@link AssessmentService}. The math lives in {@link MortgageMath} so it
 * can be exercised without reflection or mocking the surrounding services.
 */
class OfferServiceMathTest {

    @Test
    void calculateMonthlyRepayment_standardLoan_matchesBankFormula() {
        // $500,000 principal, 6% APR, 30-year term
        // Standard amortisation formula yields $2997.75 (verified against
        // independent amortisation calculators).
        BigDecimal principal = new BigDecimal("500000");
        BigDecimal annualRate = new BigDecimal("0.06");
        int termMonths = 360;

        BigDecimal repayment = MortgageMath.monthlyRepayment(principal, annualRate, termMonths);

        assertThat(repayment).isEqualByComparingTo(new BigDecimal("2997.75"));
        assertThat(repayment.scale()).isEqualTo(2);
    }

    @Test
    void calculateMonthlyRepayment_zeroInterest_returnsPrincipalOverTerm() {
        // $360,000 / 360 months = $1000.00 with no interest
        BigDecimal repayment = MortgageMath.monthlyRepayment(
                new BigDecimal("360000"), BigDecimal.ZERO, 360);

        assertThat(repayment).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(repayment.scale()).isEqualTo(2);
    }

    @Test
    void calculateMonthlyRepayment_nullPrincipal_returnsZero() {
        BigDecimal repayment = MortgageMath.monthlyRepayment(null, new BigDecimal("0.06"), 360);
        assertThat(repayment).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateMonthlyRepayment_zeroTerm_returnsZero() {
        BigDecimal repayment = MortgageMath.monthlyRepayment(
                new BigDecimal("500000"), new BigDecimal("0.06"), 0);
        assertThat(repayment).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateMonthlyRepayment_nullAnnualRate_returnsPrincipalOverTerm() {
        BigDecimal repayment = MortgageMath.monthlyRepayment(
                new BigDecimal("360000"), null, 360);
        assertThat(repayment).isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
