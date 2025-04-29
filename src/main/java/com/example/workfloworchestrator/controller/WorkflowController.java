package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for workflow definition operations
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public ResponseEntity<List<WorkflowDefinition>> getAllWorkflowDefinitions() {
        return ResponseEntity.ok(workflowService.getAllWorkflowDefinitions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> getWorkflowDefinition(@PathVariable Long id) {
        return workflowService.getWorkflowDefinition(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<WorkflowDefinition> getLatestWorkflowDefinition(@PathVariable String name) {
        return workflowService.getLatestWorkflowDefinition(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}/version/{version}")
    public ResponseEntity<WorkflowDefinition> getWorkflowDefinitionByNameAndVersion(
            @PathVariable String name, @PathVariable String version) {
        return workflowService.getWorkflowDefinition(name, version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WorkflowDefinition> createWorkflowDefinition(
            @Valid @RequestBody WorkflowDefinition workflowDefinition) {
        return new ResponseEntity<>(
                workflowService.createWorkflowDefinition(workflowDefinition),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> updateWorkflowDefinition(
            @PathVariable Long id, @Valid @RequestBody WorkflowDefinition workflowDefinition) {
        return ResponseEntity.ok(workflowService.updateWorkflowDefinition(id, workflowDefinition));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflowDefinition(@PathVariable Long id) {
        workflowService.deleteWorkflowDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
