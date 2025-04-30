package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.TaskExecutionException;
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

/**
 * Extended sequential execution strategy that respects task dependencies
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SequentialExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final TaskDependencyUtil taskDependencyUtil;

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> tasks = definition.getTasks();

            // Validate dependencies
            if (!taskDependencyUtil.validateDependencies(definition)) {
                throw new WorkflowException("Invalid task dependencies in workflow: " + definition.getName());
            }

            // Sort tasks by dependency order (topological sort)
            List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(tasks);

            // Start from current task index (for resume/retry scenarios)
            int currentTaskIndex = workflowExecution.getCurrentTaskIndex() != null ?
                    workflowExecution.getCurrentTaskIndex() : 0;

            executeTasksSequentially(workflowExecution, sortedTasks, currentTaskIndex, resultFuture);

        } catch (Exception e) {
            log.error("Error in sequential extended execution strategy", e);
            resultFuture.completeExceptionally(
                    new WorkflowException("Sequential extended execution failed: " + e.getMessage(), e));
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
                    .toList();

            if (selectedTasks.isEmpty()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
                return resultFuture;
            }

            // Sort the subset of tasks by dependency order
            List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(selectedTasks);

            executeTasksSequentially(workflowExecution, sortedTasks, 0, resultFuture);

        } catch (Exception e) {
            log.error("Error in sequential extended subset execution", e);
            resultFuture.completeExceptionally(
                    new WorkflowException("Sequential extended subset execution failed: " + e.getMessage(), e));
            updateWorkflowOnError(workflowExecution, e);
        }

        return resultFuture;
    }

    /**
     * Handle task replay by determining execution starting point
     *
     * @param workflowExecution the workflow execution
     * @param replayFromTaskId the ID of the task to start replay from
     * @return CompletableFuture with the final workflow status
     */
    public CompletableFuture<WorkflowStatus> replayFromTask(WorkflowExecution workflowExecution, Long replayFromTaskId) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> taskDefinitions = definition.getTasks();

            // Find the task index for the specified task
            List<TaskExecution> taskExecutions = taskExecutionService.getTaskExecutionsForWorkflow(workflowExecution.getId());
            int startIndex = -1;

            for (int i = 0; i < taskExecutions.size(); i++) {
                if (taskExecutions.get(i).getId().equals(replayFromTaskId)) {
                    startIndex = i;
                    break;
                }
            }

            if (startIndex < 0) {
                throw new WorkflowException("Task not found for replay: " + replayFromTaskId);
            }

            // Update current task index
            workflowExecution.setCurrentTaskIndex(startIndex);
            workflowExecutionService.save(workflowExecution);

            // Execute tasks from the replay point
            executeTasksSequentially(workflowExecution, taskDefinitions, startIndex, resultFuture);

        } catch (Exception e) {
            log.error("Error in task replay", e);
            resultFuture.complete(WorkflowStatus.FAILED);
        }

        return resultFuture;
    }

    private void executeTasksSequentially(
            WorkflowExecution workflowExecution,
            List<TaskDefinition> tasks,
            int startIndex,
            CompletableFuture<WorkflowStatus> resultFuture) {

        if (startIndex >= tasks.size()) {
            // All tasks completed
            resultFuture.complete(WorkflowStatus.COMPLETED);
            return;
        }

        // Get current task to execute
        var taskDefinition = tasks.get(startIndex);

        // Check if conditions for this task are met
        if (!evaluateCondition(taskDefinition, workflowExecution)) {
            log.info("Skipping task {} due to condition not met", taskDefinition.getName());
            // Skip to next task
            executeTasksSequentially(workflowExecution, tasks, startIndex + 1, resultFuture);
            return;
        }

        // Prepare inputs from workflow variables
        var inputs = new HashMap<>(workflowExecution.getVariables());

        // Create task execution
        var taskExecution = taskExecutionService.createTaskExecution(
                workflowExecution, taskDefinition, inputs);

        // Update workflow's current task index
        workflowExecution.setCurrentTaskIndex(startIndex);
        workflowExecutionService.save(workflowExecution);

        // Check if task requires user review
        if (taskDefinition.isRequireUserReview()) {
            // Move workflow to await review
            workflowExecutionService.updateWorkflowExecutionStatus(
                    workflowExecution.getId(), WorkflowStatus.AWAITING_USER_REVIEW);

            // Create review point
            taskExecutionService.createUserReviewPoint(taskExecution.getId());

            // The workflow execution will be resumed when the review is submitted
            resultFuture.complete(WorkflowStatus.AWAITING_USER_REVIEW);
            return;
        }

        // Execute the task
        CompletableFuture<TaskExecution> taskFuture = taskExecutionService.executeTask(taskExecution.getId());

        taskFuture.whenComplete((completedTask, throwable) -> {
            try {
                if (throwable != null) {
                    handleTaskFailure(workflowExecution, taskExecution, throwable, resultFuture);
                    return;
                }

                // Check task status
                if (completedTask.getStatus() == TaskStatus.COMPLETED) {
                    // Update workflow variables with task outputs
                    Map<String, String> variables = workflowExecution.getVariables();
                    variables.putAll(completedTask.getOutputs());
                    workflowExecution.setVariables(variables);
                    workflowExecutionService.save(workflowExecution);

                    // Move to next task
                    executeTasksSequentially(workflowExecution, tasks, startIndex + 1, resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.FAILED) {
                    handleTaskFailure(workflowExecution, completedTask,
                            new TaskExecutionException(completedTask.getErrorMessage()), resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.AWAITING_RETRY) {
                    // Task will be retried later, so we wait
                    resultFuture.complete(WorkflowStatus.RUNNING);
                } else {
                    // Other statuses like SKIPPED, CANCELLED, etc.
                    // Just continue with next task
                    executeTasksSequentially(workflowExecution, tasks, startIndex + 1, resultFuture);
                }
            } catch (Exception e) {
                log.error("Error processing task completion", e);
                resultFuture.completeExceptionally(
                        new WorkflowException("Error processing task completion: " + e.getMessage(), e));
                updateWorkflowOnError(workflowExecution, e);
            }
        });
    }

    private void handleTaskFailure(
            WorkflowExecution workflowExecution,
            TaskExecution taskExecution,
            Throwable throwable,
            CompletableFuture<WorkflowStatus> resultFuture) {

        log.error("Task execution failed", throwable);

        // Check if there's a next task on failure defined
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        Long nextTaskOnFailure = taskDefinition.getNextTaskOnFailure();

        if (nextTaskOnFailure != null) {
            // Find the task with the failure handler ID
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            Optional<TaskDefinition> errorHandlerTask = definition.getTasks().stream()
                    .filter(t -> t.getId().equals(nextTaskOnFailure))
                    .findFirst();

            if (errorHandlerTask.isPresent()) {
                // Update error message
                workflowExecution.setErrorMessage("Task failed: " +
                        (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
                workflowExecutionService.save(workflowExecution);

                // Execute just the error handler task
                List<TaskDefinition> errorPath = List.of(errorHandlerTask.get());
                executeTasksSequentially(workflowExecution, errorPath, 0, resultFuture);
                return;
            }
        }

        // Check if we should continue despite errors
        if (shouldContinueOnError(workflowExecution)) {
            // Find the index of the current task
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> sortedTasks = taskDependencyUtil.sortTasksByDependency(definition.getTasks());

            int currentIndex = -1;
            for (int i = 0; i < sortedTasks.size(); i++) {
                if (sortedTasks.get(i).getId().equals(taskExecution.getTaskDefinition().getId())) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex >= 0 && currentIndex < sortedTasks.size() - 1) {
                // Continue with the next task
                executeTasksSequentially(workflowExecution, sortedTasks, currentIndex + 1, resultFuture);
                return;
            }
        }

        // No special handling, fail the workflow
        workflowExecution.setErrorMessage("Task failed: " +
                (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
        workflowExecutionService.updateWorkflowExecutionStatus(
                workflowExecution.getId(), WorkflowStatus.FAILED);

        resultFuture.complete(WorkflowStatus.FAILED);
    }

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

    private boolean evaluateSimpleExpression(String expression, Map<String, String> variables) {
        // Handle variable references like ${task1.output.success}
        if (expression.startsWith("${") && expression.endsWith("}")) {
            var key = expression.substring(2, expression.length() - 1);
            var value = variables.get(key);
            return "true".equalsIgnoreCase(value);
        }

        // Handle AND expressions
        if (expression.contains("&&")) {
            return Arrays.stream(expression.split("&&"))
                    .map(String::trim)
                    .allMatch(part -> evaluateSimpleExpression(part, variables));
        }

        // Handle OR expressions
        if (expression.contains("||")) {
            return Arrays.stream(expression.split("\\|\\|"))
                    .map(String::trim)
                    .anyMatch(part -> evaluateSimpleExpression(part, variables));
        }

        // Direct boolean value
        return Boolean.parseBoolean(expression);
    }

    private void updateWorkflowOnError(WorkflowExecution workflowExecution, Throwable throwable) {
        workflowExecution.setErrorMessage(throwable.getMessage());
        workflowExecutionService.updateWorkflowExecutionStatus(
                workflowExecution.getId(), WorkflowStatus.FAILED);
    }

    private boolean shouldContinueOnError(WorkflowExecution workflowExecution) {
        Map<String, String> variables = workflowExecution.getVariables();
        return "true".equalsIgnoreCase(variables.getOrDefault("continueOnError", "false"));
    }
}
