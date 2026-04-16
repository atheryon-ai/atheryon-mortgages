package com.atheryon.mortgages.lixi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Service
public class LixiSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(LixiSchemaValidator.class);
    private static final String SCHEMA_PATH = "/lixi/schema/cal-2.6.91.json";
    private static final String TIER = "SCHEMA";

    private final JsonSchema schema;
    private final boolean permissive;

    public LixiSchemaValidator() {
        InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH);
        if (schemaStream == null) {
            log.warn("LIXI schema not found at {}. Running in permissive mode — all messages pass schema validation.", SCHEMA_PATH);
            this.schema = null;
            this.permissive = true;
        } else {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
            this.schema = factory.getSchema(schemaStream, config);
            this.permissive = false;
            log.info("LIXI schema loaded from {}", SCHEMA_PATH);
        }
    }

    public ValidationResult validate(JsonNode lixiMessage) {
        long start = System.nanoTime();

        if (permissive) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return ValidationResult.withWarnings(TIER,
                    List.of(new ValidationResult.ValidationWarning(
                            "", "Schema file not loaded — permissive mode active", "SCHEMA_MISSING")),
                    durationMs);
        }

        Set<ValidationMessage> messages = schema.validate(lixiMessage);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (messages.isEmpty()) {
            return ValidationResult.success(TIER, durationMs);
        }

        List<ValidationResult.ValidationError> errors = messages.stream()
                .map(msg -> new ValidationResult.ValidationError(
                        msg.getInstanceLocation().toString(),
                        msg.getMessage(),
                        msg.getType()))
                .toList();

        return ValidationResult.failure(TIER, errors, durationMs);
    }
}
