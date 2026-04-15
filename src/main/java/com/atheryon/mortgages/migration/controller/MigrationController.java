package com.atheryon.mortgages.migration.controller;

import com.atheryon.mortgages.migration.entity.MigrationFieldMapping;
import com.atheryon.mortgages.migration.entity.MigrationJob;
import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import com.atheryon.mortgages.migration.pipeline.MigrationPipelineService;
import com.atheryon.mortgages.migration.repository.MigrationFieldMappingRepository;
import com.atheryon.mortgages.migration.repository.MigrationJobRepository;
import com.atheryon.mortgages.migration.repository.MigrationLoanStagingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/migration")
@Tag(name = "Migration", description = "Legacy loan portfolio migration pipeline")
public class MigrationController {

    private final MigrationPipelineService pipelineService;
    private final MigrationJobRepository jobRepository;
    private final MigrationLoanStagingRepository stagingRepository;
    private final MigrationFieldMappingRepository fieldMappingRepository;

    public MigrationController(MigrationPipelineService pipelineService,
                                MigrationJobRepository jobRepository,
                                MigrationLoanStagingRepository stagingRepository,
                                MigrationFieldMappingRepository fieldMappingRepository) {
        this.pipelineService = pipelineService;
        this.jobRepository = jobRepository;
        this.stagingRepository = stagingRepository;
        this.fieldMappingRepository = fieldMappingRepository;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload CSV file and start migration job")
    public ResponseEntity<MigrationJob> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String jobName) throws IOException {

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().build();
        }

        String name = (jobName != null && !jobName.isBlank()) ? jobName : "Migration: " + filename;
        MigrationJob job = pipelineService.startMigration(name, filename, file.getInputStream());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/jobs")
    @Operation(summary = "List all migration jobs")
    public ResponseEntity<List<MigrationJob>> listJobs() {
        return ResponseEntity.ok(jobRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get migration job status and summary")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable UUID jobId) {
        MigrationJob job = pipelineService.getJobStatus(jobId);

        long clean = stagingRepository.countByJobIdAndClassification(jobId, "CLEAN");
        long warning = stagingRepository.countByJobIdAndClassification(jobId, "WARNING");
        long failed = stagingRepository.countByJobIdAndClassification(jobId, "FAILED");
        long promoted = stagingRepository.countByJobIdAndPromoted(jobId, true);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("job", job);
        response.put("summary", Map.of(
                "total", job.getSourceRowCount(),
                "clean", clean,
                "warning", warning,
                "failed", failed,
                "promoted", promoted
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/{jobId}/records")
    @Operation(summary = "Get staged records for a migration job (paginated)")
    public ResponseEntity<Page<MigrationLoanStaging>> getRecords(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String classification) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("rowIndex"));

        Page<MigrationLoanStaging> records;
        if (classification != null && !classification.isBlank()) {
            // Filter by classification requires a custom query — fall back to full list
            records = stagingRepository.findByJobId(jobId, pageRequest);
        } else {
            records = stagingRepository.findByJobId(jobId, pageRequest);
        }

        return ResponseEntity.ok(records);
    }

    @GetMapping("/jobs/{jobId}/mappings")
    @Operation(summary = "Get field mappings for a migration job")
    public ResponseEntity<List<MigrationFieldMapping>> getMappings(@PathVariable UUID jobId) {
        return ResponseEntity.ok(fieldMappingRepository.findByJobId(jobId));
    }

    @PutMapping("/jobs/{jobId}/mappings/{mappingId}")
    @Operation(summary = "Confirm or override a field mapping")
    public ResponseEntity<MigrationFieldMapping> updateMapping(
            @PathVariable UUID jobId,
            @PathVariable UUID mappingId,
            @RequestBody Map<String, String> body) {

        MigrationFieldMapping mapping = fieldMappingRepository.findById(mappingId)
                .orElseThrow(() -> new NoSuchElementException("Mapping not found: " + mappingId));

        if (!mapping.getJobId().equals(jobId)) {
            return ResponseEntity.badRequest().build();
        }

        String action = body.get("action"); // "confirm", "reject", or "override"
        if ("confirm".equals(action)) {
            mapping.setStatus("CONFIRMED");
            mapping.setConfidence(BigDecimal.ONE);
            mapping.setConfirmedBy(body.getOrDefault("user", "api"));
        } else if ("reject".equals(action)) {
            mapping.setStatus("REJECTED");
            mapping.setConfirmedBy(body.getOrDefault("user", "api"));
        } else if ("override".equals(action)) {
            mapping.setTargetPath(body.get("targetPath"));
            mapping.setStatus("MANUAL");
            mapping.setConfidence(BigDecimal.ONE);
            mapping.setConfirmedBy(body.getOrDefault("user", "api"));
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(fieldMappingRepository.save(mapping));
    }

    @PostMapping("/jobs/{jobId}/import")
    @Operation(summary = "Trigger import of validated records into the main system")
    public ResponseEntity<Map<String, Object>> triggerImport(@PathVariable UUID jobId) {
        MigrationJob job = pipelineService.getJobStatus(jobId);

        if (!"CLASSIFIED".equals(job.getStatus()) && !"PROMOTED".equals(job.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Job must be in CLASSIFIED or PROMOTED status to import, current: " + job.getStatus()
            ));
        }

        // Mark as promoted (actual import logic will be added in a later phase)
        long clean = stagingRepository.countByJobIdAndClassification(jobId, "CLEAN");
        job.setStatus("PROMOTED");
        jobRepository.save(job);

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "PROMOTED",
                "recordsAvailableForImport", clean,
                "message", "Import queued. Clean records will be promoted to the main system."
        ));
    }
}
