package com.example.workfloworchestrator.util;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for building workflow task definitions
 * Makes it easier to create tasks that execute sub-workflows
 */
public class WorkflowTaskBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create a task that executes a sub-workflow synchronously
     *
     * @param name Task name
     * @param description Task description
     * @param workflowName Name of the sub-workflow to execute
     * @param timeoutSeconds Maximum time to wait for completion
     * @param variables Map of variables to pass to the sub-workflow
     * @param dependencies List of task IDs this task depends on
     * @return The task definition
     */
    public static TaskDefinition createSyncWorkflowTask(
            String name,
            String description,
            String workflowName,
            int timeoutSeconds,
            Map<String, Object> variables,
            List<Long> dependencies) {

        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("workflow");

        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependsOn(dependencies);
        }

        Map<String, String> config = new HashMap<>();
        config.put("workflowName", workflowName);
        config.put("waitForCompletion", "true");
        config.put("timeoutSeconds", String.valueOf(timeoutSeconds));

        if (variables != null && !variables.isEmpty()) {
            try {
                String variablesJson = objectMapper.writeValueAsString(variables);
                config.put("inputVariables", variablesJson);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error serializing variables to JSON: " + e.getMessage(), e);
            }
        }

        task.setConfiguration(config);

        return task;
    }

    /**
     * Create a task that executes a sub-workflow asynchronously
     *
     * @param name Task name
     * @param description Task description
     * @param workflowName Name of the sub-workflow to execute
     * @param variables Map of variables to pass to the sub-workflow
     * @param dependencies List of task IDs this task depends on
     * @return The task definition
     */
    public static TaskDefinition createAsyncWorkflowTask(
            String name,
            String description,
            String workflowName,
            Map<String, Object> variables,
            List<Long> dependencies) {

        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("workflow");

        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependsOn(dependencies);
        }

        Map<String, String> config = new HashMap<>();
        config.put("workflowName", workflowName);
        config.put("waitForCompletion", "false");

        if (variables != null && !variables.isEmpty()) {
            try {
                String variablesJson = objectMapper.writeValueAsString(variables);
                config.put("inputVariables", variablesJson);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error serializing variables to JSON: " + e.getMessage(), e);
            }
        }

        task.setConfiguration(config);

        return task;
    }

    /**
     * Create a task that processes the result of an asynchronously executed sub-workflow
     *
     * @param name Task name
     * @param description Task description
     * @param subWorkflowIdExpression Expression to get the sub-workflow ID
     * @param outputPrefix Prefix for variables from sub-workflow
     * @param failOnSubWorkflowFailure Whether to fail the task if sub-workflow failed
     * @param dependencies List of task IDs this task depends on
     * @return The task definition
     */
    public static TaskDefinition createWorkflowCallbackTask(
            String name,
            String description,
            String subWorkflowIdExpression,
            String outputPrefix,
            boolean failOnSubWorkflowFailure,
            List<Long> dependencies) {

        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("workflow-callback");

        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependsOn(dependencies);
        }

        Map<String, String> config = new HashMap<>();
        config.put("subWorkflowId", subWorkflowIdExpression);

        if (outputPrefix != null && !outputPrefix.isEmpty()) {
            config.put("outputPrefix", outputPrefix);
        }

        config.put("failOnSubWorkflowFailure", String.valueOf(failOnSubWorkflowFailure));

        task.setConfiguration(config);

        return task;
    }

    /**
     * Create a task that executes a dynamically determined sub-workflow
     *
     * @param name Task name
     * @param description Task description
     * @param workflowNameExpression Expression to determine workflow name
     * @param waitForCompletion Whether to wait for sub-workflow completion
     * @param timeoutSeconds Maximum time to wait for completion (if waiting)
     * @param variables Map of variables to pass to the sub-workflow
     * @param dependencies List of task IDs this task depends on
     * @return The task definition
     */
    public static TaskDefinition createDynamicWorkflowTask(
            String name,
            String description,
            String workflowNameExpression,
            boolean waitForCompletion,
            Integer timeoutSeconds,
            Map<String, Object> variables,
            List<Long> dependencies) {

        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("workflow");

        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependsOn(dependencies);
        }

        Map<String, String> config = new HashMap<>();
        config.put("workflowName", workflowNameExpression);
        config.put("waitForCompletion", String.valueOf(waitForCompletion));

        if (waitForCompletion && timeoutSeconds != null) {
            config.put("timeoutSeconds", String.valueOf(timeoutSeconds));
        }

        if (variables != null && !variables.isEmpty()) {
            try {
                String variablesJson = objectMapper.writeValueAsString(variables);
                config.put("inputVariables", variablesJson);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error serializing variables to JSON: " + e.getMessage(), e);
            }
        }

        task.setConfiguration(config);

        return task;
    }

    /**
     * Create a task that executes a sub-workflow with conditional execution
     *
     * @param name Task name
     * @param description Task description
     * @param workflowName Name of the sub-workflow to execute
     * @param conditionalExpression Expression to determine if task should execute
     * @param waitForCompletion Whether to wait for sub-workflow completion
     * @param variables Map of variables to pass to the sub-workflow
     * @param dependencies List of task IDs this task depends on
     * @return The task definition
     */
    public static TaskDefinition createConditionalWorkflowTask(
            String name,
            String description,
            String workflowName,
            String conditionalExpression,
            boolean waitForCompletion,
            Map<String, Object> variables,
            List<Long> dependencies) {

        TaskDefinition task = new TaskDefinition();
        task.setName(name);
        task.setDescription(description);
        task.setType("workflow");
        task.setConditionalExpression(conditionalExpression);

        if (dependencies != null && !dependencies.isEmpty()) {
            task.setDependsOn(dependencies);
        }

        Map<String, String> config = new HashMap<>();
        config.put("workflowName", workflowName);
        config.put("waitForCompletion", String.valueOf(waitForCompletion));

        if (variables != null && !variables.isEmpty()) {
            try {
                String variablesJson = objectMapper.writeValueAsString(variables);
                config.put("inputVariables", variablesJson);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error serializing variables to JSON: " + e.getMessage(), e);
            }
        }

        task.setConfiguration(config);

        return task;
    }

    /**
     * Convenience method to create a map of variables for a workflow task
     *
     * @param entries Key-value pairs of variables
     * @return Map of variables
     */
    public static Map<String, Object> variables(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide key-value pairs (even number of arguments)");
        }

        Map<String, Object> variables = new HashMap<>();

        for (int i = 0; i < entries.length; i += 2) {
            String key = entries[i].toString();
            Object value = entries[i + 1];
            variables.put(key, value);
        }

        return variables;
    }
}
