package com.example.workfloworchestrator.engine.strategy;

import com.example.workfloworchestrator.exception.TaskExecutionException;
import com.example.workfloworchestrator.exception.WorkflowException;
import com.example.workfloworchestrator.model.*;
import com.example.workfloworchestrator.service.TaskExecutionService;
import com.example.workfloworchestrator.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Conditional execution strategy
 * Determines the next task based on conditions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionalExecutionStrategy implements ExecutionStrategy {

    private final TaskExecutionService taskExecutionService;
    private final WorkflowExecutionService workflowExecutionService;
    private final SequentialExecutionStrategy sequentialStrategy;

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> execute(WorkflowExecution workflowExecution) {
        CompletableFuture<WorkflowStatus> resultFuture = new CompletableFuture<>();

        try {
            WorkflowDefinition definition = workflowExecution.getWorkflowDefinition();
            List<TaskDefinition> allTasks = definition.getTasks();

            // Find starting task(s)
            List<TaskDefinition> startTasks = findStartTasks(allTasks);

            if (startTasks.isEmpty()) {
                throw new WorkflowException("No start tasks found in workflow");
            }

            // Execute the start tasks
            executeConditionalPath(workflowExecution, startTasks, allTasks, resultFuture);

        } catch (Exception e) {
            log.error("Error in conditional execution strategy", e);
            resultFuture.complete(WorkflowStatus.FAILED);
        }

        return resultFuture;
    }

    @Override
    @Transactional
    public CompletableFuture<WorkflowStatus> executeSubset(WorkflowExecution workflowExecution, List<Long> taskIds) {
        // For subset execution, we'll delegate to sequential strategy for simplicity
        return sequentialStrategy.executeSubset(workflowExecution, taskIds);
    }

    private List<TaskDefinition> findStartTasks(List<TaskDefinition> allTasks) {
        // Start tasks are those with the lowest execution order
        if (allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        // Find the minimum execution order
        int minOrder = allTasks.stream()
                .map(task -> task.getExecutionOrder() != null ? task.getExecutionOrder() : 0)
                .min(Integer::compareTo)
                .orElse(0);

        // Return all tasks with that order
        return allTasks.stream()
                .filter(task -> {
                    int order = task.getExecutionOrder() != null ? task.getExecutionOrder() : 0;
                    return order == minOrder;
                })
                .toList();
    }

    private void executeConditionalPath(WorkflowExecution workflowExecution,
                                        List<TaskDefinition> currentTasks,
                                        List<TaskDefinition> allTasks,
                                        CompletableFuture<WorkflowStatus> resultFuture) {

        if (currentTasks.isEmpty()) {
            // No more tasks to execute, workflow is complete
            resultFuture.complete(WorkflowStatus.COMPLETED);
            return;
        }

        // Execute current tasks sequentially
        executeTaskSequence(workflowExecution, currentTasks, 0, allTasks, resultFuture);
    }

    private void executeTaskSequence(WorkflowExecution workflowExecution,
                                     List<TaskDefinition> tasks,
                                     int index,
                                     List<TaskDefinition> allTasks,
                                     CompletableFuture<WorkflowStatus> resultFuture) {

        if (index >= tasks.size()) {
            // All tasks completed, determine next tasks based on conditions
            List<TaskDefinition> nextTasks = determineNextTasks(workflowExecution, tasks, allTasks);
            executeConditionalPath(workflowExecution, nextTasks, allTasks, resultFuture);
            return;
        }

        // Get current task to execute
        TaskDefinition taskDefinition = tasks.get(index);

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
                    handleTaskFailure(workflowExecution, taskExecution, throwable, tasks, index, allTasks, resultFuture);
                    return;
                }

                // Check task status
                if (completedTask.getStatus() == TaskStatus.COMPLETED) {
                    // Update workflow variables with task outputs
                    Map<String, String> variables = workflowExecution.getVariables();
                    variables.putAll(completedTask.getOutputs());
                    workflowExecution.setVariables(variables);
                    workflowExecutionService.save(workflowExecution);

                    // Continue with next task in the sequence
                    executeTaskSequence(workflowExecution, tasks, index + 1, allTasks, resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.FAILED) {
                    handleTaskFailure(workflowExecution, completedTask,
                            new TaskExecutionException(completedTask.getErrorMessage()),
                            tasks, index, allTasks, resultFuture);
                } else if (completedTask.getStatus() == TaskStatus.AWAITING_RETRY) {
                    // Task will be retried later, so we wait
                    resultFuture.complete(WorkflowStatus.RUNNING);
                } else {
                    // Other statuses like SKIPPED, CANCELLED, etc.
                    // Just continue with next task
                    executeTaskSequence(workflowExecution, tasks, index + 1, allTasks, resultFuture);
                }
            } catch (Exception e) {
                log.error("Error processing task completion", e);
                resultFuture.complete(WorkflowStatus.FAILED);
            }
        });
    }

    private void handleTaskFailure(WorkflowExecution workflowExecution,
                                   TaskExecution taskExecution,
                                   Throwable throwable,
                                   List<TaskDefinition> currentTasks,
                                   int currentIndex,
                                   List<TaskDefinition> allTasks,
                                   CompletableFuture<WorkflowStatus> resultFuture) {

        log.error("Task execution failed", throwable);

        // Check if there's a next task on failure defined
        TaskDefinition taskDefinition = taskExecution.getTaskDefinition();
        Long nextTaskOnFailure = taskDefinition.getNextTaskOnFailure();

        if (nextTaskOnFailure != null) {
            // Find the next task on failure
            Optional<TaskDefinition> nextTask = allTasks.stream()
                    .filter(t -> t.getId().equals(nextTaskOnFailure))
                    .findFirst();

            if (nextTask.isPresent()) {
                // Update error message
                workflowExecution.setErrorMessage("Task failed: " +
                        (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
                workflowExecutionService.save(workflowExecution);

                // Continue with the failure path
                executeConditionalPath(workflowExecution, List.of(nextTask.get()), allTasks, resultFuture);
                return;
            }
        }

        // No specific error handling, fail the workflow
        workflowExecution.setErrorMessage("Task failed: " +
                (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"));
        workflowExecutionService.save(workflowExecution);

        resultFuture.complete(WorkflowStatus.FAILED);
    }

    private List<TaskDefinition> determineNextTasks(WorkflowExecution workflowExecution,
                                                    List<TaskDefinition> completedTasks,
                                                    List<TaskDefinition> allTasks) {

        List<TaskDefinition> nextTasks = new ArrayList<>();

        // Check for explicit next tasks via nextTaskOnSuccess
        for (TaskDefinition completedTask : completedTasks) {
            Long nextTaskId = completedTask.getNextTaskOnSuccess();

            if (nextTaskId != null) {
                // Find the next task
                allTasks.stream()
                        .filter(t -> t.getId().equals(nextTaskId))
                        .findFirst()
                        .ifPresent(nextTasks::add);
            }
        }

        // If no explicit next tasks, evaluate conditional expressions
        if (nextTasks.isEmpty()) {
            for (TaskDefinition task : allTasks) {
                String condition = task.getConditionalExpression();

                // Skip tasks already executed
                if (completedTasks.contains(task)) {
                    continue;
                }

                if (condition != null && !condition.isEmpty()) {
                    boolean conditionMet = evaluateCondition(condition, workflowExecution.getVariables());

                    if (conditionMet) {
                        nextTasks.add(task);
                    }
                }
            }
        }

        return nextTasks;
    }

    private boolean evaluateCondition(String conditionExpression, Map<String, String> variables) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // Add variables to context
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }

            // Parse and evaluate the expression
            Expression expression = expressionParser.parseExpression(conditionExpression);
            Object result = expression.getValue(context);

            // Convert result to boolean
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result != null) {
                return Boolean.parseBoolean(result.toString());
            }

            return false;
        } catch (Exception e) {
            log.error("Error evaluating condition: {}", conditionExpression, e);
            return false;
        }
    }
}
