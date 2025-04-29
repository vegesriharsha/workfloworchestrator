package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Parallel execution strategy
 * Executes independent tasks in parallel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> taskDefinitions = definition.getTasks();

            // Group tasks by execution order
            Map<Integer, List<TaskDefinition>> tasksByOrder = groupTasksByExecutionOrder(taskDefinitions);

            // Execute groups in sequence, but tasks within groups in parallel
            executeTaskGroups(workflowExecution, tasksByOrder, resultFuture);

        } catch (Exception e) {
            log.error("Error in parallel execution strategy", e);
            resultFuture.complete(WorkflowStatus.FAILED);
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
            List<TaskDefinition> taskDefinitions = definition.getTasks().stream()
                    .filter(task -> taskIds.contains(task.getId()))
                    .collect(Collectors.toList());

            if (taskDefinitions.isEmpty()) {
                resultFuture.complete(WorkflowStatus.COMPLETED);
                return resultFuture;
            }

            // Group tasks by execution order
            Map<Integer, List<TaskDefinition>> tasksByOrder = groupTasksByExecutionOrder(taskDefinitions);

            // Execute groups in sequence, but tasks within groups in parallel
            executeTaskGroups(workflowExecution, tasksByOrder, resultFuture);

        } catch (Exception e) {
            log.error("Error in parallel subset execution", e);
            resultFuture.complete(WorkflowStatus.FAILED);
        }

        return resultFuture;
    }

    private Map<Integer, List<TaskDefinition>> groupTasksByExecutionOrder(List<TaskDefinition> taskDefinitions) {
        Map<Integer, List<TaskDefinition>> tasksByOrder = new TreeMap<>();

        for (TaskDefinition task : taskDefinitions) {
            Integer order = task.getExecutionOrder();
            if (order == null) {
                order = 0;
            }

            tasksByOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(task);
        }

        return tasksByOrder;
    }

    private void executeTaskGroups(WorkflowExecution workflowExecution,
                                   Map<Integer, List<TaskDefinition>> tasksByOrder,
                                   CompletableFuture<WorkflowStatus> resultFuture) {

        if (tasksByOrder.isEmpty()) {
            resultFuture.complete(WorkflowStatus.COMPLETED);
            return;
        }

        // Get the first group of tasks (lowest execution order)
        Integer firstOrder = tasksByOrder.keySet().iterator().next();
        List<TaskDefinition> currentGroup = tasksByOrder.remove(firstOrder);

        // Execute all tasks in the current group in parallel
        Map<Long, CompletableFuture<TaskExecution>> taskFutures = new ConcurrentHashMap<>();
        Map<Long, TaskExecution> taskExecutions = new ConcurrentHashMap<>();

        // Setup synchronized data for collecting outputs
        Map<String, String> outputCollector = new ConcurrentHashMap<>();

        // Check if any tasks require user review
        boolean requiresUserReview = currentGroup.stream().anyMatch(TaskDefinition::isRequireUserReview);

        if (requiresUserReview) {
            // Cannot execute in parallel if user review is required
            log.warn("Tasks requiring user review found in parallel group. Switching to sequential execution for this group.");
            executeTasksSequentially(workflowExecution, currentGroup, 0, tasksByOrder, resultFuture);
            return;
        }

        for (TaskDefinition taskDefinition : currentGroup) {
            // Prepare inputs from workflow variables and collected outputs
            Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());
            inputs.putAll(outputCollector);

            // Create task execution
            TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                    workflowExecution, taskDefinition, inputs);

            taskExecutions.put(taskDefinition.getId(), taskExecution);

            // Execute the task
            CompletableFuture<TaskExecution> future = taskExecutionService.executeTask(taskExecution.getId());
            taskFutures.put(taskDefinition.getId(), future);
        }

        // Create a combined future that completes when all tasks are done
        CompletableFuture<Void> allTasksFuture = CompletableFuture.allOf(
                taskFutures.values().toArray(new CompletableFuture[0]));

        // When all tasks are complete
        allTasksFuture.whenComplete((ignored, throwable) -> {
            try {
                if (throwable != null) {
                    log.error("Error executing task group", throwable);
                    resultFuture.complete(WorkflowStatus.FAILED);
                    return;
                }

                // Check if any task failed
                boolean anyFailed = false;
                List<TaskExecution> completedTasks = new ArrayList<>();

                for (Map.Entry<Long, CompletableFuture<TaskExecution>> entry : taskFutures.entrySet()) {
                    try {
                        TaskExecution taskExecution = entry.getValue().get();
                        completedTasks.add(taskExecution);

                        if (taskExecution.getStatus() == TaskStatus.FAILED) {
                            anyFailed = true;
                        } else if (taskExecution.getStatus() == TaskStatus.COMPLETED) {
                            // Collect outputs for next tasks
                            outputCollector.putAll(taskExecution.getOutputs());
                        }
                    } catch (Exception e) {
                        anyFailed = true;
                        log.error("Error getting task result", e);
                    }
                }

                // Handle task failures
                if (anyFailed) {
                    // Check if we need to continue with specific error paths
                    boolean continueOnError = false;

                    // Find tasks with next-on-failure paths
                    for (TaskExecution task : completedTasks) {
                        if (task.getStatus() == TaskStatus.FAILED &&
                                task.getTaskDefinition().getNextTaskOnFailure() != null) {

                            continueOnError = true;

                            // Find the error handler task
                            Long nextTaskId = task.getTaskDefinition().getNextTaskOnFailure();
                            TaskDefinition errorHandlerTask = workflowExecution.getWorkflowDefinition().getTasks().stream()
                                    .filter(t -> t.getId().equals(nextTaskId))
                                    .findFirst()
                                    .orElse(null);

                            if (errorHandlerTask != null) {
                                // Create a new map with just this error handler task
                                Map<Integer, List<TaskDefinition>> errorPath = new HashMap<>();
                                errorPath.put(0, List.of(errorHandlerTask));

                                // Execute the error path
                                executeTaskGroups(workflowExecution, errorPath, resultFuture);
                                return;
                            }
                        }
                    }

                    if (!continueOnError) {
                        // No specific error handling, fail the workflow
                        workflowExecution.setErrorMessage("One or more tasks failed in parallel execution");
                        workflowExecutionService.save(workflowExecution);
                        resultFuture.complete(WorkflowStatus.FAILED);
                        return;
                    }
                }

                // Update workflow variables with collected outputs
                Map<String, String> variables = workflowExecution.getVariables();
                variables.putAll(outputCollector);
                workflowExecution.setVariables(variables);
                workflowExecutionService.save(workflowExecution);

                // Continue with next group of tasks
                executeTaskGroups(workflowExecution, tasksByOrder, resultFuture);

            } catch (Exception e) {
                log.error("Error processing task group completion", e);
                resultFuture.complete(WorkflowStatus.FAILED);
            }
        });
    }

    // Fallback to sequential execution for groups with user review tasks
    private void executeTasksSequentially(WorkflowExecution workflowExecution,
                                          List<TaskDefinition> tasks,
                                          int taskIndex,
                                          Map<Integer, List<TaskDefinition>> remainingGroups,
                                          CompletableFuture<WorkflowStatus> resultFuture) {

        if (taskIndex >= tasks.size()) {
            // All tasks in this group completed, continue with next group
            executeTaskGroups(workflowExecution, remainingGroups, resultFuture);
            return;
        }

        // Get current task to execute
        TaskDefinition taskDefinition = tasks.get(taskIndex);

        // Prepare inputs from workflow variables
        Map<String, String> inputs = new HashMap<>(workflowExecution.getVariables());

        // Create task execution
        TaskExecution taskExecution = taskExecutionService.createTaskExecution(
                workflowExecution, taskDefinition, inputs);

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
                    log.error("Task execution failed", throwable);
                    resultFuture.complete(WorkflowStatus.FAILED);
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
                    executeTasksSequentially(workflowExecution, tasks, taskIndex + 1, remainingGroups, resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.FAILED) {
                    // Check if there's a next task on failure
                    Long nextTaskOnFailure = taskDefinition.getNextTaskOnFailure();

                    if (nextTaskOnFailure != null) {
                        // Find the error handler task
                        TaskDefinition errorHandlerTask = workflowExecution.getWorkflowDefinition().getTasks().stream()
                                .filter(t -> t.getId().equals(nextTaskOnFailure))
                                .findFirst()
                                .orElse(null);

                        if (errorHandlerTask != null) {
                            // Create a new map with just this error handler task
                            Map<Integer, List<TaskDefinition>> errorPath = new HashMap<>();
                            errorPath.put(0, List.of(errorHandlerTask));

                            // Execute the error path
                            executeTaskGroups(workflowExecution, errorPath, resultFuture);
                            return;
                        }
                    }

                    // No specific error handling, fail the workflow
                    workflowExecution.setErrorMessage("Task failed: " +
                            (completedTask.getErrorMessage() != null ? completedTask.getErrorMessage() : "Unknown error"));
                    workflowExecutionService.save(workflowExecution);
                    resultFuture.complete(WorkflowStatus.FAILED);
                } else {
                    // Other statuses like SKIPPED, CANCELLED, etc.
                    // Just continue with next task
                    executeTasksSequentially(workflowExecution, tasks, taskIndex + 1, remainingGroups, resultFuture);
                }
            } catch (Exception e) {
                log.error("Error processing task completion", e);
                resultFuture.complete(WorkflowStatus.FAILED);
            }
        });
    }
}
