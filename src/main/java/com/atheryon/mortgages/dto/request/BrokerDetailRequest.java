package com.atheryon.mortgages.dto.request;

public record BrokerDetailRequest(
    String brokerId,
    String brokerCompany,
    String aggregatorId,
    String aggregatorName,
    String brokerReference
) {}
