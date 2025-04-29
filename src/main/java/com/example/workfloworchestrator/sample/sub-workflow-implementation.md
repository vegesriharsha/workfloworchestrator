# Sub-Workflow Implementation Guide

This guide explains how to use sub-workflows in the Workflow Orchestrator to create modular, reusable workflow patterns.

## Overview

The sub-workflow feature allows tasks to launch other workflows as part of their execution. This enables:

1. **Workflow Modularity**: Break complex workflows into smaller, reusable components
2. **Workflow Reuse**: Reference existing workflows from multiple parent workflows
3. **Dynamic Workflow Selection**: Choose which sub-workflow to execute at runtime
4. **Hierarchical Process Management**: Create parent-child relationships between workflows

## Key Components

The sub-workflow implementation consists of the following components:

1. **WorkflowTaskExecutor**: Executes a specified workflow as a task
2. **WorkflowCallbackTaskExecutor**: Processes results from a completed sub-workflow
3. **SubWorkflowEventListener**: Handles workflow completion events and notifies parent workflows
4. **AWAITING_CALLBACK Status**: New workflow status for waiting for sub-workflow completion

## Synchronous vs. Asynchronous Execution

Sub-workflows can be executed in two modes:

1. **Synchronous**: The parent workflow waits for the sub-workflow to complete before continuing
2. **Asynchronous**: The parent workflow continues execution immediately, with a callback task to process results later

## Creating a Workflow Task

To create a task that launches a sub-workflow:

```java
TaskDefinition workflowTask = new TaskDefinition();
workflowTask.setName("launch-sub-workflow");
workflowTask.setType("workflow");

Map<String, String> config = new HashMap<>();
config.put("workflowName", "my-sub-workflow");
config.put("workflowVersion", "1.0.0"); // Optional, uses latest if not specified
config.put("waitForCompletion", "true"); // For synchronous execution
config.put("timeoutSeconds", "3600"); // 1 hour timeout
workflowTask.setConfiguration(config);
```

## Passing Data Between Workflows

### Parent to Child

Data is passed from parent to child workflow via the `inputVariables` configuration:

```java
Map<String, String> config = new HashMap<>();
config.put("workflowName", "data-processing-workflow");
config.put("inputVariables", "{"
    + "\"dataSourceId\": \"${sourceId}\","
    + "\"processType\": \"full\","
    + "\"priority\": \"high\""
    + "}");
```

Variables can also be prefixed to avoid name collisions:

```java
config.put("variablePrefix", "parent_");
```

### Child to Parent

When a sub-workflow completes, its output variables are made available to the parent workflow:

1. **Synchronous Execution**: Variables are available in the task result with a prefix:

    ```java
    // Access results in a subsequent task
    String result = "${launch-sub-workflow.output.subWorkflow.processingResult}";
    ```

2. **Asynchronous Execution**: Variables are available in the callback task:

    ```java
    // In the callback task configuration
    config.put("outputPrefix", "childWorkflow.");
    
    // Access in a subsequent task
    String result = "${callback-task.output.childWorkflow.processingResult}";
    ```

## Asynchronous Execution Pattern

For asynchronous sub-workflow execution:

1. **Launch Sub-Workflow**:

    ```java
    TaskDefinition launchTask = new TaskDefinition();
    launchTask.setName("launch-sub-workflow");
    launchTask.setType("workflow");
    
    Map<String, String> config = new HashMap<>();
    config.put("workflowName", "async-sub-workflow");
    config.put("waitForCompletion", "false");
    launchTask.setConfiguration(config);
    ```

2. **Continue with Other Tasks**:

    ```java
    TaskDefinition nextTask = new TaskDefinition();
    nextTask.setName("continue-processing");
    nextTask.setDependsOn(List.of(launchTask.getId()));
    ```

3. **Add Callback Task**:

    ```java
    TaskDefinition callbackTask = new TaskDefinition();
    callbackTask.setName("process-sub-workflow-result");
    callbackTask.setType("workflow-callback");
    
    Map<String, String> callbackConfig = new HashMap<>();
    callbackConfig.put("subWorkflowId", "${launch-sub-workflow.output.subWorkflowId}");
    callbackConfig.put("outputPrefix", "result.");
    callbackTask.set
