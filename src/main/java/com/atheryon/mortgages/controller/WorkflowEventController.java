package com.atheryon.mortgages.controller;

import com.atheryon.mortgages.domain.entity.WorkflowEvent;
import com.atheryon.mortgages.repository.WorkflowEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dev/events")
@Profile("dev")
@Tag(name = "Workflow Events", description = "Application audit trail (dev only)")
public class WorkflowEventController {

    private final WorkflowEventRepository eventRepository;

    public WorkflowEventController(WorkflowEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get all workflow events for an application")
    public ResponseEntity<List<Map<String, Object>>> getEvents(@PathVariable UUID applicationId) {
        List<WorkflowEvent> events = eventRepository.findByApplicationIdOrderByOccurredAtAsc(applicationId);
        List<Map<String, Object>> result = events.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("eventType", e.getEventType());
            m.put("occurredAt", e.getOccurredAt());
            m.put("actorType", e.getActorType());
            m.put("actorId", e.getActorId());
            m.put("actorName", e.getActorName());
            m.put("previousState", e.getPreviousState());
            m.put("newState", e.getNewState());
            m.put("payload", e.getPayload());
            m.put("correlationId", e.getCorrelationId());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
