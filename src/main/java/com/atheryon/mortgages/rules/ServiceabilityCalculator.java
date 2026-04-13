package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.IncomeItem;
import com.atheryon.mortgages.domain.entity.Liability;
import com.atheryon.mortgages.domain.enums.IncomeType;
import com.atheryon.mortgages.domain.enums.LiabilityType;
import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class ServiceabilityCalculator {

    private static final BigDecimal BUFFER_RATE = new BigDecimal("0.03");
    private static final BigDecimal FLOOR_RATE = new BigDecimal("0.0550");
    private static final BigDecimal DSR_HARD_CAP = new BigDecimal("0.50");
    private static final BigDecimal MARGINAL_THRESHOLD = new BigDecimal("200");
    private static final BigDecimal CREDIT_CARD_MONTHLY_RATE = new BigDecimal("0.038");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final MathContext MC = MathContext.DECIMAL128;

    private static final Map<IncomeType, BigDecimal> INCOME_SHADING = Map.of(
            IncomeType.SALARY, new BigDecimal("1.00"),
            IncomeType.OVERTIME, new BigDecimal("0.80"),
            IncomeType.BONUS, new BigDecimal("0.50"),
            IncomeType.COMMISSION, new BigDecimal("0.80"),
            IncomeType.RENTAL, new BigDecimal("0.80"),
            IncomeType.SELF_EMPLOYED_INCOME, new BigDecimal("1.00"),
            IncomeType.GOVERNMENT_BENEFIT, new BigDecimal("1.00"),
            IncomeType.SUPERANNUATION, new BigDecimal("1.00")
    );

    public ServiceabilityResult calculate(FinancialSnapshot snapshot,
                                          BigDecimal proposedRepayment,
                                          BigDecimal productRate) {
        // Assessment rate = max(productRate + 3%, 5.50%)
        BigDecimal assessmentRate = productRate.add(BUFFER_RATE).max(FLOOR_RATE);

        // Calculate shaded gross monthly income
        BigDecimal grossMonthlyIncome = BigDecimal.ZERO;
        BigDecimal netMonthlyIncome = BigDecimal.ZERO;

        if (snapshot.getIncomeItems() != null) {
            for (IncomeItem item : snapshot.getIncomeItems()) {
                BigDecimal annualAmount = item.getGrossAnnualAmount() != null ? item.getGrossAnnualAmount() : BigDecimal.ZERO;
                BigDecimal shading = INCOME_SHADING.getOrDefault(item.getIncomeType(), new BigDecimal("0.80"));
                BigDecimal shadedAnnual = annualAmount.multiply(shading, MC);
                BigDecimal monthlyGross = annualAmount.divide(TWELVE, 2, RoundingMode.HALF_UP);
                BigDecimal monthlyShaded = shadedAnnual.divide(TWELVE, 2, RoundingMode.HALF_UP);

                grossMonthlyIncome = grossMonthlyIncome.add(monthlyGross);
                netMonthlyIncome = netMonthlyIncome.add(monthlyShaded);
            }
        }

        // Expenses = max(declared, HEM benchmark)
        BigDecimal declaredExpenses = snapshot.getDeclaredMonthlyExpenses() != null
                ? snapshot.getDeclaredMonthlyExpenses() : BigDecimal.ZERO;
        BigDecimal hemBenchmark = snapshot.getHemMonthlyBenchmark() != null
                ? snapshot.getHemMonthlyBenchmark() : BigDecimal.ZERO;
        BigDecimal assessedExpenses = declaredExpenses.max(hemBenchmark);

        // Calculate existing commitments (monthly)
        BigDecimal existingCommitments = BigDecimal.ZERO;
        if (snapshot.getLiabilities() != null) {
            for (Liability liability : snapshot.getLiabilities()) {
                if (liability.getLiabilityType() == LiabilityType.CREDIT_CARD) {
                    // Credit card: 3.8% of limit per month
                    BigDecimal limit = liability.getCreditLimit() != null ? liability.getCreditLimit() : BigDecimal.ZERO;
                    existingCommitments = existingCommitments.add(
                            limit.multiply(CREDIT_CARD_MONTHLY_RATE, MC));
                } else {
                    BigDecimal monthlyPayment = liability.getMonthlyRepayment() != null
                            ? liability.getMonthlyRepayment() : BigDecimal.ZERO;
                    existingCommitments = existingCommitments.add(monthlyPayment);
                }
            }
        }

        // UMI = Net monthly income - assessed expenses - existing commitments - proposed repayment at assessment rate
        BigDecimal umi = netMonthlyIncome
                .subtract(assessedExpenses)
                .subtract(existingCommitments)
                .subtract(proposedRepayment);

        // NDI (net disposable income) = net monthly income - assessed expenses
        BigDecimal ndi = netMonthlyIncome.subtract(assessedExpenses);

        // DSR = (all commitments + proposed) / gross monthly income
        BigDecimal totalCommitments = existingCommitments.add(proposedRepayment);
        BigDecimal dsr = BigDecimal.ZERO;
        if (grossMonthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            dsr = totalCommitments.divide(grossMonthlyIncome, 4, RoundingMode.HALF_UP);
        }

        // Determine outcome
        ServiceabilityOutcome outcome;
        if (umi.compareTo(BigDecimal.ZERO) <= 0 || dsr.compareTo(DSR_HARD_CAP) > 0) {
            outcome = ServiceabilityOutcome.FAIL;
        } else if (umi.compareTo(MARGINAL_THRESHOLD) < 0) {
            outcome = ServiceabilityOutcome.MARGINAL;
        } else {
            outcome = ServiceabilityOutcome.PASS;
        }

        return new ServiceabilityResult(ndi, umi, dsr, assessmentRate, outcome);
    }
}
