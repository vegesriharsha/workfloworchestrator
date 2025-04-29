package com.example.workfloworchestrator.controller;

import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for workflow execution operations
 */
@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private final WorkflowExecutionService workflowExecutionService;

    @PostMapping("/start")
    public ResponseEntity<WorkflowExecution> startWorkflow(
            @RequestParam String workflowName,
            @RequestParam(required = false) String version,
            @RequestBody(required = false) Map<String, String> variables) {

        WorkflowExecution execution = workflowExecutionService.startWorkflow(
                workflowName, version, variables != null ? variables : Map.of());

        return new ResponseEntity<>(execution, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowExecution> getWorkflowExecution(@PathVariable Long id) {
        return ResponseEntity.ok(workflowExecutionService.getWorkflowExecution(id));
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<WorkflowExecution> getWorkflowExecutionByCorrelationId(
            @PathVariable String correlationId) {
        return ResponseEntity.ok(
                workflowExecutionService.getWorkflowExecutionByCorrelationId(correlationId));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowExecution>> getWorkflowExecutionsByStatus(
            @RequestParam(required = false) WorkflowStatus status) {

        if (status != null) {
            return ResponseEntity.ok(workflowExecutionService.getWorkflowExecutionsByStatus(status));
        } else {
            return ResponseEntity.ok(workflowExecutionService.getAllWorkflowExecutions());
        }
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<WorkflowExecution> pauseWorkflowExecution(@PathVariable Long id) {
        return ResponseEntity.ok(workflowExecutionService.pauseWorkflowExecution(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<WorkflowExecution> resumeWorkflowExecution(@PathVariable Long id) {
        return ResponseEntity.ok(workflowExecutionService.resumeWorkflowExecution(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkflowExecution> cancelWorkflowExecution(@PathVariable Long id) {
        return ResponseEntity.ok(workflowExecutionService.cancelWorkflowExecution(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<WorkflowExecution> retryWorkflowExecution(@PathVariable Long id) {
        return ResponseEntity.ok(workflowExecutionService.retryWorkflowExecution(id));
    }

    @PostMapping("/{id}/retry-subset")
    public ResponseEntity<WorkflowExecution> retryWorkflowExecutionSubset(
            @PathVariable Long id, @RequestBody List<Long> taskIds) {
        return ResponseEntity.ok(workflowExecutionService.retryWorkflowExecutionSubset(id, taskIds));
    }
}
