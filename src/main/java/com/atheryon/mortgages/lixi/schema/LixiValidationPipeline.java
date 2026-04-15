package com.atheryon.mortgages.lixi.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the 4-tier LIXI2 validation pipeline.
 * Tier 1: Schema validation, Tier 2: Schematron business rules,
 * Tier 3: Lender-specific rules, Tier 4: Domain rules (SRS MR-001 to MR-010).
 * Pipeline stops early on Tier 1/2 failures; Tiers 3 and 4 always both run.
 */
@Service
public class LixiValidationPipeline {

    private static final Logger log = LoggerFactory.getLogger(LixiValidationPipeline.class);

    private final LixiSchemaValidator schemaValidator;
    private final LixiSchematronValidator schematronValidator;
    private final LenderRuleValidator lenderRuleValidator;
    private final DomainRuleValidator domainRuleValidator;
    private final ObjectMapper objectMapper;

    public record PipelineResult(
            List<ValidationResult> tierResults,
            boolean overallValid,
            long totalDurationMs
    ) {
        public List<ValidationResult.ValidationError> allErrors() {
            return tierResults.stream()
                    .flatMap(r -> r.errors().stream())
                    .toList();
        }

        public List<ValidationResult.ValidationWarning> allWarnings() {
            return tierResults.stream()
                    .flatMap(r -> r.warnings().stream())
                    .toList();
        }
    }

    public LixiValidationPipeline(LixiSchemaValidator schemaValidator,
                                   LixiSchematronValidator schematronValidator,
                                   LenderRuleValidator lenderRuleValidator,
                                   DomainRuleValidator domainRuleValidator,
                                   ObjectMapper objectMapper) {
        this.schemaValidator = schemaValidator;
        this.schematronValidator = schematronValidator;
        this.lenderRuleValidator = lenderRuleValidator;
        this.domainRuleValidator = domainRuleValidator;
        this.objectMapper = objectMapper;
    }

    public PipelineResult validate(JsonNode lixiMessage) {
        long start = System.nanoTime();
        List<ValidationResult> results = new ArrayList<>();

        // Tier 1: Schema validation
        ValidationResult schemaResult = schemaValidator.validate(lixiMessage);
        results.add(schemaResult);
        log.debug("Tier 1 (Schema): valid={}, errors={}, duration={}ms",
                schemaResult.valid(), schemaResult.errors().size(), schemaResult.durationMs());

        // Stop pipeline if schema has hard failures (not just warnings from permissive mode)
        if (!schemaResult.valid()) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Validation pipeline stopped at Tier 1 with {} errors ({}ms)",
                    schemaResult.errors().size(), totalMs);
            return new PipelineResult(results, false, totalMs);
        }

        // Tier 2: Schematron business rules
        ValidationResult schematronResult = schematronValidator.validate(lixiMessage);
        results.add(schematronResult);
        log.debug("Tier 2 (Schematron): valid={}, errors={}, warnings={}, duration={}ms",
                schematronResult.valid(), schematronResult.errors().size(),
                schematronResult.warnings().size(), schematronResult.durationMs());

        // Stop pipeline if Tier 2 has critical failures
        if (!schematronResult.valid()) {
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Validation pipeline stopped at Tier 2 with {} errors ({}ms)",
                    schematronResult.errors().size(), totalMs);
            return new PipelineResult(results, false, totalMs);
        }

        // Tier 3: Lender-specific rules
        ValidationResult lenderResult = lenderRuleValidator.validate(lixiMessage, "DEFAULT");
        results.add(lenderResult);
        log.debug("Tier 3 (Lender): valid={}, errors={}, warnings={}, duration={}ms",
                lenderResult.valid(), lenderResult.errors().size(),
                lenderResult.warnings().size(), lenderResult.durationMs());

        // Tier 4: Domain rules (always run regardless of Tier 3 result)
        ValidationResult domainResult = domainRuleValidator.validate(lixiMessage);
        results.add(domainResult);
        log.debug("Tier 4 (Domain): valid={}, errors={}, warnings={}, duration={}ms",
                domainResult.valid(), domainResult.errors().size(),
                domainResult.warnings().size(), domainResult.durationMs());

        long totalMs = (System.nanoTime() - start) / 1_000_000;
        boolean overallValid = results.stream().allMatch(ValidationResult::valid);

        log.info("Validation pipeline complete: valid={}, tiers={}, totalErrors={}, totalWarnings={}, duration={}ms",
                overallValid, results.size(),
                results.stream().mapToInt(r -> r.errors().size()).sum(),
                results.stream().mapToInt(r -> r.warnings().size()).sum(),
                totalMs);

        return new PipelineResult(results, overallValid, totalMs);
    }

    public PipelineResult validate(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            return validate(node);
        } catch (JsonProcessingException e) {
            long durationMs = 0;
            ValidationResult parseError = ValidationResult.failure("SCHEMA",
                    List.of(new ValidationResult.ValidationError(
                            "", "Invalid JSON: " + e.getOriginalMessage(), "JSON_PARSE_ERROR")),
                    durationMs);
            return new PipelineResult(List.of(parseError), false, durationMs);
        }
    }
}
