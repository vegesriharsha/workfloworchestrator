package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Task executor for handling callbacks from sub-workflows
 * Processes results from a completed sub-workflow
 */
@Slf4j
@Component
public class WorkflowCallbackTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "workflow-callback";

    private final WorkflowExecutionService workflowExecutionService;

    public WorkflowCallbackTaskExecutor(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "subWorkflowId");
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);

        // Extract required configuration
        String subWorkflowIdStr = getRequiredConfig(config, "subWorkflowId");
        Long subWorkflowId = Long.parseLong(subWorkflowIdStr);

        // Optional configuration
        String outputPrefix = config.getOrDefault("outputPrefix", "subWorkflow.");
        boolean failOnSubWorkflowFailure = Boolean.parseBoolean(
                config.getOrDefault("failOnSubWorkflowFailure", "true"));

        // Get the sub-workflow execution
        WorkflowExecution subWorkflowExecution = workflowExecutionService.getWorkflowExecution(subWorkflowId);

        // Prepare the result map
        Map<String, Object> result = new HashMap<>();
        result.put("subWorkflowId", subWorkflowId);
        result.put("subWorkflowStatus", subWorkflowExecution.getStatus());
        result.put("subWorkflowVariables", subWorkflowExecution.getVariables());

        // Determine success based on sub-workflow status
        boolean success = subWorkflowExecution.getStatus() == WorkflowStatus.COMPLETED;

        // If configured to fail on sub-workflow failure and sub-workflow failed, mark as failure
        if (failOnSubWorkflowFailure && !success) {
            result.put("success", false);
            result.put("error", "Sub-workflow failed: " + subWorkflowExecution.getErrorMessage());
        } else {
            result.put("success", true);
        }

        // Add sub-workflow variables to context with prefix
        for (Map.Entry<String, String> entry : subWorkflowExecution.getVariables().entrySet()) {
            context.setVariable(outputPrefix + entry.getKey(), entry.getValue());
        }

        return result;
    }

    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution timestamp
        result.put("executionTimestamp", System.currentTimeMillis());

        // Log execution summary
        Long subWorkflowId = (Long) result.get("subWorkflowId");
        Object subWorkflowStatus = result.get("subWorkflowStatus");
        Boolean success = (Boolean) result.get("success");

        if (success) {
            log.info("Sub-workflow callback processed successfully. Sub-workflow ID: {}, Status: {}",
                    subWorkflowId, subWorkflowStatus);
        } else {
            log.warn("Sub-workflow callback processed with issues. Sub-workflow ID: {}, Status: {}, Error: {}",
                    subWorkflowId, subWorkflowStatus, result.get("error"));
        }

        return result;
    }
}
