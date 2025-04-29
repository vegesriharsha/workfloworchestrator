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
 * Sample controller showing how to use sub-workflows
 */
@Slf4j
@RestController
@RequestMapping("/api/examples/sub-workflows")
@RequiredArgsConstructor
public class SubWorkflowExampleController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;

    /**
     * Create sample sub-workflows
     *
     * @return Map of created workflow definitions
     */
    @PostMapping("/setup")
    public Map<String, WorkflowDefinition> setupSubWorkflows() {
        Map<String, WorkflowDefinition> workflows = new HashMap<>();

        // Create sub-workflows
        WorkflowDefinition dataExtractWorkflow = createDataExtractWorkflow();
        WorkflowDefinition dataProcessWorkflow = createDataProcessWorkflow();
        WorkflowDefinition dataLoadWorkflow = createDataLoadWorkflow();

        // Create parent workflow
        WorkflowDefinition parentWorkflow = createParentWorkflow();

        // Save sub-workflows to database
        workflows.put("data-extract", workflowService.createWorkflowDefinition(dataExtractWorkflow));
        workflows.put("data-process", workflowService.createWorkflowDefinition(dataProcessWorkflow));
        workflows.put("data-load", workflowService.createWorkflowDefinition(dataLoadWorkflow));
        workflows.put("parent-workflow", workflowService.createWorkflowDefinition(parentWorkflow));

        return workflows;
    }

    /**
     * Execute the parent workflow that uses sub-workflows
     *
     * @param sourceId The data source ID to use
     * @return The created workflow execution
     */
    @PostMapping("/execute")
    public WorkflowExecution executeParentWorkflow(@RequestParam String sourceId) {
        // Set input variables
        Map<String, String> variables = new HashMap<>();
        variables.put("sourceId", sourceId);
        variables.put("executionId", UUID.randomUUID().toString());
        variables.put("startTime", LocalDateTime.now().toString());

        // Start parent workflow
        return workflowExecutionService.startWorkflow("parent-workflow", null, variables);
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
     * Create data extract sub-workflow
     */
    private WorkflowDefinition createDataExtractWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("data-extract");
        workflow.setDescription("Extract data from source system");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Connect to source
        TaskDefinition connectTask = new TaskDefinition();
        connectTask.setName("connect-to-source");
        connectTask.setDescription("Connect to source system");
        connectTask.setType("http");
        Map<String, String> connectConfig = new HashMap<>();
        connectConfig.put("url", "https://api.example.com/connect");
        connectConfig.put("method", "POST");
        connectConfig.put("requestBody", "{\"sourceId\": \"${sourceId}\"}");
        connectTask.setConfiguration(connectConfig);

        // Extract data
        TaskDefinition extractTask = new TaskDefinition();
        extractTask.setName("extract-data");
        extractTask.setDescription("Extract data from source");
        extractTask.setType("http");
        extractTask.setDependsOn(List.of(connectTask.getId()));
        Map<String, String> extractConfig = new HashMap<>();
        extractConfig.put("url", "https://api.example.com/extract");
        extractConfig.put("method", "POST");
        extractConfig.put("requestBody", "{\"connectionId\": \"${connect-to-source.output.connectionId}\"}");
        extractTask.setConfiguration(extractConfig);

        // Store data
        TaskDefinition storeTask = new TaskDefinition();
        storeTask.setName("store-data");
        storeTask.setDescription("Store extracted data");
        storeTask.setType("http");
        storeTask.setDependsOn(List.of(extractTask.getId()));
        Map<String, String> storeConfig = new HashMap<>();
        storeConfig.put("url", "https://api.example.com/store");
        storeConfig.put("method", "POST");
        storeConfig.put("requestBody", "{\"data\": ${extract-data.output.data}}");
        storeTask.setConfiguration(storeConfig);

        tasks.add(connectTask);
        tasks.add(extractTask);
        tasks.add(storeTask);
        workflow.setTasks(tasks);

        return workflow;
    }

    /**
     * Create data process sub-workflow
     */
    private WorkflowDefinition createDataProcessWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("data-process");
        workflow.setDescription("Process extracted data");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Validate data
        TaskDefinition validateTask = new TaskDefinition();
        validateTask.setName("validate-data");
        validateTask.setDescription("Validate data format");
        validateTask.setType("http");
        Map<String, String> validateConfig = new HashMap<>();
        validateConfig.put("url", "https://api.example.com/validate");
        validateConfig.put("method", "POST");
        validateConfig.put("requestBody", "{\"dataId\": \"${dataId}\"}");
        validateTask.setConfiguration(validateConfig);

        // Transform data
        TaskDefinition transformTask = new TaskDefinition();
        transformTask.setName("transform-data");
        transformTask.setDescription("Transform data format");
        transformTask.setType("http");
        Map<String, String> transformConfig = new HashMap<>();
        transformConfig.put("url", "https://api.example.com/transform");
        transformConfig.put("method", "POST");
        transformConfig.put("requestBody", "{\"dataId\": \"${dataId}\"}");
        transformTask.setConfiguration(transformConfig);

        // Enrich data
        TaskDefinition enrichTask = new TaskDefinition();
        enrichTask.setName("enrich-data");
        enrichTask.setDescription("Enrich data with additional info");
        enrichTask.setType("http");
        Map<String, String> enrichConfig = new HashMap<>();
        enrichConfig.put("url", "https://api.example.com/enrich");
        enrichConfig.put("method", "POST");
        enrichConfig.put("requestBody", "{\"dataId\": \"${dataId}\"}");
        enrichTask.setConfiguration(enrichConfig);

        // Add to execution group for parallel execution
        validateTask.setExecutionGroup("process-group");
        transformTask.setExecutionGroup("process-group");
        enrichTask.setExecutionGroup("process-group");

        tasks.add(validateTask);
        tasks.add(transformTask);
        tasks.add(enrichTask);
        workflow.setTasks(tasks);

        return workflow;
    }

    /**
     * Create data load sub-workflow
     */
    private WorkflowDefinition createDataLoadWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("data-load");
        workflow.setDescription("Load processed data to target");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Prepare target
        TaskDefinition prepareTask = new TaskDefinition();
        prepareTask.setName("prepare-target");
        prepareTask.setDescription("Prepare target system");
        prepareTask.setType("http");
        Map<String, String> prepareConfig = new HashMap<>();
        prepareConfig.put("url", "https://api.example.com/prepare");
        prepareConfig.put("method", "POST");
        prepareConfig.put("requestBody", "{\"targetId\": \"${targetId}\"}");
        prepareTask.setConfiguration(prepareConfig);

        // Load data
        TaskDefinition loadTask = new TaskDefinition();
        loadTask.setName("load-data");
        loadTask.setDescription("Load data to target");
        loadTask.setType("http");
        loadTask.setDependsOn(List.of(prepareTask.getId()));
        Map<String, String> loadConfig = new HashMap<>();
        loadConfig.put("url", "https://api.example.com/load");
        loadConfig.put("method", "POST");
        loadConfig.put("requestBody", "{\"targetId\": \"${targetId}\", \"dataId\": \"${processedDataId}\"}");
        loadTask.setConfiguration(loadConfig);

        // Verify load
        TaskDefinition verifyTask = new TaskDefinition();
        verifyTask.setName("verify-load");
        verifyTask.setDescription("Verify data loading");
        verifyTask.setType("http");
        verifyTask.setDependsOn(List.of(loadTask.getId()));
        Map<String, String> verifyConfig = new HashMap<>();
        verifyConfig.put("url", "https://api.example.com/verify");
        verifyConfig.put("method", "GET");
        verifyConfig.put("requestBody", "{\"loadId\": \"${load-data.output.loadId}\"}");
        verifyTask.setConfiguration(verifyConfig);

        tasks.add(prepareTask);
        tasks.add(loadTask);
        tasks.add(verifyTask);
        workflow.setTasks(tasks);

        return workflow;
    }

    /**
     * Create parent workflow that uses sub-workflows
     */
    private WorkflowDefinition createParentWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("parent-workflow");
        workflow.setDescription("Main workflow orchestrating sub-workflows");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Execute extract workflow
        TaskDefinition extractTask = new TaskDefinition();
        extractTask.setName("run-extract");
        extractTask.setDescription("Execute data extract workflow");
        extractTask.setType("workflow");
        Map<String, String> extractConfig = new HashMap<>();
        extractConfig.put("workflowName", "data-extract");
        extractConfig.put("waitForCompletion", "true");
        extractConfig.put("timeoutSeconds", "1800");
        extractConfig.put("inputVariables", "{\"sourceId\": \"${sourceId}\", \"executionId\": \"${executionId}\"}");
        extractTask.setConfiguration(extractConfig);

        // Execute process workflow
        TaskDefinition processTask = new TaskDefinition();
        processTask.setName("run-process");
        processTask.setDescription("Execute data process workflow");
        processTask.setType("workflow");
        processTask.setDependsOn(List.of(extractTask.getId()));
        Map<String, String> processConfig = new HashMap<>();
        processConfig.put("workflowName", "data-process");
        processConfig.put("waitForCompletion", "true");
        processConfig.put("timeoutSeconds", "1800");
        processConfig.put("inputVariables", "{\"dataId\": \"${run-extract.output.subWorkflow.dataId}\"}");
        processTask.setConfiguration(processConfig);

        // Execute load workflow
        TaskDefinition loadTask = new TaskDefinition();
        loadTask.setName("run-load");
        loadTask.setDescription("Execute data load workflow");
        loadTask.setType("workflow");
        loadTask.setDependsOn(List.of(processTask.getId()));
        Map<String, String> loadConfig = new HashMap<>();
        loadConfig.put("workflowName", "data-load");
        loadConfig.put("waitForCompletion", "true");
        loadConfig.put("timeoutSeconds", "1800");
        loadConfig.put("inputVariables", "{\"processedDataId\": \"${run-process.output.subWorkflow.processedDataId}\", \"targetId\": \"warehouse\"}");
        loadTask.setConfiguration(loadConfig);

        // Send notification
        TaskDefinition notifyTask = new TaskDefinition();
        notifyTask.setName("send-notification");
        notifyTask.setDescription("Send workflow completion notification");
        notifyTask.setType("http");
        notifyTask.setDependsOn(List.of(loadTask.getId()));
        Map<String, String> notifyConfig = new HashMap<>();
        notifyConfig.put("url", "https://api.example.com/notify");
        notifyConfig.put("method", "POST");
        notifyConfig.put("requestBody", "{\"executionId\": \"${executionId}\", \"status\": \"completed\"}");
        notifyTask.setConfiguration(notifyConfig);

        tasks.add(extractTask);
        tasks.add(processTask);
        tasks.add(loadTask);
        tasks.add(notifyTask);
        workflow.setTasks(tasks);

        return workflow;
    }
}
