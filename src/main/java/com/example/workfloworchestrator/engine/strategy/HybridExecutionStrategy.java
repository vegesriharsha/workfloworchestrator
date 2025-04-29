package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import com.example.workfloworchestrator.util.TaskDependencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hybrid execution strategy combining graph-based dependencies and execution groups
 * Provides the most flexibility and control over workflow execution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskDependencyUtil taskDependencyUtil;

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> allTasks = definition.getTasks();

            // Validate task dependencies
            if (!taskDependencyUtil.validateDependencies(definition)) {
                throw new WorkflowException("Invalid task dependencies in workflow: " + definition.getName());
            }

            // Execute tasks with dependencies
            executeTasksWithDependencies(workflowExecution, allTasks, resultFuture);

        } catch (Exception e) {
            log.error("Error in hybrid execution strategy", e);
            resultFuture.completeExceptionally(
                    new WorkflowException("Hybrid execution failed: " + e.getMessage(), e));
            updateWorkflowOnError(workflowExecution, e);
        }

        return resultFuture;
    }

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();

            // Filter tasks based on provided IDs
            List<TaskDefinition> selectedTasks = definition.getTasks().stream()
                    .filter(task -> taskIds.contains(task.getId()))
                    .collect(Collectors.toList());

            if (selectedTasks.isEmpty()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
                return resultFuture;
            }

            // Execute the subset of tasks
            executeTasksWithDependencies(workflowExecution, selectedTasks, resultFuture);

        } catch (Exception e) {
            log.error("Error in hybrid subset execution", e);
            resultFuture.completeExceptionally(
                    new WorkflowException("Hybrid subset execution failed: " + e.getMessage(), e));
            updateWorkflowOnError(workflowExecution, e);
        }

        return resultFuture;
    }

    /**
     * Execute tasks respecting their dependencies and execution groups
     */
    private void executeTasksWithDependencies(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> allTasks,
            CompletableFuture<WorkflowStatus> resultFuture) {

        // Find tasks with no dependencies to start with
        List<TaskDefinition> currentBatch = allTasks.stream()
                .filter(task -> !task.hasDependencies() || task.getDependsOn().isEmpty())
                .collect(Collectors.toList());

        // Set to track completed task IDs
        Set<Long> completedTaskIds = Collections.synchronizedSet(new HashSet<>());

        // Process tasks in batches until all are executed
        processBatchesUntilCompletion(workflowExecution, allTasks, currentBatch, completedTaskIds, resultFuture);
    }

    /**
     * Process batches of tasks until all are completed
     */
    private void processBatchesUntilCompletion(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> allTasks,
            List<TaskDefinition> currentBatch,
            Set<Long> completedTaskIds,
            CompletableFuture<WorkflowStatus> resultFuture) {

        if (currentBatch.isEmpty()) {
            // Check if all tasks are completed
            if (completedTaskIds.size() == allTasks.size()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
            } else {
                // Some tasks could not be executed (possibly due to unmet dependencies)
                log.warn("Not all tasks were executed. Executed: {}, Total: {}",
                        completedTaskIds.size(), allTasks.size());
                resultFuture.complete(WorkflowStatus.COMPLETED);
            }
            return;
        }

        // Group tasks in current batch by execution group
        Map<String, List<TaskDefinition>> groupedTasks = currentBatch.stream()
                .collect(Collectors.groupingBy(task ->
                        task.getExecutionGroup() != null ? task.getExecutionGroup() : "default"));

        // Futures for each group's execution
        List<CompletableFuture<Void>> groupFutures = new ArrayList<>();

        // Map to track task execution futures
        Map<Long, CompletableFuture<TaskExecution>> taskFutures = new ConcurrentHashMap<>();

        // Process each group
        for (Map.Entry<String, List<TaskDefinition>> entry : groupedTasks.entrySet()) {
            String executionGroup = entry.getKey();
            List<TaskDefinition> groupTasks = entry.getValue();

            // Check execution mode for this group
            boolean executeInParallel = shouldExecuteInParallel(executionGroup, groupTasks);

            CompletableFuture<Void> groupFuture;
            if (executeInParallel) {
                groupFuture = executeTasksInParallel(workflowExecution, groupTasks, taskFutures);
            } else {
                groupFuture = executeTasksSequentially(workflowExecution, groupTasks, taskFutures);
            }

            groupFutures.add(groupFuture);
        }

        // When all groups complete
        CompletableFuture<Void> allGroupsFuture = CompletableFuture.allOf(
                groupFutures.toArray(new CompletableFuture[0]));

        allGroupsFuture.whenComplete((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("Error executing task groups", throwable);
                    resultFuture.completeExceptionally(
                            new WorkflowException("Error executing task groups: " + throwable.getMessage(), throwable));
                    updateWorkflowOnError(workflowExecution, throwable);
                    return;
                }

                // Process completed tasks
                boolean anyTaskFailed = false;
                Map<String, String> outputCollector = new HashMap<>();

                for (Map.Entry<Long, CompletableFuture<TaskExecution>> entry : taskFutures.entrySet()) {
                    try {
                        TaskExecution taskExecution = entry.getValue().join();
                        Long taskId = entry.getKey();

                        // Add to completed tasks
                        completedTaskIds.add(taskId);

                        if (taskExecution.getStatus() == TaskStatus.FAILED) {
                            anyTaskFailed = true;
                            // For now, we'll continue with other tasks
                        } else if (taskExecution.getStatus() == TaskStatus.COMPLETED) {
                            // Collect outputs
                            outputCollector.putAll(taskExecution.getOutputs());
                        }
                    } catch (Exception e) {
                        log.error("Error processing task result", e);
                        anyTaskFailed = true;
                    }
                }

                // Update workflow variables with collected outputs
                Map<String, String> variables = workflowExecution.getVariables();
                variables.putAll(outputCollector);
                workflowExecution.setVariables(variables);
                workflowExecutionService.save(workflowExecution);

                // Check if we should stop on error
                if (anyTaskFailed && !shouldContinueOnError(workflowExecution)) {
                    workflowExecution.setErrorMessage("One or more tasks failed");
                    workflowExecutionService.updateWorkflowExecutionStatus(
                            workflowExecution.getId(), WorkflowStatus.FAILED);
                    resultFuture.complete(WorkflowStatus.FAILED);
                    return;
                }

                // Find next batch of tasks whose dependencies are now satisfied
                List<TaskDefinition> nextBatch = findNextBatchOfTasks(workflowExecution, allTasks, completedTaskIds);

                // Continue with next batch
                processBatchesUntilCompletion(workflowExecution, allTasks, nextBatch, completedTaskIds, resultFuture);

            } catch (Exception e) {
                log.error("Error processing batch completion", e);
                resultFuture.completeExceptionally(
                        new WorkflowException("Error processing batch completion: " + e.getMessage(), e));
                updateWorkflowOnError(workflowExecution, e);
            }
        });
    }

    /**
     * Find tasks whose dependencies are now satisfied
     */
    private List<TaskDefinition> findNextBatchOfTasks(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> allTasks,
            Set<Long> completedTaskIds) {

        return allTasks.stream()
                .filter(task -> !completedTaskIds.contains(task.getId())) // Not already executed
                .filter(task -> {
                    // Check if all dependencies are satisfied
                    if (!task.hasDependencies()) {
                        return true;
                    }

                    return completedTaskIds.containsAll(task.getDependsOn());
                })
                .filter(task -> evaluateCondition(task, workflowExecution)) // Check conditional expression
                .collect(Collectors.toList());
    }

    /**
     * Determine if tasks in a group should execute in parallel
     */
    private boolean shouldExecuteInParallel(String executionGroup, List<TaskDefinition> tasks) {
        // For now, default is to execute in parallel if all tasks have parallelExecution=true
        return tasks.stream().allMatch(TaskDefinition::isParallelExecution);
    }

    /**
     * Execute tasks in parallel
     */
    private CompletableFuture<Void> executeTasksInParallel(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures) {

        List<CompletableFuture<TaskExecution>> futures = new ArrayList<>();

        for (TaskDefinition taskDefinition : tasks) {
            // Prepare inputs from workflow variables
            Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

            // Create task execution
            TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                    workflowExecution, taskDefinition, inputs);

            // Check if task requires user review
            CompletableFuture<TaskExecution> future;
            if (taskDefinition.isRequireUserReview()) {
                future = handleUserReviewTask(workflowExecution, taskExecution);
            } else {
                // Execute the task
                future = taskExecutionService.executeTask(taskExecution.getId());
            }

            futures.add(future);
            taskFutures.put(taskDefinition.getId(), future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Execute tasks sequentially
     */
    private CompletableFuture<Void> executeTasksSequentially(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures) {

        CompletableFuture<Void> sequenceFuture = new CompletableFuture<>();

        // Execute tasks one by one
        executeTaskSequence(workflowExecution, tasks, 0, taskFutures, sequenceFuture);

        return sequenceFuture;
    }

    /**
     * Execute tasks in sequence
     */
    private void executeTaskSequence(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            int index,
            Map<Long, CompletableFuture<TaskExecution>> taskFutures,
            CompletableFuture<Void> sequenceFuture) {

        if (index >= tasks.size()) {
            // All tasks in sequence completed
            sequenceFuture.complete(null);
            return;
        }

        TaskDefinition taskDefinition = tasks.get(index);

        // Prepare inputs from workflow variables
        Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

        // Create task execution
        TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                workflowExecution, taskDefinition, inputs);

        CompletableFuture<TaskExecution> taskFuture;

        // Check if task requires user review
        if (taskDefinition.isRequireUserReview()) {
            taskFuture = handleUserReviewTask(workflowExecution, taskExecution);
        } else {
            // Execute the task
            taskFuture = taskExecutionService.executeTask(taskExecution.getId());
        }

        // Store the task future
        taskFutures.put(taskDefinition.getId(), taskFuture);

        // When this task completes, process the result and execute the next one
        taskFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                sequenceFuture.completeExceptionally(throwable);
                return;
            }

            try {
                // If task completed successfully, update workflow variables
                if (result.getStatus() == TaskStatus.COMPLETED) {
                    Map<String, String> variables = workflowExecution.getVariables();
                    variables.putAll(result.getOutputs());
                    workflowExecution.setVariables(variables);
                    workflowExecutionService.save(workflowExecution);
                }

                // Move to next task in sequence
                executeTaskSequence(workflowExecution, tasks, index + 1, taskFutures, sequenceFuture);
            } catch (Exception e) {
                sequenceFuture.completeExceptionally(e);
            }
        });
    }

    /**
     * Handle user review task
     */
    private CompletableFuture<TaskExecution> handleUserReviewTask(
            WorkflowExecution workflowExecution,
            TaskExecution taskExecution) {

        CompletableFuture<TaskExecution> future = new CompletableFuture<>();

        try {
            // Move workflow to await review
            workflowExecutionService.updateWorkflowExecutionStatus(
                    workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);

            // Create review point
            taskExecutionService.createUserReviewPoint(taskExecution.getId());

            // Return the task execution in its current state
            future.complete(taskExecution);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Evaluate task condition
     */
    private boolean evaluateCondition(TaskDefinition taskDefinition, WorkflowExecution workflowExecution) {
        String condition = taskDefinition.getConditionalExpression();

        if (condition == null || condition.trim().isEmpty()) {
            // No condition, always execute
            return true;
        }

        try {
            // Get workflow variables
            Map<String, String> variables = workflowExecution.getVariables();

            // Simple expression evaluation for demo
            return evaluateSimpleExpression(condition, variables);
        } catch (Exception e) {
            log.error("Error evaluating condition for task {}: {}",
                    taskDefinition.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate a simple expression
     * For demonstration purposes only - in production use a proper expression engine
     */
    private boolean evaluateSimpleExpression(String expression, Map<String, String> variables) {
        // Handle variable references like ${task1.output.success}
        if (expression.startsWith("${") && expression.endsWith("}")) {
            String key = expression.substring(2, expression.length() - 1);
            String value = variables.get(key);
            return "true".equalsIgnoreCase(value);
        }

        // Handle AND expressions
        if (expression.contains("&&")) {
            String[] parts = expression.split("&&");
            return Arrays.stream(parts)
                    .map(String::trim)
                    .allMatch(part -> evaluateSimpleExpression(part, variables));
        }

        // Handle OR expressions
        if (expression.contains("||")) {
            String[] parts = expression.split("\\|\\|");
            return Arrays.stream(parts)
                    .map(String::trim)
                    .anyMatch(part -> evaluateSimpleExpression(part, variables));
        }

        // Direct boolean value
        return Boolean.parseBoolean(expression);
    }

    /**
     * Update workflow on error
     */
    private void updateWorkflowOnError(WorkflowExecution workflowExecution, Throwable throwable) {
        workflowExecution.setErrorMessage(throwable.getMessage());
        workflowExecutionService.updateWorkflowExecutionStatus(
                workflowExecution.getId(), WorkflowStatus.FAILED);
    }

    /**
     * Check if workflow should continue on error
     */
    private boolean shouldContinueOnError(WorkflowExecution workflowExecution) {
        Map<String, String> variables = workflowExecution.getVariables();
        return "true".equalsIgnoreCase(variables.getOrDefault("continueOnError", "false"));
    }
}
