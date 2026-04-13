package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.IncomeItem;
import com.atheryon.mortgages.domain.entity.Liability;
import com.atheryon.mortgages.domain.enums.IncomeType;
import com.atheryon.mortgages.domain.enums.LiabilityType;
import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ServiceabilityCalculatorTest {

    private ServiceabilityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ServiceabilityCalculator();
    }

    @Test
    void calculate_passScenario_goodIncomeAndLowExpenses() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("120000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(new BigDecimal("2000"));
        snapshot.setHemMonthlyBenchmark(new BigDecimal("1800"));
        snapshot.setLiabilities(List.of());

        BigDecimal proposedRepayment = new BigDecimal("2500");
        BigDecimal productRate = new BigDecimal("0.0625");

        ServiceabilityResult result = calculator.calculate(snapshot, proposedRepayment, productRate);

        assertThat(result.getOutcome()).isEqualTo(ServiceabilityOutcome.PASS);
        assertThat(result.getUmi()).isGreaterThan(new BigDecimal("200"));
        assertThat(result.getAssessmentRate()).isEqualByComparingTo(new BigDecimal("0.0925"));
        assertThat(result.getNetDisposableIncome()).isPositive();
    }

    @Test
    void calculate_failScenario_expensesExceedIncome() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("36000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(new BigDecimal("3500"));
        snapshot.setHemMonthlyBenchmark(new BigDecimal("2000"));
        snapshot.setLiabilities(List.of());

        BigDecimal proposedRepayment = new BigDecimal("1500");
        BigDecimal productRate = new BigDecimal("0.065");

        ServiceabilityResult result = calculator.calculate(snapshot, proposedRepayment, productRate);

        assertThat(result.getOutcome()).isEqualTo(ServiceabilityOutcome.FAIL);
        assertThat(result.getUmi()).isLessThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void calculate_marginalScenario_umiBelow200() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("60000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(new BigDecimal("2500"));
        snapshot.setHemMonthlyBenchmark(new BigDecimal("2200"));
        snapshot.setLiabilities(List.of());

        BigDecimal proposedRepayment = new BigDecimal("2350");
        BigDecimal productRate = new BigDecimal("0.065");

        ServiceabilityResult result = calculator.calculate(snapshot, proposedRepayment, productRate);

        assertThat(result.getOutcome()).isEqualTo(ServiceabilityOutcome.MARGINAL);
        assertThat(result.getUmi()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getUmi()).isLessThan(new BigDecimal("200"));
    }

    @Test
    void calculate_creditCardLiabilityUses3Point8PercentOfLimit() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("120000"));

        Liability creditCard = new Liability();
        creditCard.setLiabilityType(LiabilityType.CREDIT_CARD);
        creditCard.setCreditLimit(new BigDecimal("10000"));
        creditCard.setMonthlyRepayment(new BigDecimal("250"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(new BigDecimal("2000"));
        snapshot.setHemMonthlyBenchmark(new BigDecimal("1800"));
        snapshot.setLiabilities(List.of(creditCard));

        BigDecimal proposedRepayment = new BigDecimal("2500");
        BigDecimal productRate = new BigDecimal("0.065");

        ServiceabilityResult result = calculator.calculate(snapshot, proposedRepayment, productRate);

        // Credit card commitment = 10000 * 0.038 = 380 (uses limit, not monthly repayment)
        // UMI = 10000 (shaded monthly) - 2000 (expenses) - 380 (cc) - 2500 (proposed)
        // = 5120 > 200, so PASS
        assertThat(result.getOutcome()).isEqualTo(ServiceabilityOutcome.PASS);

        // Now verify by comparing with no credit card
        FinancialSnapshot snapshotNoCc = new FinancialSnapshot();
        snapshotNoCc.setIncomeItems(List.of(salary));
        snapshotNoCc.setDeclaredMonthlyExpenses(new BigDecimal("2000"));
        snapshotNoCc.setHemMonthlyBenchmark(new BigDecimal("1800"));
        snapshotNoCc.setLiabilities(List.of());

        ServiceabilityResult resultNoCc = calculator.calculate(snapshotNoCc, proposedRepayment, productRate);

        // UMI with credit card should be exactly 380 less than without
        BigDecimal expectedDiff = new BigDecimal("10000").multiply(new BigDecimal("0.038"));
        BigDecimal actualDiff = resultNoCc.getUmi().subtract(result.getUmi());
        assertThat(actualDiff).isEqualByComparingTo(expectedDiff);
    }

    @Test
    void calculate_incomeShadingSalaryAt100Percent() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("120000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(BigDecimal.ZERO);
        snapshot.setHemMonthlyBenchmark(BigDecimal.ZERO);
        snapshot.setLiabilities(List.of());

        ServiceabilityResult result = calculator.calculate(snapshot, BigDecimal.ZERO, new BigDecimal("0.05"));

        // Salary at 100% shading: 120000/12 = 10000
        assertThat(result.getUmi()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void calculate_incomeShadingRentalAt80Percent() {
        IncomeItem rental = new IncomeItem();
        rental.setIncomeType(IncomeType.RENTAL);
        rental.setGrossAnnualAmount(new BigDecimal("60000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(rental));
        snapshot.setDeclaredMonthlyExpenses(BigDecimal.ZERO);
        snapshot.setHemMonthlyBenchmark(BigDecimal.ZERO);
        snapshot.setLiabilities(List.of());

        ServiceabilityResult result = calculator.calculate(snapshot, BigDecimal.ZERO, new BigDecimal("0.05"));

        // Rental at 80%: 60000 * 0.80 / 12 = 4000
        assertThat(result.getUmi()).isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    void calculate_incomeShadingBonusAt50Percent() {
        IncomeItem bonus = new IncomeItem();
        bonus.setIncomeType(IncomeType.BONUS);
        bonus.setGrossAnnualAmount(new BigDecimal("24000"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(bonus));
        snapshot.setDeclaredMonthlyExpenses(BigDecimal.ZERO);
        snapshot.setHemMonthlyBenchmark(BigDecimal.ZERO);
        snapshot.setLiabilities(List.of());

        ServiceabilityResult result = calculator.calculate(snapshot, BigDecimal.ZERO, new BigDecimal("0.05"));

        // Bonus at 50%: 24000 * 0.50 / 12 = 1000
        assertThat(result.getUmi()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void calculate_assessmentRateIsMaxOfProductRatePlus3PercentOr5Point5Percent() {
        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of());
        snapshot.setDeclaredMonthlyExpenses(BigDecimal.ZERO);
        snapshot.setHemMonthlyBenchmark(BigDecimal.ZERO);
        snapshot.setLiabilities(List.of());

        // Product rate 6.25%: assessment = 6.25 + 3 = 9.25% (> 5.50%)
        ServiceabilityResult result1 = calculator.calculate(snapshot, BigDecimal.ZERO, new BigDecimal("0.0625"));
        assertThat(result1.getAssessmentRate()).isEqualByComparingTo(new BigDecimal("0.0925"));

        // Product rate 1.5%: assessment = 1.5 + 3 = 4.5% < 5.50%, so floor of 5.50%
        ServiceabilityResult result2 = calculator.calculate(snapshot, BigDecimal.ZERO, new BigDecimal("0.015"));
        assertThat(result2.getAssessmentRate()).isEqualByComparingTo(new BigDecimal("0.0550"));
    }

    @Test
    void calculate_dsrCapAt50PercentCausesFail() {
        IncomeItem salary = new IncomeItem();
        salary.setIncomeType(IncomeType.SALARY);
        salary.setGrossAnnualAmount(new BigDecimal("60000"));

        Liability personalLoan = new Liability();
        personalLoan.setLiabilityType(LiabilityType.PERSONAL_LOAN);
        personalLoan.setMonthlyRepayment(new BigDecimal("1500"));

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setIncomeItems(List.of(salary));
        snapshot.setDeclaredMonthlyExpenses(new BigDecimal("500"));
        snapshot.setHemMonthlyBenchmark(new BigDecimal("500"));
        snapshot.setLiabilities(List.of(personalLoan));

        // Gross monthly = 5000, existing commitments = 1500, proposed = 1500
        // DSR = (1500 + 1500) / 5000 = 0.60 > 0.50 => FAIL
        BigDecimal proposedRepayment = new BigDecimal("1500");
        BigDecimal productRate = new BigDecimal("0.065");

        ServiceabilityResult result = calculator.calculate(snapshot, proposedRepayment, productRate);

        assertThat(result.getDsr()).isGreaterThan(new BigDecimal("0.50"));
        assertThat(result.getOutcome()).isEqualTo(ServiceabilityOutcome.FAIL);
    }
}
