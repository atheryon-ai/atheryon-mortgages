package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.LmiQuote;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.PropertySecurity;
import com.atheryon.mortgages.domain.entity.Valuation;
import com.atheryon.mortgages.domain.enums.LmiQuoteStatus;
import com.atheryon.mortgages.domain.enums.ValuationStatus;
import com.atheryon.mortgages.domain.enums.ValuationType;
import com.atheryon.mortgages.exception.ResourceNotFoundException;
import com.atheryon.mortgages.repository.LmiQuoteRepository;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.PropertySecurityRepository;
import com.atheryon.mortgages.repository.ValuationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class SecurityService {

    private final PropertySecurityRepository securityRepository;
    private final ValuationRepository valuationRepository;
    private final LmiQuoteRepository lmiQuoteRepository;
    private final LoanApplicationRepository applicationRepository;

    public SecurityService(PropertySecurityRepository securityRepository,
                           ValuationRepository valuationRepository,
                           LmiQuoteRepository lmiQuoteRepository,
                           LoanApplicationRepository applicationRepository) {
        this.securityRepository = securityRepository;
        this.valuationRepository = valuationRepository;
        this.lmiQuoteRepository = lmiQuoteRepository;
        this.applicationRepository = applicationRepository;
    }

    public PropertySecurity create(PropertySecurity security) {
        return securityRepository.save(security);
    }

    @Transactional(readOnly = true)
    public PropertySecurity getById(UUID id) {
        return securityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PropertySecurity", "id", id));
    }

    public Valuation requestValuation(UUID securityId, ValuationType type, String provider) {
        PropertySecurity security = getById(securityId);

        Valuation valuation = new Valuation();
        valuation.setSecurity(security);
        valuation.setValuationType(type);
        valuation.setStatus(ValuationStatus.REQUESTED);
        valuation.setRequestedDate(LocalDate.now());
        valuation.setProvider(provider);

        Valuation saved = valuationRepository.save(valuation);
        security.setValuation(saved);
        securityRepository.save(security);
        return saved;
    }

    public LmiQuote requestLmiQuote(UUID securityId, UUID applicationId) {
        PropertySecurity security = getById(securityId);
        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", "id", applicationId));

        LmiQuote quote = new LmiQuote();
        quote.setSecurity(security);
        quote.setStatus(LmiQuoteStatus.QUOTED);
        quote.setQuoteDate(LocalDate.now());
        quote.setApplicationId(applicationId);

        LmiQuote saved = lmiQuoteRepository.save(quote);
        security.setLmiQuote(saved);
        securityRepository.save(security);
        return saved;
    }
}
