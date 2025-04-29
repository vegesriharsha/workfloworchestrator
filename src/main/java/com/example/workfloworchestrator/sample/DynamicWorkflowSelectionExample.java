package com.example.workfloworchestrator.sample;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.model.WorkflowExecution;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Example demonstrating dynamic sub-workflow selection
 */
@Slf4j
@RestController
@RequestMapping("/api/examples/dynamic-workflows")
@RequiredArgsConstructor
public class DynamicWorkflowSelectionExample {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;

    /**
     * Create sample workflows for dynamic selection
     *
     * @return Map of created workflow definitions
     */
    @PostMapping("/setup")
    public Map<String, WorkflowDefinition> setupDynamicWorkflows() {
        Map<String, WorkflowDefinition> workflows = new HashMap<>();

        // Create various target sub-workflows
        WorkflowDefinition highPriorityWorkflow = createTargetWorkflow("high-priority", "High priority workflow");
        WorkflowDefinition mediumPriorityWorkflow = createTargetWorkflow("medium-priority", "Medium priority workflow");
        WorkflowDefinition lowPriorityWorkflow = createTargetWorkflow("low-priority", "Low priority workflow");

        // Create parent workflow with dynamic selection
        WorkflowDefinition routerWorkflow = createRouterWorkflow();

        // Save workflows to database
        workflows.put("high-priority", workflowService.createWorkflowDefinition(highPriorityWorkflow));
        workflows.put("medium-priority", workflowService.createWorkflowDefinition(mediumPriorityWorkflow));
        workflows.put("low-priority", workflowService.createWorkflowDefinition(lowPriorityWorkflow));
        workflows.put("router-workflow", workflowService.createWorkflowDefinition(routerWorkflow));

        return workflows;
    }

    /**
     * Execute the router workflow
     *
     * @param requestId The request ID
     * @param priority The priority level (high, medium, low)
     * @return The created workflow execution
     */
    @PostMapping("/execute")
    public WorkflowExecution executeRouterWorkflow(
            @RequestParam String requestId,
            @RequestParam String priority) {

        // Set input variables
        Map<String, String> variables = new HashMap<>();
        variables.put("requestId", requestId);
        variables.put("priority", priority);
        variables.put("timestamp", LocalDateTime.now().toString());

        // Start router workflow
        return workflowExecutionService.startWorkflow("router-workflow", null, variables);
    }

    /**
     * Get workflow execution status
     *
     * @param executionId The workflow execution ID
     * @return The workflow execution
     */
    @GetMapping("/{executionId}")
    public WorkflowExecution getWorkflowExecution(@PathVariable Long executionId) {
        return workflowExecutionService.getWorkflowExecution(executionId);
    }

    /**
     * Create a target workflow (high, medium, or low priority)
     */
    private WorkflowDefinition createTargetWorkflow(String name, String description) {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName(name);
        workflow.setDescription(description);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Log execution
        TaskDefinition logTask = new TaskDefinition();
        logTask.setName("log-execution");
        logTask.setDescription("Log workflow execution");
        logTask.setType("http");
        Map<String, String> logConfig = new HashMap<>();
        logConfig.put("url", "https://api.example.com/log");
        logConfig.put("method", "POST");
        logConfig.put("requestBody", "{\"workflow\": \"" + name + "\", \"requestId\": \"${requestId}\", \"timestamp\": \"${timestamp}\"}");
        logTask.setConfiguration(logConfig);

        // Process request
        TaskDefinition processTask = new TaskDefinition();
        processTask.setName("process-request");
        processTask.setDescription("Process request with " + name + " handling");
        processTask.setType("http");
        processTask.setDependsOn(List.of(logTask.getId()));
        Map<String, String> processConfig = new HashMap<>();
        processConfig.put("url", "https://api.example.com/process/" + name);
        processConfig.put("method", "POST");
        processConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"priority\": \"" + name + "\"}");
        processTask.setConfiguration(processConfig);

        // Send response
        TaskDefinition responseTask = new TaskDefinition();
        responseTask.setName("send-response");
        responseTask.setDescription("Send response after " + name + " processing");
        responseTask.setType("http");
        responseTask.setDependsOn(List.of(processTask.getId()));
        Map<String, String> responseConfig = new HashMap<>();
        responseConfig.put("url", "https://api.example.com/respond");
        responseConfig.put("method", "POST");
        responseConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"result\": ${process-request.output.result}}");
        responseTask.setConfiguration(responseConfig);

        tasks.add(logTask);
        tasks.add(processTask);
        tasks.add(responseTask);
        workflow.setTasks(tasks);

        return workflow;
    }

    /**
     * Create router workflow that dynamically selects a sub-workflow
     */
    private WorkflowDefinition createRouterWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("router-workflow");
        workflow.setDescription("Router workflow that dynamically selects a sub-workflow");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Validate request
        TaskDefinition validateTask = new TaskDefinition();
        validateTask.setName("validate-request");
        validateTask.setDescription("Validate incoming request");
        validateTask.setType("http");
        Map<String, String> validateConfig = new HashMap<>();
        validateConfig.put("url", "https://api.example.com/validate");
        validateConfig.put("method", "POST");
        validateConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"priority\": \"${priority}\"}");
        validateTask.setConfiguration(validateConfig);

        // Determine target workflow
        TaskDefinition determineTask = new TaskDefinition();
        determineTask.setName("determine-target-workflow");
        determineTask.setDescription("Determine which sub-workflow to execute");
        determineTask.setType("http");
        determineTask.setDependsOn(List.of(validateTask.getId()));
        Map<String, String> determineConfig = new HashMap<>();
        determineConfig.put("url", "https://api.example.com/determine-workflow");
        determineConfig.put("method", "POST");
        determineConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"priority\": \"${priority}\"}");
        determineTask.setConfiguration(determineConfig);

        // Execute high priority workflow
        TaskDefinition highPriorityTask = new TaskDefinition();
        highPriorityTask.setName("execute-high-priority");
        highPriorityTask.setDescription("Execute high priority workflow");
        highPriorityTask.setType("workflow");
        highPriorityTask.setDependsOn(List.of(determineTask.getId()));
        highPriorityTask.setConditionalExpression("${determine-target-workflow.output.targetWorkflow == 'high-priority'}");
        Map<String, String> highConfig = new HashMap<>();
        highConfig.put("workflowName", "high-priority");
        highConfig.put("waitForCompletion", "true");
        highConfig.put("inputVariables", "{\"requestId\": \"${requestId}\", \"timestamp\": \"${timestamp}\"}");
        highPriorityTask.setConfiguration(highConfig);

        // Execute medium priority workflow
        TaskDefinition mediumPriorityTask = new TaskDefinition();
        mediumPriorityTask.setName("execute-medium-priority");
        mediumPriorityTask.setDescription("Execute medium priority workflow");
        mediumPriorityTask.setType("workflow");
        mediumPriorityTask.setDependsOn(List.of(determineTask.getId()));
        mediumPriorityTask.setConditionalExpression("${determine-target-workflow.output.targetWorkflow == 'medium-priority'}");
        Map<String, String> mediumConfig = new HashMap<>();
        mediumConfig.put("workflowName", "medium-priority");
        mediumConfig.put("waitForCompletion", "true");
        mediumConfig.put("inputVariables", "{\"requestId\": \"${requestId}\", \"timestamp\": \"${timestamp}\"}");
        mediumPriorityTask.setConfiguration(mediumConfig);

        // Execute low priority workflow
        TaskDefinition lowPriorityTask = new TaskDefinition();
        lowPriorityTask.setName("execute-low-priority");
        lowPriorityTask.setDescription("Execute low priority workflow");
        lowPriorityTask.setType("workflow");
        lowPriorityTask.setDependsOn(List.of(determineTask.getId()));
        lowPriorityTask.setConditionalExpression("${determine-target-workflow.output.targetWorkflow == 'low-priority'}");
        Map<String, String> lowConfig = new HashMap<>();
        lowConfig.put("workflowName", "low-priority");
        lowConfig.put("waitForCompletion", "true");
        lowConfig.put("inputVariables", "{\"requestId\": \"${requestId}\", \"timestamp\": \"${timestamp}\"}");
        lowPriorityTask.setConfiguration(lowConfig);

        // Fallback task for unknown priority
        TaskDefinition fallbackTask = new TaskDefinition();
        fallbackTask.setName("handle-unknown-priority");
        fallbackTask.setDescription("Handle unknown priority level");
        fallbackTask.setType("http");
        fallbackTask.setDependsOn(List.of(determineTask.getId()));
        fallbackTask.setConditionalExpression("${determine-target-workflow.output.targetWorkflow == 'unknown'}");
        Map<String, String> fallbackConfig = new HashMap<>();
        fallbackConfig.put("url", "https://api.example.com/fallback");
        fallbackConfig.put("method", "POST");
        fallbackConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"error\": \"Unknown priority level\"}");
        fallbackTask.setConfiguration(fallbackConfig);

        // Final notification
        TaskDefinition notifyTask = new TaskDefinition();
        notifyTask.setName("send-completion-notification");
        notifyTask.setDescription("Send completion notification");
        notifyTask.setType("http");
        notifyTask.setDependsOn(List.of(
                highPriorityTask.getId(),
                mediumPriorityTask.getId(),
                lowPriorityTask.getId(),
                fallbackTask.getId()));
        Map<String, String> notifyConfig = new HashMap<>();
        notifyConfig.put("url", "https://api.example.com/notify");
        notifyConfig.put("method", "POST");
        notifyConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"status\": \"completed\"}");
        notifyTask.setConfiguration(notifyConfig);

        tasks.add(validateTask);
        tasks.add(determineTask);
        tasks.add(highPriorityTask);
        tasks.add(mediumPriorityTask);
        tasks.add(lowPriorityTask);
        tasks.add(fallbackTask);
        tasks.add(notifyTask);
        workflow.setTasks(tasks);

        return workflow;
    }

    /**
     * Alternative approach: Create fully dynamic workflow
     * This demonstrates using a variable for the workflowName
     */
    private WorkflowDefinition createFullyDynamicRouterWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("fully-dynamic-router");
        workflow.setDescription("Fully dynamic router that selects workflow at runtime");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Determine target workflow
        TaskDefinition determineTask = new TaskDefinition();
        determineTask.setName("determine-target-workflow");
        determineTask.setDescription("Determine which sub-workflow to execute");
        determineTask.setType("http");
        Map<String, String> determineConfig = new HashMap<>();
        determineConfig.put("url", "https://api.example.com/determine-workflow");
        determineConfig.put("method", "POST");
        determineConfig.put("requestBody", "{\"requestId\": \"${requestId}\", \"priority\": \"${priority}\"}");
        determineTask.setConfiguration(determineConfig);

        // Execute dynamic workflow
        TaskDefinition dynamicTask = new TaskDefinition();
        dynamicTask.setName("execute-dynamic-workflow");
        dynamicTask.setDescription("Execute dynamically determined workflow");
        dynamicTask.setType("workflow");
        dynamicTask.setDependsOn(List.of(determineTask.getId()));
        Map<String, String> dynamicConfig = new HashMap<>();
        // Use variable for workflow name - will be determined at runtime
        dynamicConfig.put("workflowName", "${determine-target-workflow.output.targetWorkflow}");
        dynamicConfig.put("waitForCompletion", "true");
        dynamicConfig.put("inputVariables", "{\"requestId\": \"${requestId}\", \"timestamp\": \"${timestamp}\"}");
        dynamicTask.setConfiguration(dynamicConfig);

        tasks.add(determineTask);
        tasks.add(dynamicTask);
        workflow.setTasks(tasks);

        return workflow;
    }
}
