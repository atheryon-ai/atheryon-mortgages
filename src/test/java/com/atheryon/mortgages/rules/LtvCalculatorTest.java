package com.atheryon.mortgages.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LtvCalculatorTest {

    private LtvCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new LtvCalculator();
    }

    @Test
    void calculate_ltvAt60Percent_noLmiRequired() {
        BigDecimal loanAmount = new BigDecimal("300000");
        BigDecimal propertyValue = new BigDecimal("500000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(result.getLtvBand()).isEqualTo("0-80.00");
        assertThat(result.isLmiRequired()).isFalse();
    }

    @Test
    void calculate_ltvAt75Percent_noLmiRequired() {
        BigDecimal loanAmount = new BigDecimal("375000");
        BigDecimal propertyValue = new BigDecimal("500000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getLtvBand()).isEqualTo("0-80.00");
        assertThat(result.isLmiRequired()).isFalse();
    }

    @Test
    void calculate_ltvAt85Percent_lmiRequired() {
        BigDecimal loanAmount = new BigDecimal("425000");
        BigDecimal propertyValue = new BigDecimal("500000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(result.getLtvBand()).isEqualTo("80.01-85.00");
        assertThat(result.isLmiRequired()).isTrue();
    }

    @Test
    void calculate_ltvAt92Percent_lmiRequiredAndMaxLoanCapped() {
        BigDecimal loanAmount = new BigDecimal("920000");
        BigDecimal propertyValue = new BigDecimal("1000000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(result.getLtvBand()).isEqualTo("90.01-95.00");
        assertThat(result.isLmiRequired()).isTrue();
        assertThat(result.getMaxLoan()).isEqualByComparingTo(new BigDecimal("950000.00"));
    }

    @Test
    void calculate_ltvOver95Percent_stillCalculatesWithBand95Plus() {
        BigDecimal loanAmount = new BigDecimal("960000");
        BigDecimal propertyValue = new BigDecimal("1000000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("96.00"));
        assertThat(result.getLtvBand()).isEqualTo("95.01+");
        assertThat(result.isLmiRequired()).isTrue();
        // Max loan capped at 95% of property value
        assertThat(result.getMaxLoan()).isEqualByComparingTo(new BigDecimal("950000.00"));
    }

    @Test
    void calculate_exactlyAt80Percent_noLmiRequired() {
        BigDecimal loanAmount = new BigDecimal("400000");
        BigDecimal propertyValue = new BigDecimal("500000");

        LtvResult result = calculator.calculate(loanAmount, propertyValue, BigDecimal.ZERO, false);

        assertThat(result.getLtv()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(result.getLtvBand()).isEqualTo("0-80.00");
        assertThat(result.isLmiRequired()).isFalse();
    }

    @Test
    void calculate_capitalisedLmiIncreasesEffectiveLoan() {
        BigDecimal loanAmount = new BigDecimal("395000");
        BigDecimal propertyValue = new BigDecimal("500000");
        BigDecimal lmiPremium = new BigDecimal("10000");

        // Without capitalised LMI: 395000/500000 = 79%
        LtvResult resultNotCapitalised = calculator.calculate(loanAmount, propertyValue, lmiPremium, false);
        assertThat(resultNotCapitalised.getLtv()).isEqualByComparingTo(new BigDecimal("79.00"));
        assertThat(resultNotCapitalised.isLmiRequired()).isFalse();

        // With capitalised LMI: (395000+10000)/500000 = 81%
        LtvResult resultCapitalised = calculator.calculate(loanAmount, propertyValue, lmiPremium, true);
        assertThat(resultCapitalised.getLtv()).isEqualByComparingTo(new BigDecimal("81.00"));
        assertThat(resultCapitalised.isLmiRequired()).isTrue();
    }

    @Test
    void calculate_zeroPropertyValue_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                calculator.calculate(new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property value must be greater than zero");
    }

    @Test
    void calculate_nullPropertyValue_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                calculator.calculate(new BigDecimal("300000"), null, BigDecimal.ZERO, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
