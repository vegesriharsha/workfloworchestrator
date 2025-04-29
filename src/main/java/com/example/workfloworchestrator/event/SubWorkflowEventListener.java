package com.example.workfloworchestrator.event;

import com.example.workfloworchestrator.engine.WorkflowEngine;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Event listener for sub-workflow events
 * Handles notifications to parent workflows when a sub-workflow completes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubWorkflowEventListener {

    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowEngine workflowEngine;

    /**
     * Handle workflow completion events
     * Notify parent workflows when a sub-workflow completes
     */
    @Async
    @EventListener
    @Transactional
    public void handleWorkflowCompletedEvent(WorkflowEvent event) {
        // Only process COMPLETED, FAILED, or CANCELLED events
        if (event.getEventType() != WorkflowEventType.COMPLETED &&
                event.getEventType() != WorkflowEventType.FAILED &&
                event.getEventType() != WorkflowEventType.CANCELLED) {
            return;
        }

        Long workflowExecutionId = event.getWorkflowExecutionId();

        try {
            WorkflowExecution execution = workflowExecutionService.getWorkflowExecution(workflowExecutionId);

            // Check if this is a sub-workflow by looking for parent correlation ID
            String parentCorrelationId = execution.getVariables().get("parentCorrelationId");
            String parentTaskId = execution.getVariables().get("parentTaskId");

            if (parentCorrelationId != null && !parentCorrelationId.isEmpty()) {
                log.info("Sub-workflow {} completed with status {}. Notifying parent workflow.",
                        workflowExecutionId, execution.getStatus());

                // Find parent workflow by correlation ID
                try {
                    Optional<WorkflowExecution> parentExecution =
                            workflowExecutionService.findWorkflowExecutionByCorrelationId(parentCorrelationId);

                    if (parentExecution.isPresent()) {
                        handleParentWorkflowCallback(parentExecution.get(), execution, parentTaskId);
                    } else {
                        log.warn("Parent workflow with correlation ID {} not found", parentCorrelationId);
                    }
                } catch (Exception e) {
                    log.error("Error finding parent workflow with correlation ID {}", parentCorrelationId, e);
                }
            }
        } catch (Exception e) {
            log.error("Error processing workflow completion event for workflow {}", workflowExecutionId, e);
        }
    }

    /**
     * Handle callback to parent workflow when sub-workflow completes
     */
    private void handleParentWorkflowCallback(WorkflowExecution parentExecution,
                                              WorkflowExecution subWorkflowExecution,
                                              String parentTaskId) {

        // Check if parent workflow is in a state to handle callbacks
        if (parentExecution.getStatus() != WorkflowStatus.RUNNING &&
                parentExecution.getStatus() != WorkflowStatus.AWAITING_CALLBACK) {
            log.info("Parent workflow {} is not in a state to handle callbacks (status: {})",
                    parentExecution.getId(), parentExecution.getStatus());
            return;
        }

        try {
            // If parent is AWAITING_CALLBACK, resume execution
            if (parentExecution.getStatus() == WorkflowStatus.AWAITING_CALLBACK) {
                // Update status to RUNNING
                workflowExecutionService.updateWorkflowExecutionStatus(
                        parentExecution.getId(), WorkflowStatus.RUNNING);

                // Add sub-workflow data to parent variables
                Map<String, String> parentVariables = parentExecution.getVariables();
                parentVariables.put("subWorkflowId", subWorkflowExecution.getId().toString());
                parentVariables.put("subWorkflowStatus", subWorkflowExecution.getStatus().toString());
                parentVariables.put("subWorkflowCompleted", "true");

                // Save updated variables
                workflowExecutionService.save(parentExecution);

                // Resume parent workflow execution
                workflowEngine.executeWorkflow(parentExecution.getId());
            }
        } catch (Exception e) {
            log.error("Error handling parent workflow callback for workflow {}",
                    parentExecution.getId(), e);
        }
    }
}
