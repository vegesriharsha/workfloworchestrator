package com.example.workfloworchestrator;

import com.example.workfloworchestrator.controller.UserReviewController;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.repository.TaskExecutionRepository;
import com.example.workfloworchestrator.repository.WorkflowDefinitionRepository;
import com.example.workfloworchestrator.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WorkflowOrchestratorIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    private String baseUrl;
    private HttpHeaders headers;

    @BeforeEach
    public void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @AfterEach
    public void tearDown() {
        // Clean up the database after each test
        taskExecutionRepository.deleteAll();
        workflowExecutionRepository.deleteAll();
        workflowDefinitionRepository.deleteAll();
    }

    @Test
    public void testCreateAndExecuteSimpleWorkflow() throws Exception {
        // 1. Create a simple workflow definition
        WorkflowDefinition workflowDefinition = createSimpleWorkflow();
        
        // 2. Save the workflow definition
        HttpEntity<WorkflowDefinition> workflowRequest = new HttpEntity<>(workflowDefinition, headers);
        ResponseEntity<WorkflowDefinition> workflowResponse = restTemplate.postForEntity(
                baseUrl + "/workflows", workflowRequest, WorkflowDefinition.class);
        
        assertEquals(HttpStatus.CREATED, workflowResponse.getStatusCode());
        Long workflowId = workflowResponse.getBody().getId();
        assertNotNull(workflowId);
        
        // 3. Start workflow execution
        Map<String, String> variables = new HashMap<>();
        variables.put("inputParam", "testValue");
        
        HttpEntity<Map<String, String>> executionRequest = new HttpEntity<>(variables, headers);
        ResponseEntity<WorkflowExecution> executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=SimpleWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        Long executionId = executionResponse.getBody().getId();
        assertNotNull(executionId);
        
        // 4. Wait for workflow to complete (up to 10 seconds)
        WorkflowExecution finalExecution = waitForWorkflowCompletion(executionId, 10);
        
        // 5. Verify workflow completed successfully
        assertEquals(WorkflowStatus.COMPLETED, finalExecution.getStatus());
        assertEquals(2, finalExecution.getTaskExecutions().size());
        
        // 6. Verify all tasks completed successfully
        for (TaskExecution taskExecution : finalExecution.getTaskExecutions()) {
            assertEquals(TaskStatus.COMPLETED, taskExecution.getStatus());
        }
    }

    @Test
    public void testParallelTaskExecution() throws Exception {
        // 1. Create a workflow with parallel execution strategy
        WorkflowDefinition workflowDefinition = createParallelWorkflow();
        
        // 2. Save the workflow definition
        HttpEntity<WorkflowDefinition> workflowRequest = new HttpEntity<>(workflowDefinition, headers);
        ResponseEntity<WorkflowDefinition> workflowResponse = restTemplate.postForEntity(
                baseUrl + "/workflows", workflowRequest, WorkflowDefinition.class);
        
        assertEquals(HttpStatus.CREATED, workflowResponse.getStatusCode());
        
        // 3. Start workflow execution
        Map<String, String> variables = new HashMap<>();
        HttpEntity<Map<String, String>> executionRequest = new HttpEntity<>(variables, headers);
        ResponseEntity<WorkflowExecution> executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=ParallelWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        Long executionId = executionResponse.getBody().getId();
        
        // 4. Wait for workflow to complete
        WorkflowExecution finalExecution = waitForWorkflowCompletion(executionId, 15);
        
        // 5. Verify workflow completed successfully
        assertEquals(WorkflowStatus.COMPLETED, finalExecution.getStatus());
        assertEquals(3, finalExecution.getTaskExecutions().size());
        
        // 6. Verify all tasks completed successfully
        for (TaskExecution taskExecution : finalExecution.getTaskExecutions()) {
            assertEquals(TaskStatus.COMPLETED, taskExecution.getStatus());
        }
        
        // 7. Verify that the parallel tasks ran concurrently
        List<TaskExecution> tasks = finalExecution.getTaskExecutions();
        
        // Find the two parallel tasks
        TaskExecution task1 = null;
        TaskExecution task2 = null;
        
        for (TaskExecution task : tasks) {
            if (task.getTaskDefinition().getName().equals("ParallelTask1")) {
                task1 = task;
            } else if (task.getTaskDefinition().getName().equals("ParallelTask2")) {
                task2 = task;
            }
        }
        
        assertNotNull(task1);
        assertNotNull(task2);
        
        // Verify both tasks started before either completed
        boolean overlapping = (task1.getStartedAt().isBefore(task2.getCompletedAt()) && 
                               task2.getStartedAt().isBefore(task1.getCompletedAt()));
        
        assertTrue(overlapping, "Parallel tasks should have executed concurrently");
    }

    @Test
    public void testConditionalTaskExecution() throws Exception {
        // 1. Create a workflow with conditional execution
        WorkflowDefinition workflowDefinition = createConditionalWorkflow();
        
        // 2. Save the workflow definition
        HttpEntity<WorkflowDefinition> workflowRequest = new HttpEntity<>(workflowDefinition, headers);
        ResponseEntity<WorkflowDefinition> workflowResponse = restTemplate.postForEntity(
                baseUrl + "/workflows", workflowRequest, WorkflowDefinition.class);
        
        assertEquals(HttpStatus.CREATED, workflowResponse.getStatusCode());
        
        // 3. Start workflow execution with condition = true
        Map<String, String> variables = new HashMap<>();
        variables.put("condition", "true");
        
        HttpEntity<Map<String, String>> executionRequest = new HttpEntity<>(variables, headers);
        ResponseEntity<WorkflowExecution> executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=ConditionalWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        Long executionId = executionResponse.getBody().getId();
        
        // 4. Wait for workflow to complete
        WorkflowExecution finalExecution = waitForWorkflowCompletion(executionId, 10);
        
        // 5. Verify workflow completed successfully
        assertEquals(WorkflowStatus.COMPLETED, finalExecution.getStatus());
        
        // 6. Verify the "if true" task was executed
        boolean ifTrueTaskExecuted = false;
        boolean ifFalseTaskExecuted = false;
        
        for (TaskExecution taskExecution : finalExecution.getTaskExecutions()) {
            if (taskExecution.getTaskDefinition().getName().equals("IfTrueTask")) {
                ifTrueTaskExecuted = true;
                assertEquals(TaskStatus.COMPLETED, taskExecution.getStatus());
            }
            if (taskExecution.getTaskDefinition().getName().equals("IfFalseTask")) {
                ifFalseTaskExecuted = true;
            }
        }
        
        assertTrue(ifTrueTaskExecuted, "The 'if true' task should have been executed");
        assertFalse(ifFalseTaskExecuted, "The 'if false' task should not have been executed");
        
        // 7. Now test with condition = false
        workflowExecutionRepository.deleteAll(); // Clean up previous execution
        
        variables.put("condition", "false");
        executionRequest = new HttpEntity<>(variables, headers);
        executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=ConditionalWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        executionId = executionResponse.getBody().getId();
        
        // 8. Wait for workflow to complete
        finalExecution = waitForWorkflowCompletion(executionId, 10);
        
        // 9. Verify workflow completed successfully
        assertEquals(WorkflowStatus.COMPLETED, finalExecution.getStatus());
        
        // 10. Verify the "if false" task was executed
        ifTrueTaskExecuted = false;
        ifFalseTaskExecuted = false;
        
        for (TaskExecution taskExecution : finalExecution.getTaskExecutions()) {
            if (taskExecution.getTaskDefinition().getName().equals("IfTrueTask")) {
                ifTrueTaskExecuted = true;
            }
            if (taskExecution.getTaskDefinition().getName().equals("IfFalseTask")) {
                ifFalseTaskExecuted = true;
                assertEquals(TaskStatus.COMPLETED, taskExecution.getStatus());
            }
        }
        
        assertFalse(ifTrueTaskExecuted, "The 'if true' task should not have been executed");
        assertTrue(ifFalseTaskExecuted, "The 'if false' task should have been executed");
    }

    @Test
    public void testWorkflowWithUserReviewPoints() throws Exception {
        // 1. Create a workflow with user review points
        WorkflowDefinition workflowDefinition = createWorkflowWithUserReview();
        
        // 2. Save the workflow definition
        HttpEntity<WorkflowDefinition> workflowRequest = new HttpEntity<>(workflowDefinition, headers);
        ResponseEntity<WorkflowDefinition> workflowResponse = restTemplate.postForEntity(
                baseUrl + "/workflows", workflowRequest, WorkflowDefinition.class);
        
        assertEquals(HttpStatus.CREATED, workflowResponse.getStatusCode());
        
        // 3. Start workflow execution
        Map<String, String> variables = new HashMap<>();
        HttpEntity<Map<String, String>> executionRequest = new HttpEntity<>(variables, headers);
        ResponseEntity<WorkflowExecution> executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=ReviewWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        Long executionId = executionResponse.getBody().getId();
        
        // 4. Wait for workflow to reach review point (status = AWAITING_USER_REVIEW)
        WorkflowExecution waitingExecution = null;
        for (int i = 0; i < 10; i++) {
            ResponseEntity<WorkflowExecution> response = restTemplate.getForEntity(
                    baseUrl + "/executions/" + executionId, 
                    WorkflowExecution.class);
            
            if (response.getBody().getStatus() == WorkflowStatus.AWAITING_USER_REVIEW) {
                waitingExecution = response.getBody();
                break;
            }
            
            TimeUnit.SECONDS.sleep(1);
        }
        
        assertNotNull(waitingExecution, "Workflow should have reached AWAITING_USER_REVIEW status");
        assertEquals(WorkflowStatus.AWAITING_USER_REVIEW, waitingExecution.getStatus());
        
        // 5. Find the review point
        assertFalse(waitingExecution.getReviewPoints().isEmpty(), "Review points should exist");
        Long reviewPointId = waitingExecution.getReviewPoints().get(0).getId();
        
        // 6. Submit a review approval
        UserReviewController.EnhancedReviewRequest reviewRequest = new UserReviewController.EnhancedReviewRequest();
        reviewRequest.setDecision(UserReviewPoint.ReviewDecision.APPROVE);
        reviewRequest.setReviewer("test-user");
        reviewRequest.setComment("Approved in automated test");
        
        HttpEntity<UserReviewController.EnhancedReviewRequest> reviewHttpRequest =
                new HttpEntity<>(reviewRequest, headers);
        
        ResponseEntity<WorkflowExecution> reviewResponse = restTemplate.exchange(
                baseUrl + "/reviews/" + reviewPointId + "/submit",
                HttpMethod.POST,
                reviewHttpRequest,
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.OK, reviewResponse.getStatusCode());
        
        // 7. Wait for workflow to complete
        WorkflowExecution finalExecution = waitForWorkflowCompletion(executionId, 10);
        
        // 8. Verify workflow completed successfully
        assertEquals(WorkflowStatus.COMPLETED, finalExecution.getStatus());
        
        // 9. Verify review point was properly recorded
        assertFalse(finalExecution.getReviewPoints().isEmpty());
        UserReviewPoint reviewPoint = finalExecution.getReviewPoints().get(0);
        assertEquals(UserReviewPoint.ReviewDecision.APPROVE, reviewPoint.getDecision());
        assertEquals("test-user", reviewPoint.getReviewer());
        assertNotNull(reviewPoint.getReviewedAt());
    }

    @Test
    public void testErrorHandlingAndRetryMechanism() throws Exception {
        // 1. Create a workflow with a task that will fail
        WorkflowDefinition workflowDefinition = createWorkflowWithFailingTask();
        
        // 2. Save the workflow definition
        HttpEntity<WorkflowDefinition> workflowRequest = new HttpEntity<>(workflowDefinition, headers);
        ResponseEntity<WorkflowDefinition> workflowResponse = restTemplate.postForEntity(
                baseUrl + "/workflows", workflowRequest, WorkflowDefinition.class);
        
        assertEquals(HttpStatus.CREATED, workflowResponse.getStatusCode());
        
        // 3. Start workflow execution
        Map<String, String> variables = new HashMap<>();
        variables.put("shouldFail", "true"); // This will make the task fail
        
        HttpEntity<Map<String, String>> executionRequest = new HttpEntity<>(variables, headers);
        ResponseEntity<WorkflowExecution> executionResponse = restTemplate.postForEntity(
                baseUrl + "/executions/start?workflowName=RetryWorkflow", 
                executionRequest, 
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.CREATED, executionResponse.getStatusCode());
        Long executionId = executionResponse.getBody().getId();
        
        // 4. Wait for workflow to fail
        WorkflowExecution failedExecution = null;
        for (int i = 0; i < 10; i++) {
            ResponseEntity<WorkflowExecution> response = restTemplate.getForEntity(
                    baseUrl + "/executions/" + executionId, 
                    WorkflowExecution.class);
            
            if (response.getBody().getStatus() == WorkflowStatus.FAILED) {
                failedExecution = response.getBody();
                break;
            }
            
            TimeUnit.SECONDS.sleep(1);
        }
        
        assertNotNull(failedExecution, "Workflow should have failed");
        assertEquals(WorkflowStatus.FAILED, failedExecution.getStatus());
        
        // 5. Find the failed task
        TaskExecution failedTask = null;
        for (TaskExecution task : failedExecution.getTaskExecutions()) {
            if (task.getStatus() == TaskStatus.FAILED) {
                failedTask = task;
                break;
            }
        }
        
        assertNotNull(failedTask, "There should be a failed task");
        
        // 6. Retry the workflow
        ResponseEntity<WorkflowExecution> retryResponse = restTemplate.postForEntity(
                baseUrl + "/executions/" + executionId + "/retry",
                null,
                WorkflowExecution.class);
        
        assertEquals(HttpStatus.OK, retryResponse.getStatusCode());
        
        // 7. Update variables to allow the task to succeed this time
        Map<String, String> updatedVariables = new HashMap<>();
        updatedVariables.put("shouldFail", "false"); // This will make the task succeed
        
        // We need to update the workflow variables directly in the database 
        // or through a service call since there's no direct API for this
        WorkflowExecution execution = workflowExecutionRepository.findById(executionId).get();
        execution.getVariables().put("shouldFail", "false");
        workflowExecutionRepository.save(execution);
        
        // 8. Wait for workflow to complete
        WorkflowExecution completedExecution = waitForWorkflowCompletion(executionId, 10);
        
        // 9. Verify workflow completed successfully after retry
        assertEquals(WorkflowStatus.COMPLETED, completedExecution.getStatus());
        
        // 10. Verify retry count was incremented
        assertTrue(completedExecution.getRetryCount() > 0, "Retry count should be incremented");
    }

    /**
     * Helper method to wait for a workflow to complete
     */
    private WorkflowExecution waitForWorkflowCompletion(Long executionId, int timeoutSeconds) throws InterruptedException {
        WorkflowExecution execution = null;
        
        for (int i = 0; i < timeoutSeconds; i++) {
            ResponseEntity<WorkflowExecution> response = restTemplate.getForEntity(
                    baseUrl + "/executions/" + executionId, 
                    WorkflowExecution.class);
            
            execution = response.getBody();
            
            if (execution.getStatus() == WorkflowStatus.COMPLETED || 
                execution.getStatus() == WorkflowStatus.FAILED) {
                break;
            }
            
            TimeUnit.SECONDS.sleep(1);
        }
        
        return execution;
    }

    /**
     * Create a simple sequential workflow with two tasks
     */
    private WorkflowDefinition createSimpleWorkflow() {
        // Create task definitions
        TaskDefinition task1 = TaskDefinition.builder()
                .name("Task1")
                .description("First task in workflow")
                .type("REST")
                .executionOrder(1)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/task1",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
                .name("Task2")
                .description("Second task in workflow")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/task2",
                    "method", "POST"
                ))
                .build();
        
        // Create workflow definition
        return WorkflowDefinition.builder()
                .name("SimpleWorkflow")
                .description("A simple sequential workflow with two tasks")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)
                .tasks(Arrays.asList(task1, task2))
                .build();
    }

    /**
     * Create a workflow with parallel execution
     */
    private WorkflowDefinition createParallelWorkflow() {
        // Create task definitions
        TaskDefinition task1 = TaskDefinition.builder()
                .name("InitialTask")
                .description("Initial task")
                .type("REST")
                .executionOrder(1)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/initial",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
                .name("ParallelTask1")
                .description("First parallel task")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .executionGroup("parallel-group")
                .parallelExecution(true)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/parallel1",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task3 = TaskDefinition.builder()
                .name("ParallelTask2")
                .description("Second parallel task")
                .type("REST")
                .executionOrder(2)  // Same order for parallel execution
                .executionMode(ExecutionMode.API)
                .executionGroup("parallel-group")
                .parallelExecution(true)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/parallel2",
                    "method", "POST"
                ))
                .build();
        
        // Create workflow definition
        return WorkflowDefinition.builder()
                .name("ParallelWorkflow")
                .description("A workflow with parallel task execution")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.PARALLEL)
                .tasks(Arrays.asList(task1, task2, task3))
                .build();
    }

    /**
     * Create a workflow with conditional execution
     */
    private WorkflowDefinition createConditionalWorkflow() {
        // Create task definitions
        TaskDefinition task1 = TaskDefinition.builder()
                .name("ConditionalTask")
                .description("Task with conditional branching")
                .type("CONDITIONAL")
                .executionOrder(1)
                .executionMode(ExecutionMode.API)
                .conditionalExpression("${condition == 'true'}")
                .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
                .name("IfTrueTask")
                .description("Task executed if condition is true")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/true-task",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task3 = TaskDefinition.builder()
                .name("IfFalseTask")
                .description("Task executed if condition is false")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/false-task",
                    "method", "POST"
                ))
                .build();
        
        // Set up conditional next task references
        task1.setNextTaskOnSuccess(task2.getId());
        task1.setNextTaskOnFailure(task3.getId());
        
        // Create workflow definition
        return WorkflowDefinition.builder()
                .name("ConditionalWorkflow")
                .description("A workflow with conditional task execution")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.CONDITIONAL)
                .tasks(Arrays.asList(task1, task2, task3))
                .build();
    }

    /**
     * Create a workflow with user review point
     */
    private WorkflowDefinition createWorkflowWithUserReview() {
        // Create task definitions
        TaskDefinition task1 = TaskDefinition.builder()
                .name("PrepareDataTask")
                .description("Task to prepare data for review")
                .type("REST")
                .executionOrder(1)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/prepare-data",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
                .name("ReviewTask")
                .description("Task requiring user review")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .requireUserReview(true)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/review-data",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task3 = TaskDefinition.builder()
                .name("FinalizeTask")
                .description("Final task after review")
                .type("REST")
                .executionOrder(3)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/finalize",
                    "method", "POST"
                ))
                .build();
        
        // Create workflow definition
        return WorkflowDefinition.builder()
                .name("ReviewWorkflow")
                .description("A workflow with user review point")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)
                .tasks(Arrays.asList(task1, task2, task3))
                .build();
    }

    /**
     * Create a workflow with a failing task for testing retry mechanism
     */
    private WorkflowDefinition createWorkflowWithFailingTask() {
        // Create task definitions
        TaskDefinition task1 = TaskDefinition.builder()
                .name("InitialTask")
                .description("Initial task")
                .type("REST")
                .executionOrder(1)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/initial",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task2 = TaskDefinition.builder()
                .name("FailingTask")
                .description("Task that can fail")
                .type("REST")
                .executionOrder(2)
                .executionMode(ExecutionMode.API)
                .retryLimit(3)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/failing-task?shouldFail=${shouldFail}",
                    "method", "POST"
                ))
                .build();
        
        TaskDefinition task3 = TaskDefinition.builder()
                .name("FinalTask")
                .description("Final task")
                .type("REST")
                .executionOrder(3)
                .executionMode(ExecutionMode.API)
                .configuration(Map.of(
                    "url", "http://localhost:" + port + "/api/mock/final",
                    "method", "POST"
                ))
                .build();
        
        // Create workflow definition
        return WorkflowDefinition.builder()
                .name("RetryWorkflow")
                .description("A workflow with a task that can fail")
                .version("1.0")
                .createdAt(LocalDateTime.now())
                .strategyType(WorkflowDefinition.ExecutionStrategyType.SEQUENTIAL)
                .tasks(Arrays.asList(task1, task2, task3))
                .build();
    }
}
