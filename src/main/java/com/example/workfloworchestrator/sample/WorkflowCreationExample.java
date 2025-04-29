package com.example.workfloworchestrator.sample;

import com.example.workfloworchestrator.model.TaskDefinition;
import com.example.workfloworchestrator.model.WorkflowDefinition;
import com.example.workfloworchestrator.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating how to create workflows with graph-based dependencies
 */
@Slf4j
@RestController
@RequestMapping("/api/examples")
@RequiredArgsConstructor
public class WorkflowCreationExample {

    private final WorkflowService workflowService;

    /**
     * Create a sample ETL workflow with graph-based dependencies
     *
     * @return the created workflow definition
     */
    @PostMapping("/etl-workflow")
    public WorkflowDefinition createEtlWorkflow() {
        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("etl-data-pipeline");
        workflow.setDescription("Extract-Transform-Load Data Pipeline");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.HYBRID);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Extract tasks (can run in parallel)
        TaskDefinition extractCustomers = createExtractTask("extract-customers", "Extract customer data from CRM", "customers");
        TaskDefinition extractOrders = createExtractTask("extract-orders", "Extract order data from ERP", "orders");
        TaskDefinition extractProducts = createExtractTask("extract-products", "Extract product data from inventory", "products");

        // Transform tasks (depend on extract tasks)
        TaskDefinition transformCustomers = createTransformTask("transform-customers", "Transform customer data");
        transformCustomers.getDependsOn().add(extractCustomers.getId());

        TaskDefinition transformOrders = createTransformTask("transform-orders", "Transform order data");
        transformOrders.getDependsOn().add(extractOrders.getId());

        TaskDefinition transformProducts = createTransformTask("transform-products", "Transform product data");
        transformProducts.getDependsOn().add(extractProducts.getId());

        // Join task (depends on all transform tasks)
        TaskDefinition joinData = createJoinTask("join-data", "Join customer, order, and product data");
        joinData.getDependsOn().add(transformCustomers.getId());
        joinData.getDependsOn().add(transformOrders.getId());
        joinData.getDependsOn().add(transformProducts.getId());
        joinData.setConditionalExpression("${transformCustomers.output.success} && ${transformOrders.output.success} && ${transformProducts.output.success}");

        // Validation task (depends on join)
        TaskDefinition validateData = createValidationTask("validate-data", "Validate joined data");
        validateData.getDependsOn().add(joinData.getId());

        // Load task (depends on validation)
        TaskDefinition loadData = createLoadTask("load-data", "Load data to data warehouse");
        loadData.getDependsOn().add(validateData.getId());
        loadData.setConditionalExpression("${validateData.output.success}");

        // Report tasks (can run in parallel after load)
        TaskDefinition generateSalesReport = createReportTask("generate-sales-report", "Generate sales report");
        generateSalesReport.getDependsOn().add(loadData.getId());

        TaskDefinition generateInventoryReport = createReportTask("generate-inventory-report", "Generate inventory report");
        generateInventoryReport.getDependsOn().add(loadData.getId());

        // Notification task (depends on all reports)
        TaskDefinition sendNotification = createNotificationTask("send-notification", "Send notification to users");
        sendNotification.getDependsOn().add(generateSalesReport.getId());
        sendNotification.getDependsOn().add(generateInventoryReport.getId());

        // Add all tasks to workflow
        tasks.add(extractCustomers);
        tasks.add(extractOrders);
        tasks.add(extractProducts);
        tasks.add(transformCustomers);
        tasks.add(transformOrders);
        tasks.add(transformProducts);
        tasks.add(joinData);
        tasks.add(validateData);
        tasks.add(loadData);
        tasks.add(generateSalesReport);
        tasks.add(generateInventoryReport);
        tasks.add(sendNotification);

        workflow.setTasks(tasks);

        // Save and return the workflow definition
        return workflowService.createWorkflowDefinition(workflow);
    }

    /**
     * Create a sample approval workflow with conditional paths
     *
     * @return the created workflow definition
     */
    @PostMapping("/approval-workflow")
    public WorkflowDefinition createApprovalWorkflow() {
        // Create workflow definition
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("document-approval");
        workflow.setDescription("Document review and approval workflow");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setStrategyType(WorkflowDefinition.ExecutionStrategyType.HYBRID);

        List<TaskDefinition> tasks = new ArrayList<>();

        // Initial document submission (start task)
        TaskDefinition submitDocument = createHttpTask("submit-document", "Submit document for review");

        // Document validation (depends on submit)
        TaskDefinition validateDocument = createHttpTask("validate-document", "Validate document format and content");
        validateDocument.getDependsOn().add(submitDocument.getId());
        validateDocument.setExecutionGroup("validation");

        // Manager review (depends on validation, requires human input)
        TaskDefinition managerReview = createUserReviewTask("manager-review", "Manager reviews the document");
        managerReview.getDependsOn().add(validateDocument.getId());
        managerReview.setConditionalExpression("${validateDocument.output.success}");

        // Compliance check (runs in parallel with manager review if validation passes)
        TaskDefinition complianceCheck = createHttpTask("compliance-check", "Check document for compliance");
        complianceCheck.getDependsOn().add(validateDocument.getId());
        complianceCheck.setConditionalExpression("${validateDocument.output.success}");
        complianceCheck.setExecutionGroup("review");

        // Final approval (depends on both manager review and compliance check)
        TaskDefinition finalApproval = createHttpTask("final-approval", "Final document approval");
        finalApproval.getDependsOn().add(managerReview.getId());
        finalApproval.getDependsOn().add(complianceCheck.getId());
        finalApproval.setConditionalExpression("${managerReview.output.approved} && ${complianceCheck.output.compliant}");

        // Document publishing (depends on final approval)
        TaskDefinition publishDocument = createHttpTask("publish-document", "Publish approved document");
        publishDocument.getDependsOn().add(finalApproval.getId());

        // Rejection notification (conditional path if manager rejects)
        TaskDefinition rejectionNotification = createNotificationTask("rejection-notification", "Send rejection notification");
        rejectionNotification.getDependsOn().add(managerReview.getId());
        rejectionNotification.setConditionalExpression("!${managerReview.output.approved}");

        // Compliance failure notification (conditional path if compliance check fails)
        TaskDefinition complianceFailureNotification = createNotificationTask("compliance-failure", "Send compliance failure notification");
        complianceFailureNotification.getDependsOn().add(complianceCheck.getId());
        complianceFailureNotification.setConditionalExpression("!${complianceCheck.output.compliant}");

        // Add all tasks to workflow
        tasks.add(submitDocument);
        tasks.add(validateDocument);
        tasks.add(managerReview);
        tasks.add(complianceCheck);
        tasks.add(finalApproval);
        tasks.add(publishDocument);
        tasks.add(rejectionNotification);
        tasks.add(complianceFailureNotification);

        workflow.setTasks(tasks);

        // Save and return the workflow definition
        return workflowService.createWorkflowDefinition(workflow);
    }

    // Helper methods to create different types of tasks

    private TaskDefinition createExtractTask(String name, String description, String dataType) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId()); // In a real app, this would be auto-generated by the DB
        task.setName(name);
        task.setDescription(description);
        task.setType("database");
        task.setExecutionGroup("extract");
        task.setParallelExecution(true);

        Map<String, String> config = new HashMap<>();
        config.put("query", "SELECT * FROM " + dataType + " WHERE last_updated > :lastRunDate");
        config.put("connectionName", "source-db");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createTransformTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setExecutionGroup("transform");
        task.setParallelExecution(true);

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/transform");
        config.put("method", "POST");
        config.put("requestBody", "{\"data\": ${extract.output.data}}");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createJoinTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setExecutionGroup("process");

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/join");
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createValidationTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setExecutionGroup("process");

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/validate");
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createLoadTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("database");
        task.setExecutionGroup("load");

        Map<String, String> config = new HashMap<>();
        config.put("query", "INSERT INTO data_warehouse (data) VALUES (:transformedData)");
        config.put("connectionName", "target-db");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createReportTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setExecutionGroup("report");
        task.setParallelExecution(true);

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/report");
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createNotificationTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setExecutionGroup("notification");

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/notify");
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createHttpTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/" + name);
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    private TaskDefinition createUserReviewTask(String name, String description) {
        TaskDefinition task = new TaskDefinition();
        task.setId(generateTaskId());
        task.setName(name);
        task.setDescription(description);
        task.setType("http");
        task.setRequireUserReview(true);

        Map<String, String> config = new HashMap<>();
        config.put("url", "https://api.example.com/" + name);
        config.put("method", "POST");
        task.setConfiguration(config);

        return task;
    }

    // Generate a simple ID for tasks (in a real app, this would be handled by the DB)
    private static long nextTaskId = 1;

    private Long generateTaskId() {
        return nextTaskId++;
    }
}
