package com.atheryon.mortgages.rules;

import com.atheryon.mortgages.domain.entity.ConsentRecord;
import com.atheryon.mortgages.domain.entity.Document;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.FinancialSnapshot;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.DelegatedAuthorityLevel;
import com.atheryon.mortgages.domain.enums.DocumentStatus;
import com.atheryon.mortgages.domain.enums.ServiceabilityOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DecisionEngine {

    private static final BigDecimal UMI_AUTO_APPROVE_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal LTV_AUTO_APPROVE_MAX = new BigDecimal("80");
    private static final BigDecimal LTV_AUTO_DECLINE_MAX = new BigDecimal("95");
    private static final int CREDIT_SCORE_AUTO_APPROVE_MIN = 700;
    private static final int CREDIT_SCORE_AUTO_DECLINE_MAX = 400;

    public DecisionResult evaluate(LoanApplication app, FinancialSnapshot snapshot,
                                   Integer creditScore, BigDecimal ltv) {
        List<String> reasons = new ArrayList<>();

        // Check auto-decline conditions first
        if (snapshot != null && snapshot.getServiceabilityOutcome() == ServiceabilityOutcome.FAIL) {
            reasons.add("Serviceability assessment failed");
            return new DecisionResult(DecisionOutcome.DECLINED, reasons, null);
        }

        if (ltv != null && ltv.compareTo(LTV_AUTO_DECLINE_MAX) > 0) {
            reasons.add("LTV exceeds maximum of 95%");
            return new DecisionResult(DecisionOutcome.DECLINED, reasons, null);
        }

        if (creditScore != null && creditScore < CREDIT_SCORE_AUTO_DECLINE_MAX) {
            reasons.add("Credit score below minimum threshold of 400");
            return new DecisionResult(DecisionOutcome.DECLINED, reasons, null);
        }

        // Check auto-approve conditions
        boolean serviceabilityPass = snapshot != null
                && snapshot.getServiceabilityOutcome() == ServiceabilityOutcome.PASS
                && snapshot.getUncommittedMonthlyIncome() != null
                && snapshot.getUncommittedMonthlyIncome().compareTo(UMI_AUTO_APPROVE_THRESHOLD) > 0;

        boolean ltvOk = ltv != null && ltv.compareTo(LTV_AUTO_APPROVE_MAX) <= 0;
        boolean creditOk = creditScore != null && creditScore >= CREDIT_SCORE_AUTO_APPROVE_MIN;
        boolean docsVerified = allDocumentsVerified(app);
        boolean consentsGranted = allConsentsGranted(app);

        if (serviceabilityPass && ltvOk && creditOk && docsVerified && consentsGranted) {
            reasons.add("All automated checks passed");
            return new DecisionResult(DecisionOutcome.APPROVED, reasons,
                    DelegatedAuthorityLevel.LEVEL_1_AUTO);
        }

        // Otherwise refer to underwriter
        if (!serviceabilityPass) {
            reasons.add("Serviceability requires manual review");
        }
        if (!ltvOk) {
            reasons.add("LTV exceeds 80% — requires underwriter assessment");
        }
        if (!creditOk) {
            reasons.add("Credit score below auto-approve threshold of 700");
        }
        if (!docsVerified) {
            reasons.add("Not all documents verified");
        }
        if (!consentsGranted) {
            reasons.add("Not all consents granted");
        }

        DelegatedAuthorityLevel authority;
        if (ltv != null && ltv.compareTo(new BigDecimal("90")) > 0) {
            authority = DelegatedAuthorityLevel.LEVEL_3_SENIOR_UNDERWRITER;
        } else if (creditScore != null && creditScore < 500) {
            authority = DelegatedAuthorityLevel.LEVEL_3_SENIOR_UNDERWRITER;
        } else {
            authority = DelegatedAuthorityLevel.LEVEL_2_ASSESSOR;
        }

        return new DecisionResult(DecisionOutcome.REFERRED_TO_UNDERWRITER, reasons, authority);
    }

    private boolean allDocumentsVerified(LoanApplication app) {
        if (app.getDocuments() == null || app.getDocuments().isEmpty()) {
            return false;
        }
        return app.getDocuments().stream()
                .allMatch(doc -> doc.getStatus() == DocumentStatus.VERIFIED);
    }

    private boolean allConsentsGranted(LoanApplication app) {
        if (app.getConsents() == null || app.getConsents().isEmpty()) {
            return false;
        }
        return app.getConsents().stream()
                .allMatch(ConsentRecord::isGranted);
    }
}
