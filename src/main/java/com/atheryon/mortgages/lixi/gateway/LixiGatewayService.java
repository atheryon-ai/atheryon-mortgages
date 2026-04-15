package com.atheryon.mortgages.lixi.gateway;

import com.atheryon.mortgages.domain.entity.*;
import com.atheryon.mortgages.lixi.mapper.LixiCalMapper;
import com.atheryon.mortgages.lixi.mapper.MappingResult;
import com.atheryon.mortgages.lixi.message.LixiMessage;
import com.atheryon.mortgages.lixi.message.LixiMessageRepository;
import com.atheryon.mortgages.lixi.schema.LixiValidationPipeline;
import com.atheryon.mortgages.lixi.schema.LixiValidationPipeline.PipelineResult;
import com.atheryon.mortgages.lixi.schema.ValidationResult;
import com.atheryon.mortgages.service.ApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LixiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(LixiGatewayService.class);

    private final LixiValidationPipeline validationPipeline;
    private final LixiCalMapper calMapper;
    private final ApplicationService applicationService;
    private final LixiMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public LixiGatewayService(LixiValidationPipeline validationPipeline,
                               LixiCalMapper calMapper,
                               ApplicationService applicationService,
                               LixiMessageRepository messageRepository,
                               ObjectMapper objectMapper) {
        this.validationPipeline = validationPipeline;
        this.calMapper = calMapper;
        this.applicationService = applicationService;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    public IngestResponse ingest(String rawJson) {
        log.info("Ingesting LIXI2 CAL message ({} bytes)", rawJson.length());

        PipelineResult validation = validationPipeline.validate(rawJson);

        if (!validation.overallValid()) {
            log.info("LIXI2 message failed validation with {} errors", validation.allErrors().size());
            storeMessage(null, rawJson, validation);
            return new IngestResponse(
                    null,
                    null,
                    false,
                    validation.allErrors().size(),
                    validation.allWarnings().size(),
                    validation.totalDurationMs(),
                    buildTierSummaries(validation)
            );
        }

        JsonNode jsonNode = parseJson(rawJson);
        MappingResult mapping = calMapper.mapFromCal(jsonNode);
        if (!mapping.warnings().isEmpty()) {
            log.info("LIXI2 mapping completed with {} warnings", mapping.warnings().size());
        }

        LoanApplication app = mapping.application();
        linkEntities(app, mapping);
        LoanApplication saved = applicationService.create(app);

        log.info("LIXI2 ingest complete: applicationId={}, applicationNumber={}",
                saved.getId(), saved.getApplicationNumber());

        storeMessage(saved.getId(), rawJson, validation);

        return new IngestResponse(
                saved.getId(),
                saved.getApplicationNumber(),
                true,
                0,
                validation.allWarnings().size(),
                validation.totalDurationMs(),
                buildTierSummaries(validation)
        );
    }

    @Transactional(readOnly = true)
    public ValidationResponse validateOnly(String rawJson) {
        log.info("Validating LIXI2 CAL message ({} bytes)", rawJson.length());
        PipelineResult result = validationPipeline.validate(rawJson);
        return toValidationResponse(result);
    }

    @Transactional(readOnly = true)
    public List<LixiMessage> getMessages(UUID applicationId) {
        return messageRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private void linkEntities(LoanApplication app, MappingResult mapping) {
        for (MappingResult.PartyWithRole pwr : mapping.parties()) {
            ApplicationParty ap = new ApplicationParty();
            ap.setApplication(app);
            ap.setParty(pwr.party());
            ap.setRole(pwr.role());
            app.getApplicationParties().add(ap);
        }

        for (PropertySecurity sec : mapping.securities()) {
            sec.setApplication(app);
            app.getSecurities().add(sec);
        }

        if (mapping.financialSnapshot() != null) {
            mapping.financialSnapshot().setApplication(app);
            app.setFinancialSnapshot(mapping.financialSnapshot());
        }

        for (ConsentRecord consent : mapping.consents()) {
            consent.setApplication(app);
            app.getConsents().add(consent);
        }

        if (mapping.brokerDetail() != null) {
            mapping.brokerDetail().setApplication(app);
            app.setBrokerDetail(mapping.brokerDetail());
        }
    }

    private void storeMessage(UUID applicationId, String rawJson, PipelineResult validation) {
        String validationJson;
        try {
            validationJson = objectMapper.writeValueAsString(validation);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize validation result", e);
            validationJson = null;
        }

        LixiMessage message = LixiMessage.builder()
                .applicationId(applicationId)
                .direction("INBOUND")
                .standard("LIXI2")
                .version("2.6.91")
                .format("JSON")
                .payload(rawJson)
                .validationResult(validationJson)
                .build();

        messageRepository.save(message);
    }

    private JsonNode parseJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private List<String> buildTierSummaries(PipelineResult result) {
        return result.tierResults().stream()
                .map(tr -> String.format("%s: %s (%dms)",
                        tr.tier(),
                        tr.valid() ? "PASS" : "FAIL",
                        tr.durationMs()))
                .toList();
    }

    private ValidationResponse toValidationResponse(PipelineResult result) {
        List<ValidationResponse.TierResult> tiers = result.tierResults().stream()
                .map(tr -> new ValidationResponse.TierResult(
                        tr.tier(),
                        tr.valid(),
                        tr.errors().stream().map(ValidationResult.ValidationError::message).toList(),
                        tr.warnings().stream().map(ValidationResult.ValidationWarning::message).toList(),
                        tr.durationMs()))
                .toList();

        return new ValidationResponse(result.overallValid(), tiers, result.totalDurationMs());
    }
}
