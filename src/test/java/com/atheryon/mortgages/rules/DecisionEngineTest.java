package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.entity.ConsentRecord;
import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.enums.ConsentType;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DelegatedAuthorityLevel;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DecisionEngineTest {

    private DecisionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DecisionEngine();
    }

    private LoanApplication createAppWithDocsAndConsents(DocumentStatus docStatus, boolean consentGranted) {
        LoanApplication app = new LoanApplication();

        Document doc = new Document();
        doc.setStatus(docStatus);
        app.setDocuments(List.of(doc));

        ConsentRecord consent = new ConsentRecord();
        consent.setConsentType(ConsentType.CREDIT_CHECK);
        consent.setGranted(consentGranted);
        app.setConsents(List.of(consent));

        return app;
    }

    @Test
    void evaluate_autoApprove_allConditionsMet() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        Integer creditScore = 750;
        BigDecimal ltv = new BigDecimal("75");

        DecisionResult result = engine.evaluate(app, snapshot, creditScore, ltv);

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.APPROVED);
        assertThat(result.getSuggestedDelegatedAuthority()).isEqualTo(DelegatedAuthorityLevel.LEVEL_1_AUTO);
        assertThat(result.getReasons()).contains("All automated checks passed");
    }

    @Test
    void evaluate_autoDecline_serviceabilityFail() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.FAIL);

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.DECLINED);
        assertThat(result.getReasons()).contains("Serviceability assessment failed");
    }

    @Test
    void evaluate_autoDecline_ltvOver95() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("96"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.DECLINED);
        assertThat(result.getReasons()).contains("LTV exceeds maximum of 95%");
    }

    @Test
    void evaluate_autoDecline_creditScoreBelow400() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 350, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.DECLINED);
        assertThat(result.getReasons()).contains("Credit score below minimum threshold of 400");
    }

    @Test
    void evaluate_referral_marginalServiceability() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.MARGINAL);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("100"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getReasons()).contains("Serviceability requires manual review");
    }

    @Test
    void evaluate_referral_ltvOver80ButUnder95() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("85"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getReasons()).contains("LTV exceeds 80% — requires underwriter assessment");
    }

    @Test
    void evaluate_referral_documentsNotVerified() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.UPLOADED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getReasons()).contains("Not all documents verified");
    }

    @Test
    void evaluate_referral_consentsNotGranted() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, false);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getReasons()).contains("Not all consents granted");
    }

    @Test
    void evaluate_seniorUnderwriter_ltvOver90() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 750, new BigDecimal("92"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getSuggestedDelegatedAuthority())
                .isEqualTo(DelegatedAuthorityLevel.LEVEL_3_SENIOR_UNDERWRITER);
    }

    @Test
    void evaluate_seniorUnderwriter_creditScoreBelow500() {
        LoanApplication app = createAppWithDocsAndConsents(DocumentStatus.VERIFIED, true);

        FinancialSnapshot snapshot = new FinancialSnapshot();
        snapshot.setServiceabilityOutcome(ServiceabilityOutcome.PASS);
        snapshot.setUncommittedMonthlyIncome(new BigDecimal("600"));

        DecisionResult result = engine.evaluate(app, snapshot, 450, new BigDecimal("75"));

        assertThat(result.getOutcome()).isEqualTo(DecisionOutcome.REFERRED_TO_UNDERWRITER);
        assertThat(result.getSuggestedDelegatedAuthority())
                .isEqualTo(DelegatedAuthorityLevel.LEVEL_3_SENIOR_UNDERWRITER);
    }
}
