package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.DecisionRecord;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.Offer;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.domain.enums.DecisionOutcome;
import com.atheryon.mortgages.domain.enums.OfferStatus;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.OfferRepository;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OfferService {

    private static final int OFFER_VALIDITY_DAYS = 30;

    private final OfferRepository offerRepository;
    private final LoanApplicationRepository applicationRepository;
    private final ApplicationStateMachine stateMachine;

    public OfferService(OfferRepository offerRepository,
                        LoanApplicationRepository applicationRepository,
                        ApplicationStateMachine stateMachine) {
        this.offerRepository = offerRepository;
        this.applicationRepository = applicationRepository;
        this.stateMachine = stateMachine;
    }

    public Offer generateOffer(UUID applicationId) {
        LoanApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        if (app.getStatus() != ApplicationStatus.DECISIONED) {
            throw new BusinessRuleException("INVALID_OFFER_STATE",
                    "Offers can only be generated for applications in DECISIONED status");
        }
        DecisionRecord decision = app.getDecisionRecord();
        if (decision == null
                || (decision.getOutcome() != DecisionOutcome.APPROVED
                        && decision.getOutcome() != DecisionOutcome.CONDITIONALLY_APPROVED)) {
            throw new BusinessRuleException("INVALID_OFFER_OUTCOME",
                    "Offers can only be generated when decisionRecord.outcome is APPROVED or CONDITIONALLY_APPROVED");
        }

        BigDecimal approvedAmount = app.getRequestedAmount();
        BigDecimal interestRate = BigDecimal.ZERO;
        if (app.getProduct() != null && app.getProduct().getLendingRates() != null
                && !app.getProduct().getLendingRates().isEmpty()) {
            interestRate = app.getProduct().getLendingRates().get(0).getRate();
        }

        BigDecimal monthlyRepayment = calculateMonthlyRepayment(
                approvedAmount, interestRate, app.getTermMonths());

        // Determine if LMI is required (LTV > 80%)
        boolean lmiRequired = false;
        if (app.getSecurities() != null && !app.getSecurities().isEmpty()) {
            BigDecimal propertyValue = app.getSecurities().get(0).getPurchasePrice();
            if (propertyValue != null && propertyValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ltv = approvedAmount.divide(propertyValue, 4, RoundingMode.HALF_UP);
                lmiRequired = ltv.compareTo(new BigDecimal("0.80")) > 0;
            }
        }

        Offer offer = new Offer();
        offer.setApplication(app);
        offer.setOfferStatus(OfferStatus.ISSUED);
        offer.setApprovedAmount(approvedAmount);
        offer.setInterestRate(interestRate);
        offer.setTermMonths(app.getTermMonths());
        offer.setEstimatedMonthlyRepayment(monthlyRepayment);
        offer.setExpiryDate(LocalDate.now().plusDays(OFFER_VALIDITY_DAYS));
        offer.setLmiRequired(lmiRequired);
        offer.setOfferDate(LocalDate.now());

        Offer saved = offerRepository.save(offer);
        app.setOffer(saved);

        stateMachine.transition(app, ApplicationStatus.OFFER_ISSUED, "SYSTEM", "SYSTEM");
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        return saved;
    }

    @Transactional(readOnly = true)
    public Offer getById(UUID id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", id));
    }

    public Offer accept(UUID offerId, String acceptedBy, String method) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));

        if (offer.getOfferStatus() != OfferStatus.ISSUED) {
            throw new BusinessRuleException("INVALID_OFFER_STATUS",
                    "Only ISSUED offers can be accepted");
        }

        if (offer.getExpiryDate() != null && offer.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("OFFER_EXPIRED",
                    "This offer has expired and can no longer be accepted");
        }

        offer.setOfferStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedDate(LocalDateTime.now());
        offer.setAcceptedBy(acceptedBy);

        LoanApplication app = offer.getApplication();
        stateMachine.transition(app, ApplicationStatus.ACCEPTED, acceptedBy, "CUSTOMER");
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        return offerRepository.save(offer);
    }

    public Offer decline(UUID offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", "id", offerId));

        offer.setOfferStatus(OfferStatus.DECLINED);

        LoanApplication app = offer.getApplication();
        stateMachine.transition(app, ApplicationStatus.WITHDRAWN, "CUSTOMER", "CUSTOMER");
        app.setUpdatedAt(LocalDateTime.now());
        applicationRepository.save(app);

        return offerRepository.save(offer);
    }

    public List<Offer> expireOffers() {
        List<Offer> expiredOffers = offerRepository.findByOfferStatusAndExpiryDateBefore(
                OfferStatus.ISSUED, LocalDate.now());

        for (Offer offer : expiredOffers) {
            offer.setOfferStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);

            LoanApplication app = offer.getApplication();
            if (app.getStatus() == ApplicationStatus.OFFER_ISSUED) {
                stateMachine.transition(app, ApplicationStatus.LAPSED, "SYSTEM", "SYSTEM");
                app.setUpdatedAt(LocalDateTime.now());
                applicationRepository.save(app);
            }
        }

        return expiredOffers;
    }

    private BigDecimal calculateMonthlyRepayment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        if (principal == null || termMonths <= 0) {
            return BigDecimal.ZERO;
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        double r = annualRate.doubleValue() / 12.0;
        double n = termMonths;
        double p = principal.doubleValue();
        double factor = Math.pow(1 + r, n);
        double monthly = p * (r * factor) / (factor - 1);
        return BigDecimal.valueOf(monthly).setScale(2, RoundingMode.HALF_UP);
    }
}
