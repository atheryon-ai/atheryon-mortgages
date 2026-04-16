package com.atheryon.mortgages.lixi.gateway;

import java.util.List;
import java.util.UUID;

public record IngestResponse(
        UUID applicationId,
        String applicationNumber,
        boolean valid,
        int errorCount,
        int warningCount,
        long validationDurationMs,
        List<String> tierSummaries
) {}
