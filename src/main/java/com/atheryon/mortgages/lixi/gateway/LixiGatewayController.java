package com.atheryon.mortgages.lixi.gateway;

import com.atheryon.mortgages.lixi.message.LixiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lixi")
@Tag(name = "LIXI Gateway", description = "LIXI2 CAL message ingestion and validation")
public class LixiGatewayController {

    private final LixiGatewayService gatewayService;

    public LixiGatewayController(LixiGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ingest a LIXI2 CAL message", description = "Validates, maps, and persists a LIXI2 CAL JSON message")
    public ResponseEntity<IngestResponse> ingest(@RequestBody String rawJson) {
        IngestResponse response = gatewayService.ingest(rawJson);
        if (!response.valid()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate a LIXI2 CAL message without persisting")
    public ResponseEntity<ValidationResponse> validate(@RequestBody String rawJson) {
        ValidationResponse response = gatewayService.validateOnly(rawJson);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/messages/{applicationId}")
    @Operation(summary = "Get stored LIXI2 messages for an application")
    public ResponseEntity<List<LixiMessage>> getMessages(@PathVariable UUID applicationId) {
        List<LixiMessage> messages = gatewayService.getMessages(applicationId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/samples")
    @Operation(summary = "List available sample LIXI2 messages")
    public ResponseEntity<List<String>> listSamples() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/lixi/samples/*.json");
        List<String> names = Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(name -> name != null)
                .map(name -> name.replace(".json", ""))
                .sorted()
                .toList();
        return ResponseEntity.ok(names);
    }

    @GetMapping("/samples/{name}")
    @Operation(summary = "Get a specific sample LIXI2 message by name")
    public ResponseEntity<String> getSample(@PathVariable String name) throws IOException {
        ClassPathResource resource = new ClassPathResource("lixi/samples/" + name + ".json");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(content);
    }
}
