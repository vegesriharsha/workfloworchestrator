package com.example.workfloworchestrator.engine.executor;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.ExecutionContext;
import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.model.WorkflowStatus;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Task executor for launching sub-workflows
 * Allows a task to execute another workflow as part of its execution
 */
@Slf4j
@Component
public class WorkflowTaskExecutor extends AbstractTaskExecutor {

    private static final String TASK_TYPE = "workflow";

    private final WorkflowExecutionService workflowExecutionService;

    public WorkflowTaskExecutor(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    protected void validateTaskConfig(TaskDefinition taskDefinition) {
        validateTaskConfig(taskDefinition, "workflowName");
    }

    @Override
    protected Map<String, Object> doExecute(TaskDefinition taskDefinition, ExecutionContext context) throws Exception {
        Map<String, String> config = processConfigVariables(taskDefinition.getConfiguration(), context);

        // Extract required configuration
        String workflowName = getRequiredConfig(config, "workflowName");

        // Optional configuration
        String workflowVersion = config.get("workflowVersion");
        boolean waitForCompletion = Boolean.parseBoolean(config.getOrDefault("waitForCompletion", "true"));
        int timeoutSeconds = Integer.parseInt(config.getOrDefault("timeoutSeconds", "3600")); // 1 hour default
        String parentCorrelationId = config.get("parentCorrelationId");

        // Prepare input variables for sub-workflow
        Map<String, String> variables = prepareSubWorkflowVariables(config, context);

        // Generate a correlation ID that links to the parent workflow
        String correlationId = generateCorrelationId(parentCorrelationId);
        variables.put("parentCorrelationId", parentCorrelationId != null ? parentCorrelationId : correlationId);
        variables.put("parentTaskId", String.valueOf(taskDefinition.getId()));

        // Start the sub-workflow
        WorkflowExecution subWorkflowExecution =
                workflowExecutionService.startWorkflow(workflowName, workflowVersion, variables);

        // Prepare the result map
        Map<String, Object> result = new HashMap<>();
        result.put("subWorkflowId", subWorkflowExecution.getId());
        result.put("correlationId", subWorkflowExecution.getCorrelationId());

        if (waitForCompletion) {
            // Wait for the sub-workflow to complete
            try {
                CompletableFuture<WorkflowExecution> future =
                        workflowExecutionService.waitForWorkflowCompletion(subWorkflowExecution.getId(), timeoutSeconds);

                WorkflowExecution completedExecution = future.get(timeoutSeconds, TimeUnit.SECONDS);

                // Add sub-workflow results to the result map
                result.put("subWorkflowStatus", completedExecution.getStatus());
                result.put("subWorkflowVariables", completedExecution.getVariables());
                result.put("success", completedExecution.getStatus() == WorkflowStatus.COMPLETED);

                if (completedExecution.getStatus() != WorkflowStatus.COMPLETED) {
                    result.put("error", "Sub-workflow failed: " + completedExecution.getErrorMessage());
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                result.put("success", false);
                result.put("error", "Error waiting for sub-workflow: " + e.getMessage());
                throw new TaskExecutionException("Error waiting for sub-workflow: " + e.getMessage(), e);
            }
        } else {
            // Not waiting, just return sub-workflow info
            result.put("success", true);
            result.put("subWorkflowStatus", subWorkflowExecution.getStatus());
        }

        return result;
    }

    /**
     * Prepare variables to be passed to the sub-workflow
     */
    private Map<String, String> prepareSubWorkflowVariables(Map<String, String> config, ExecutionContext context) {
        Map<String, String> variables = new HashMap<>();

        // Check if there's a variablePrefix in the config
        String variablePrefix = config.getOrDefault("variablePrefix", "");

        // Add main workflow variables to sub-workflow with prefix
        for (Map.Entry<String, Object> entry : context.getAllVariables().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value != null) {
                variables.put(variablePrefix + key, value.toString());
            }
        }

        // If there's a specific variables map in the config, add/override those
        String inputVariablesStr = config.get("inputVariables");
        if (inputVariablesStr != null && !inputVariablesStr.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, String> inputVariables = mapper.readValue(inputVariablesStr,
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));

                // Process variables with context
                for (Map.Entry<String, String> entry : inputVariables.entrySet()) {
                    String value = processVariables(entry.getValue(), context);
                    variables.put(entry.getKey(), value);
                }
            } catch (Exception e) {
                log.warn("Failed to parse inputVariables JSON: {}", e.getMessage());
            }
        }

        return variables;
    }

    /**
     * Generate a correlation ID that links to the parent workflow
     */
    private String generateCorrelationId(String parentCorrelationId) {
        String uuid = UUID.randomUUID().toString();
        if (parentCorrelationId != null && !parentCorrelationId.isEmpty()) {
            return parentCorrelationId + "." + uuid;
        } else {
            return uuid;
        }
    }

    @Override
    protected Map<String, Object> postProcessResult(Map<String, Object> result, ExecutionContext context) {
        // Add execution timestamp
        result.put("executionTimestamp", System.currentTimeMillis());

        // For sub-workflow variables, add them to context with prefix if waitForCompletion=true
        if (result.containsKey("subWorkflowVariables") && result.get("subWorkflowVariables") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> subWorkflowVariables = (Map<String, String>) result.get("subWorkflowVariables");

            // Use outputPrefix if specified, otherwise use "subWorkflow."
            String outputPrefix = context.getVariable("outputPrefix", String.class);
            if (outputPrefix == null) {
                outputPrefix = "subWorkflow.";
            }

            // Add each variable to context with prefix
            for (Map.Entry<String, String> entry : subWorkflowVariables.entrySet()) {
                context.setVariable(outputPrefix + entry.getKey(), entry.getValue());
            }
        }

        return result;
    }
}
