package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.service.WalkthroughService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dev/walkthrough")
@Profile("dev")
@Tag(name = "Walkthrough", description = "Step-by-step mortgage lifecycle walkthrough (dev only)")
public class WalkthroughController {

    private final WalkthroughService walkthroughService;

    public WalkthroughController(WalkthroughService walkthroughService) {
        this.walkthroughService = walkthroughService;
    }

    @PostMapping("/start")
    @Operation(summary = "Start a new walkthrough session")
    public ResponseEntity<Map<String, Object>> start() {
        return ResponseEntity.status(HttpStatus.CREATED).body(walkthroughService.startWalkthrough());
    }

    @PostMapping("/{sessionId}/next")
    @Operation(summary = "Execute the next lifecycle step")
    public ResponseEntity<Map<String, Object>> next(@PathVariable String sessionId) {
        return ResponseEntity.ok(walkthroughService.executeNextStep(sessionId));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get current walkthrough state")
    public ResponseEntity<Map<String, Object>> getState(@PathVariable String sessionId) {
        return ResponseEntity.ok(walkthroughService.getState(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete a walkthrough session")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        walkthroughService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
