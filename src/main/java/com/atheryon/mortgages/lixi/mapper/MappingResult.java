package com.atheryon.mortgages.lixi.mapper;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.domain.enums.PartyRole;

import java.util.List;

public record MappingResult(
        LoanApplication application,
        List<PartyWithRole> parties,
        List<PropertySecurity> securities,
        FinancialSnapshot financialSnapshot,
        List<ConsentRecord> consents,
        BrokerDetail brokerDetail,
        List<MappingWarning> warnings
) {
    public record PartyWithRole(
            Party party,
            PartyRole role,
            List<PartyAddress> addresses,
            List<Employment> employments,
            List<PartyIdentification> identifications
    ) {}

    public record MappingWarning(String lixiPath, String reason) {}
}
